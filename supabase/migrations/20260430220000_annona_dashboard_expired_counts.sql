-- Add expired_count to items_by_category and items_by_location in annona_dashboard_summary

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
        SELECT
          c.name AS category,
          count(i.id) AS count,
          count(CASE WHEN i.expiration_date < CURRENT_DATE THEN 1 END) AS expired_count
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
        SELECT
          coalesce(sl.name, 'Unassigned') AS location,
          count(i.id) AS count,
          count(CASE WHEN i.expiration_date < CURRENT_DATE THEN 1 END) AS expired_count
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
