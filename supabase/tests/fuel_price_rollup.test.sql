-- The daily rollup is what makes it safe to delete raw fuel prices, so the
-- property that matters is equivalence: the weekly minima chart must show the
-- same numbers after aggregation as it did when reading every raw row. These
-- tests pin that, the Europe/Berlin bucketing it depends on, and the guard that
-- stops the prune from outrunning the rollup.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(12);

-- ── Fixtures ───────────────────────────────────────────────────────────────
insert into public.fuel_stations (id, name, brand, place)
values
  ('eeee0000-0000-4000-8000-000000000001', 'Rollup Test Station A', 'TEST', 'Testhausen'),
  ('eeee0000-0000-4000-8000-000000000002', 'Rollup Test Station B', 'TEST', 'Testhausen');

-- Berlin is UTC+2 in July, so 2026-07-09 22:30Z is 2026-07-10 00:30 local: it
-- must land on the 10th, the day a reader would call it.
insert into public.fuel_prices (station_id, fuel_type, price, checked_at) values
  ('eeee0000-0000-4000-8000-000000000001', 'E10', 1.799, '2026-07-09 22:30:00+00'),
  ('eeee0000-0000-4000-8000-000000000001', 'E10', 1.759, '2026-07-10 08:00:00+00'),
  ('eeee0000-0000-4000-8000-000000000001', 'E10', 1.819, '2026-07-10 16:00:00+00'),
  -- a second day, and a second station, to exercise grouping
  ('eeee0000-0000-4000-8000-000000000001', 'E10', 1.729, '2026-07-11 09:00:00+00'),
  ('eeee0000-0000-4000-8000-000000000002', 'E10', 1.709, '2026-07-11 09:00:00+00'),
  ('eeee0000-0000-4000-8000-000000000001', 'Diesel', 1.659, '2026-07-10 08:00:00+00');

select public.rollup_fuel_prices_daily('2026-07-09'::date);

-- ── Bucketing and aggregation ──────────────────────────────────────────────
select is(
  (select reading_count from public.fuel_prices_daily
    where station_id = 'eeee0000-0000-4000-8000-000000000001'
      and fuel_type = 'E10' and day = '2026-07-10'),
  3,
  'a reading at 00:30 Berlin counts towards the Berlin day, not the UTC one'
);

select is(
  (select min_price from public.fuel_prices_daily
    where station_id = 'eeee0000-0000-4000-8000-000000000001'
      and fuel_type = 'E10' and day = '2026-07-10'),
  1.759::numeric,
  'min_price is the cheapest reading of the Berlin day'
);

select is(
  (select max_price from public.fuel_prices_daily
    where station_id = 'eeee0000-0000-4000-8000-000000000001'
      and fuel_type = 'E10' and day = '2026-07-10'),
  1.819::numeric,
  'max_price is the dearest reading of the Berlin day'
);

select is(
  (select round(avg_price, 3) from public.fuel_prices_daily
    where station_id = 'eeee0000-0000-4000-8000-000000000001'
      and fuel_type = 'E10' and day = '2026-07-10'),
  1.792::numeric,
  'avg_price averages that day''s readings'
);

select is(
  (select count(*) from public.fuel_prices_daily
    where day between '2026-07-09' and '2026-07-11'
      and station_id in ('eeee0000-0000-4000-8000-000000000001','eeee0000-0000-4000-8000-000000000002')),
  4::bigint,
  'rows are one per station, fuel type and day'
);

select is(
  (select count(*) from public.fuel_prices_daily
    where fuel_type = 'Diesel' and day = '2026-07-10'
      and station_id = 'eeee0000-0000-4000-8000-000000000001'),
  1::bigint,
  'fuel types are aggregated separately'
);

-- ── Idempotence and catch-up ───────────────────────────────────────────────
select public.rollup_fuel_prices_daily('2026-07-09'::date);

select is(
  (select count(*) from public.fuel_prices_daily
    where day between '2026-07-09' and '2026-07-11'
      and station_id in ('eeee0000-0000-4000-8000-000000000001','eeee0000-0000-4000-8000-000000000002')),
  4::bigint,
  're-running the rollup upserts rather than duplicating'
);

-- A late reading for an already-rolled day must be picked up on recompute.
insert into public.fuel_prices (station_id, fuel_type, price, checked_at)
values ('eeee0000-0000-4000-8000-000000000001', 'E10', 1.699, '2026-07-10 10:00:00+00');

select public.rollup_fuel_prices_daily('2026-07-10'::date);

select is(
  (select min_price from public.fuel_prices_daily
    where station_id = 'eeee0000-0000-4000-8000-000000000001'
      and fuel_type = 'E10' and day = '2026-07-10'),
  1.699::numeric,
  'recomputing a day picks up readings that arrived after the first run'
);

-- ── Equivalence: the whole point ───────────────────────────────────────────
-- Weekly minima from the rollup must equal weekly minima computed from raw.
select is(
  (select min(min_price) from public.get_fuel_price_weekly_minima(
     'E10', '2026-07-06 00:00+00'::timestamptz, '2026-07-12 23:59:59+00'::timestamptz, 'Europe/Berlin')),
  (select min(fp.price) from public.fuel_prices fp
    where fp.fuel_type = 'E10'
      and fp.checked_at >= '2026-07-06 00:00+00'
      and fp.checked_at <= '2026-07-12 23:59:59+00'),
  'weekly minima from the rollup match the raw readings they replace'
);

select is(
  (select week_start from public.get_fuel_price_weekly_minima(
     'E10', '2026-07-06 00:00+00'::timestamptz, '2026-07-12 23:59:59+00'::timestamptz, 'Europe/Berlin') limit 1),
  '2026-07-06'::date,
  'weeks start on Monday'
);

-- ── The prune may not outrun the rollup ────────────────────────────────────
-- An unaggregated day is off limits even when it is older than the window.
insert into public.fuel_prices (station_id, fuel_type, price, checked_at)
values ('eeee0000-0000-4000-8000-000000000001', 'E10', 1.500, now() - interval '400 days');

select is(
  (select count(*) from public.fuel_prices
    where station_id = 'eeee0000-0000-4000-8000-000000000001'
      and checked_at < now() - interval '399 days'),
  1::bigint,
  'a reading from before the rollup window exists to be pruned'
);

select public.prune_fuel_prices_raw(21);

select is(
  (select count(*) from public.fuel_prices
    where station_id = 'eeee0000-0000-4000-8000-000000000001'
      and checked_at < now() - interval '399 days'),
  1::bigint,
  'and survives the prune, because no daily row covers it yet'
);

select * from finish();

rollback;
