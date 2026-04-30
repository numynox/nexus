-- Annona: Switch from per-user ownership to shared database + item activity log

-- 1. Drop old RLS policies
DROP POLICY IF EXISTS "Users manage own categories" ON annona_categories;
DROP POLICY IF EXISTS "Users manage own storage locations" ON annona_storage_locations;
DROP POLICY IF EXISTS "Users manage own products" ON annona_products;
DROP POLICY IF EXISTS "Users manage own items" ON annona_items;

-- 2. Drop old user-scoped indexes
DROP INDEX IF EXISTS idx_annona_categories_user;
DROP INDEX IF EXISTS idx_annona_storage_locations_user;
DROP INDEX IF EXISTS idx_annona_products_user;
DROP INDEX IF EXISTS idx_annona_items_user;

-- 3. Deduplicate before removing user_id

-- Categories: keep lowest id per name, repoint FKs
WITH dupes AS (
  SELECT id, name, ROW_NUMBER() OVER (PARTITION BY name ORDER BY id) AS rn
  FROM annona_categories
)
UPDATE annona_products p
SET category_id = keeper.id
FROM dupes d
JOIN (SELECT name, MIN(id) AS id FROM annona_categories GROUP BY name) keeper USING (name)
WHERE p.category_id = d.id AND d.rn > 1;

DELETE FROM annona_categories
WHERE id NOT IN (SELECT MIN(id) FROM annona_categories GROUP BY name);

-- Storage locations: keep lowest id per name, repoint FKs
WITH dupes AS (
  SELECT id, name, ROW_NUMBER() OVER (PARTITION BY name ORDER BY id) AS rn
  FROM annona_storage_locations
)
UPDATE annona_items i
SET storage_location_id = keeper.id
FROM dupes d
JOIN (SELECT name, MIN(id) AS id FROM annona_storage_locations GROUP BY name) keeper USING (name)
WHERE i.storage_location_id = d.id AND d.rn > 1;

DELETE FROM annona_storage_locations
WHERE id NOT IN (SELECT MIN(id) FROM annona_storage_locations GROUP BY name);

-- Products: keep lowest id per (name, brand, ean), repoint item FKs
WITH dupes AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY name, COALESCE(brand,''), COALESCE(ean,'')
           ORDER BY id
         ) AS rn,
         FIRST_VALUE(id) OVER (
           PARTITION BY name, COALESCE(brand,''), COALESCE(ean,'')
           ORDER BY id
         ) AS keeper_id
  FROM annona_products
)
UPDATE annona_items i
SET product_id = d.keeper_id
FROM dupes d
WHERE i.product_id = d.id AND d.rn > 1;

DELETE FROM annona_products
WHERE id NOT IN (
  SELECT MIN(id) FROM annona_products
  GROUP BY name, COALESCE(brand,''), COALESCE(ean,'')
);

-- 4. Drop user_id columns and add unique constraints
ALTER TABLE annona_categories DROP CONSTRAINT IF EXISTS annona_categories_user_id_name_key;
ALTER TABLE annona_categories DROP COLUMN user_id;
ALTER TABLE annona_categories ADD CONSTRAINT annona_categories_name_key UNIQUE(name);

ALTER TABLE annona_storage_locations DROP CONSTRAINT IF EXISTS annona_storage_locations_user_id_name_key;
ALTER TABLE annona_storage_locations DROP COLUMN user_id;
ALTER TABLE annona_storage_locations ADD CONSTRAINT annona_storage_locations_name_key UNIQUE(name);

ALTER TABLE annona_products DROP COLUMN user_id;
ALTER TABLE annona_items DROP COLUMN user_id;

-- 5. Drop the comment column from items (replaced by log entries)
ALTER TABLE annona_items DROP COLUMN IF EXISTS comment;

-- 6. Create the item activity log table
CREATE TABLE annona_item_log (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  item_id bigint NOT NULL REFERENCES annona_items(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  entry_type text NOT NULL CHECK (entry_type IN ('comment', 'created', 'expiration_changed', 'location_changed', 'consumed', 'unconsumed', 'deleted')),
  message text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_annona_item_log_item ON annona_item_log(item_id);
CREATE INDEX idx_annona_item_log_user ON annona_item_log(user_id);

-- 7. RLS for item log
ALTER TABLE annona_item_log ENABLE ROW LEVEL SECURITY;

-- All authenticated users can read and insert log entries
CREATE POLICY "Authenticated users read item log"
  ON annona_item_log FOR SELECT
  USING (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users insert item log"
  ON annona_item_log FOR INSERT
  WITH CHECK (auth.uid() = user_id);

-- 8. New RLS: all authenticated users can manage shared data
CREATE POLICY "Authenticated users manage categories"
  ON annona_categories FOR ALL
  USING (auth.role() = 'authenticated')
  WITH CHECK (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users manage storage locations"
  ON annona_storage_locations FOR ALL
  USING (auth.role() = 'authenticated')
  WITH CHECK (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users manage products"
  ON annona_products FOR ALL
  USING (auth.role() = 'authenticated')
  WITH CHECK (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users manage items"
  ON annona_items FOR ALL
  USING (auth.role() = 'authenticated')
  WITH CHECK (auth.role() = 'authenticated');

-- 9. Replace dashboard RPC (no longer user-scoped)
CREATE OR REPLACE FUNCTION annona_dashboard_summary()
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  result json;
BEGIN
  SELECT json_build_object(
    'total_products', (SELECT count(*) FROM annona_products),
    'total_active_items', (SELECT count(*) FROM annona_items WHERE NOT is_consumed),
    'expired_items', (SELECT count(*) FROM annona_items WHERE NOT is_consumed AND expiration_date < CURRENT_DATE),
    'expiring_within_7_days', (SELECT count(*) FROM annona_items WHERE NOT is_consumed AND expiration_date >= CURRENT_DATE AND expiration_date <= CURRENT_DATE + interval '7 days'),
    'expiring_within_30_days', (SELECT count(*) FROM annona_items WHERE NOT is_consumed AND expiration_date >= CURRENT_DATE AND expiration_date <= CURRENT_DATE + interval '30 days'),
    'no_expiration', (SELECT count(*) FROM annona_items WHERE NOT is_consumed AND expiration_date IS NULL),
    'consumed_items', (SELECT count(*) FROM annona_items WHERE is_consumed),
    'items_by_category', (
      SELECT coalesce(json_agg(row_to_json(t)), '[]'::json)
      FROM (
        SELECT c.name AS category, count(i.id) AS count
        FROM annona_items i
        JOIN annona_products p ON p.id = i.product_id
        LEFT JOIN annona_categories c ON c.id = p.category_id
        WHERE NOT i.is_consumed
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
        WHERE NOT i.is_consumed
        GROUP BY sl.name
        ORDER BY count DESC
      ) t
    )
  ) INTO result;

  RETURN result;
END;
$$;

-- Drop the old user-scoped overload if it exists
DROP FUNCTION IF EXISTS annona_dashboard_summary(uuid);
