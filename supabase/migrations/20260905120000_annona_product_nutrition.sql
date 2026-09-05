-- Nutrition and provenance for Annona products.
--
-- Scanning a barcode used to give you an empty form: every product had to be
-- typed by hand. Open Food Facts answers most EANs with a name, brand, quantity
-- and image, and — nearly free once that lookup exists — the nutrition
-- declaration. These columns hold the latter.
--
-- The fields are the EU nutrition declaration, per 100 g/ml, which is what Open
-- Food Facts normalises to and what is printed on the packet. `nutrition_source`
-- records where the values came from so a hand-typed product is never mistaken
-- for a looked-up one.

ALTER TABLE public.annona_products
  ADD COLUMN IF NOT EXISTS energy_kcal_100g numeric(7, 2),
  ADD COLUMN IF NOT EXISTS fat_100g numeric(7, 2),
  ADD COLUMN IF NOT EXISTS saturated_fat_100g numeric(7, 2),
  ADD COLUMN IF NOT EXISTS carbohydrates_100g numeric(7, 2),
  ADD COLUMN IF NOT EXISTS sugars_100g numeric(7, 2),
  ADD COLUMN IF NOT EXISTS protein_100g numeric(7, 2),
  ADD COLUMN IF NOT EXISTS salt_100g numeric(7, 2),
  ADD COLUMN IF NOT EXISTS serving_size text,
  ADD COLUMN IF NOT EXISTS nutriscore text,
  ADD COLUMN IF NOT EXISTS nutrition_source text,
  ADD COLUMN IF NOT EXISTS nutrition_updated_at timestamptz;

ALTER TABLE public.annona_products
  DROP CONSTRAINT IF EXISTS annona_products_nutriscore_check;

ALTER TABLE public.annona_products
  ADD CONSTRAINT annona_products_nutriscore_check
  CHECK (nutriscore IS NULL OR nutriscore IN ('a', 'b', 'c', 'd', 'e'));

COMMENT ON COLUMN public.annona_products.energy_kcal_100g IS
  'Nutrition declaration per 100 g/ml, as printed on the packet.';
COMMENT ON COLUMN public.annona_products.nutrition_source IS
  'Where the nutrition values came from, e.g. "openfoodfacts". NULL means typed by hand.';
