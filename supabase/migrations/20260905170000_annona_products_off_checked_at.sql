-- Marks when a product was last looked up in Open Food Facts, whatever the
-- outcome.
--
-- Without it there is no way to tell "not looked up yet" from "looked up, Open
-- Food Facts has never heard of this barcode": nutrition_source stays null in
-- both cases, so a backfill would re-query the unknown products on every run,
-- forever, against an API that allows 15 requests a minute.
--
-- Stamped even when nothing is found, so the backfill is resumable and
-- idempotent. Pass --refresh to the script to re-check products stamped long
-- ago; Open Food Facts gains data over time.

alter table public.annona_products
  add column if not exists off_checked_at timestamptz;

comment on column public.annona_products.off_checked_at is
  'When this barcode was last looked up in Open Food Facts, found or not. NULL means never.';
