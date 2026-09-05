-- Vibilia's refuel statistics, pinned against hand-computed numbers.
--
-- These are the rules the RPC exists to enforce, and each has been a bug at
-- least once: consumption is only computed between recorded refuels, a refuel
-- flagged `missed_previous_refuel` contributes distance but not fuel, a
-- negative tank-level correction is discarded rather than counted, and the
-- whole thing is scoped to cars the caller may see.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(11);

-- ── Fixtures ───────────────────────────────────────────────────────────────
-- Two users; only the first one owns the cars.
insert into auth.users (id, email) values
  ('aaaa0000-0000-4000-8000-000000000001', 'stats-owner@test.local'),
  ('aaaa0000-0000-4000-8000-000000000002', 'stats-stranger@test.local');

insert into public.cars (id, name, owner_id, tank_capacity) values
  ('cccc0000-0000-4000-8000-000000000001', 'Stats Car', 'aaaa0000-0000-4000-8000-000000000001', 50),
  ('cccc0000-0000-4000-8000-000000000002', 'Negative Correction Car', 'aaaa0000-0000-4000-8000-000000000001', 50);

-- Stats Car: every fill is a full tank, so the tank-level correction cancels
-- and the arithmetic is easy to check by hand.
--   e1 → no previous refuel        : no distance, no consumption
--   e2 → 500 km on 40 L            :  8.0 L/100km
--   e3 → 500 km, missed_previous   : distance counted, fuel NOT counted
--   e4 → 250 km on 25 L            : 10.0 L/100km
insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('cccc0000-0000-4000-8000-000000000001', 'aaaa0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 10000, 40, 60.00, 1.500, 50, true, false),
  ('cccc0000-0000-4000-8000-000000000001', 'aaaa0000-0000-4000-8000-000000000001', '2026-01-15 10:00+00', 10500, 40, 64.00, 1.600, 50, true, false),
  ('cccc0000-0000-4000-8000-000000000001', 'aaaa0000-0000-4000-8000-000000000001', '2026-02-01 10:00+00', 11000, 25, 40.00, 1.600, 50, true, true),
  ('cccc0000-0000-4000-8000-000000000001', 'aaaa0000-0000-4000-8000-000000000001', '2026-02-15 10:00+00', 11250, 25, 37.50, 1.500, 50, true, false);

-- Negative Correction Car: the second fill leaves a fuller tank than it added
-- (5 L added, level 10 → 50), so `liters + prev_level - level` is negative and
-- must be discarded, not counted as fuel used.
insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('cccc0000-0000-4000-8000-000000000002', 'aaaa0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 20000, 30, 45.00, 1.500, 10, false, false),
  ('cccc0000-0000-4000-8000-000000000002', 'aaaa0000-0000-4000-8000-000000000001', '2026-01-20 10:00+00', 20200, 5, 7.50, 1.500, 50, false, false);

-- One expense, to check it is reported separately from fuel cost.
insert into public.car_expenses (car_id, user_id, expensed_at, title, amount, category, notes)
values ('cccc0000-0000-4000-8000-000000000001', 'aaaa0000-0000-4000-8000-000000000001', '2026-03-01 10:00+00', 'Service', 120.00, 'Service', 'Stats fixture');

-- ── As the owner ───────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims to '{"sub":"aaaa0000-0000-4000-8000-000000000001","role":"authenticated"}';

create temporary table stats_owner as
select * from public.get_car_refuel_statistics(
  'cccc0000-0000-4000-8000-000000000001'::uuid,
  '2026-06-01 00:00+00'::timestamptz
);

select is(
  (select latest_mileage from stats_owner), 11250,
  'latest_mileage is the highest odometer reading'
);

select is(
  (select driven_total_km from stats_owner), 1250::numeric,
  'driven_total_km sums the gaps between refuels, including the missed one (500 + 500 + 250)'
);

select is(
  (select fuel_used_total_l from stats_owner), 65::numeric,
  'fuel_used_total_l counts only measurable intervals: 40 + 25, with the missed refuel excluded'
);

select is(
  (select fuel_cost_total_eur from stats_owner), 201.50::numeric,
  'fuel_cost_total_eur sums every refuel, including the first and the missed one'
);

select is(
  (select round(avg_consumption_l_per_100km, 4) from stats_owner), 9.0000::numeric,
  'avg_consumption averages the two measurable intervals (8.0 and 10.0)'
);

select is(
  (select round(min_consumption_l_per_100km, 4) from stats_owner), 8.0000::numeric,
  'min_consumption is the better of the two intervals'
);

select is(
  (select round(max_consumption_l_per_100km, 4) from stats_owner), 10.0000::numeric,
  'max_consumption is the worse of the two intervals'
);

select is(
  (select expense_total_eur from stats_owner), 120.00::numeric,
  'expense_total_eur is reported separately from fuel cost'
);

-- ── The negative tank-level correction must not become fuel used ────────────
create temporary table stats_negative as
select * from public.get_car_refuel_statistics(
  'cccc0000-0000-4000-8000-000000000002'::uuid,
  '2026-06-01 00:00+00'::timestamptz
);

select is(
  (select driven_total_km from stats_negative), 200::numeric,
  'distance is still counted when the fuel figure is unusable'
);

select is(
  (select fuel_used_total_l from stats_negative), 0::numeric,
  'a negative tank-level correction is discarded, not counted as fuel used'
);

-- ── As a user with no access to the car ────────────────────────────────────
set local request.jwt.claims to '{"sub":"aaaa0000-0000-4000-8000-000000000002","role":"authenticated"}';

select is(
  (select driven_total_km from public.get_car_refuel_statistics(
     'cccc0000-0000-4000-8000-000000000001'::uuid,
     '2026-06-01 00:00+00'::timestamptz)),
  0::numeric,
  'a user without access to the car gets no data from the statistics RPC'
);

select * from finish();

rollback;
