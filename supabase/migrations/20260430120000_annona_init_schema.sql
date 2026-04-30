-- Annona: Grocery expiration tracker

-- Categories for organizing products
CREATE TABLE annona_categories (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, name)
);

-- Storage locations (e.g. Fridge, Pantry, Freezer)
CREATE TABLE annona_storage_locations (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, name)
);

-- Products (e.g. "Barilla Farfalle Pasta 200g")
CREATE TABLE annona_products (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  brand text,
  ean text,
  quantity text,
  category_id bigint REFERENCES annona_categories(id) ON DELETE SET NULL,
  image_url text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Items (a specific purchased package with expiration)
CREATE TABLE annona_items (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id bigint NOT NULL REFERENCES annona_products(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  expiration_date date,
  storage_location_id bigint REFERENCES annona_storage_locations(id) ON DELETE SET NULL,
  comment text,
  is_consumed boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_annona_categories_user ON annona_categories(user_id);
CREATE INDEX idx_annona_storage_locations_user ON annona_storage_locations(user_id);
CREATE INDEX idx_annona_products_user ON annona_products(user_id);
CREATE INDEX idx_annona_products_ean ON annona_products(ean) WHERE ean IS NOT NULL;
CREATE INDEX idx_annona_items_user ON annona_items(user_id);
CREATE INDEX idx_annona_items_product ON annona_items(product_id);
CREATE INDEX idx_annona_items_expiration ON annona_items(expiration_date) WHERE NOT is_consumed;

-- RLS
ALTER TABLE annona_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE annona_storage_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE annona_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE annona_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users manage own categories"
  ON annona_categories FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users manage own storage locations"
  ON annona_storage_locations FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users manage own products"
  ON annona_products FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users manage own items"
  ON annona_items FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

-- Dashboard RPC: summary statistics
CREATE OR REPLACE FUNCTION annona_dashboard_summary(p_user_id uuid)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  result json;
BEGIN
  SELECT json_build_object(
    'total_products', (SELECT count(*) FROM annona_products WHERE user_id = p_user_id),
    'total_active_items', (SELECT count(*) FROM annona_items WHERE user_id = p_user_id AND NOT is_consumed),
    'expired_items', (SELECT count(*) FROM annona_items WHERE user_id = p_user_id AND NOT is_consumed AND expiration_date < CURRENT_DATE),
    'expiring_within_7_days', (SELECT count(*) FROM annona_items WHERE user_id = p_user_id AND NOT is_consumed AND expiration_date >= CURRENT_DATE AND expiration_date <= CURRENT_DATE + interval '7 days'),
    'expiring_within_30_days', (SELECT count(*) FROM annona_items WHERE user_id = p_user_id AND NOT is_consumed AND expiration_date >= CURRENT_DATE AND expiration_date <= CURRENT_DATE + interval '30 days'),
    'no_expiration', (SELECT count(*) FROM annona_items WHERE user_id = p_user_id AND NOT is_consumed AND expiration_date IS NULL),
    'consumed_items', (SELECT count(*) FROM annona_items WHERE user_id = p_user_id AND is_consumed),
    'items_by_category', (
      SELECT coalesce(json_agg(row_to_json(t)), '[]'::json)
      FROM (
        SELECT c.name AS category, count(i.id) AS count
        FROM annona_items i
        JOIN annona_products p ON p.id = i.product_id
        LEFT JOIN annona_categories c ON c.id = p.category_id
        WHERE i.user_id = p_user_id AND NOT i.is_consumed
        GROUP BY c.name
        ORDER BY count DESC
      ) t
    ),
    'items_by_location', (
      SELECT coalesce(json_agg(row_to_json(t)), '[]'::json)
      FROM (
        SELECT coalesce(sl.name, 'Unassigned') AS location, count(i.id) AS count
        FROM annona_items i
        LEFT JOIN annona_storage_locations sl ON sl.id = i.storage_location_id
        WHERE i.user_id = p_user_id AND NOT i.is_consumed
        GROUP BY sl.name
        ORDER BY count DESC
      ) t
    )
  ) INTO result;

  RETURN result;
END;
$$;
