-- Drop sort_order from annona_categories; sort alphabetically instead
ALTER TABLE annona_categories DROP COLUMN IF EXISTS sort_order;
