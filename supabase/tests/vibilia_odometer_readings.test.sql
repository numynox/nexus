-- Vibilia odometer readings.
--
-- What must hold: a reading is plotted on the odometer chart next to the
-- refuels and widens the statistics' year range, but changes none of the
-- statistics — no distance, fuel or expense figure moves when an old reading
-- from before the first refuel is added. Readings are shared like the rest of
-- a car: members see and edit them, strangers see nothing and cannot add any.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(14);

-- ── Fixtures ───────────────────────────────────────────────────────────────
insert into auth.users (id, email) values
  ('0d0a0000-0000-4000-8000-000000000001', 'odometer-owner@test.local'),
  ('0d0a0000-0000-4000-8000-000000000002', 'odometer-member@test.local'),
  ('0d0a0000-0000-4000-8000-000000000003', 'odometer-stranger@test.local');

insert into public.profiles (id, email) values
  ('0d0a0000-0000-4000-8000-000000000001', 'odometer-owner@test.local'),
  ('0d0a0000-0000-4000-8000-000000000002', 'odometer-member@test.local'),
  ('0d0a0000-0000-4000-8000-000000000003', 'odometer-stranger@test.local')
on conflict (id) do nothing;

insert into public.cars (id, name, owner_id, tank_capacity) values
  ('0d0b0000-0000-4000-8000-000000000001', 'Odometer Car', '0d0a0000-0000-4000-8000-000000000001', 50);

insert into public.car_access (car_id, user_id) values
  ('0d0b0000-0000-4000-8000-000000000001', '0d0a0000-0000-4000-8000-000000000002');

-- Two full refuels 500 km apart and one expense, all in 2026.
insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('0d0b0000-0000-4000-8000-000000000001', '0d0a0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 90000, 40, 60.00, 1.500, 50, true, false),
  ('0d0b0000-0000-4000-8000-000000000001', '0d0a0000-0000-4000-8000-000000000001', '2026-01-15 10:00+00', 90500, 40, 64.00, 1.600, 50, true, false);

insert into public.car_expenses (car_id, user_id, expensed_at, title, amount, category)
values ('0d0b0000-0000-4000-8000-000000000001', '0d0a0000-0000-4000-8000-000000000001', '2026-03-01 10:00+00', 'Service', 120.00, 'Service');

-- ── As the owner, before any reading ───────────────────────────────────────
set local role authenticated;
set local request.jwt.claims to '{"sub":"0d0a0000-0000-4000-8000-000000000001","role":"authenticated"}';

create temporary table stats_before as
select * from public.get_car_refuel_statistics('0d0b0000-0000-4000-8000-000000000001', '2026-06-01 00:00+00');

-- Two readings from a service record, years before the first refuel.
select lives_ok(
  $$ insert into public.vibilia_odometer_readings (car_id, read_at, mileage, notes) values
       ('0d0b0000-0000-4000-8000-000000000001', '2019-05-02 09:00+00', 31000, 'Service record'),
       ('0d0b0000-0000-4000-8000-000000000001', '2022-06-10 09:00+00', 64000, null) $$,
  'the owner adds readings, user_id defaulting to themselves'
);

select is(
  (select count(*) from public.vibilia_odometer_readings where user_id = '0d0a0000-0000-4000-8000-000000000001'),
  2::bigint,
  'a new reading belongs to whoever added it'
);

select results_eq(
  $$ select read_at, mileage, source
     from public.get_car_odometer_plot('0d0b0000-0000-4000-8000-000000000001', '-infinity', '2026-06-01 00:00+00') $$,
  $$ values
       ('2019-05-02 09:00+00'::timestamptz, 31000, 'reading'::text),
       ('2022-06-10 09:00+00'::timestamptz, 64000, 'reading'::text),
       ('2026-01-01 10:00+00'::timestamptz, 90000, 'refuel'::text),
       ('2026-01-15 10:00+00'::timestamptz, 90500, 'refuel'::text) $$,
  'the odometer chart has readings and refuels, oldest first'
);

select results_eq(
  $$ select mileage from public.get_car_odometer_plot('0d0b0000-0000-4000-8000-000000000001', '2022-01-01 00:00+00', '2026-01-10 00:00+00') $$,
  $$ values (64000), (90000) $$,
  'the chart is limited to the requested range'
);

select results_eq(
  $$ select min_year, max_year from public.get_car_refuel_year_bounds('0d0b0000-0000-4000-8000-000000000001') $$,
  $$ values (2019, 2026) $$,
  'the range picker reaches back to the oldest reading'
);

create temporary table stats_after as
select * from public.get_car_refuel_statistics('0d0b0000-0000-4000-8000-000000000001', '2026-06-01 00:00+00');

select is(
  (select driven_total_km from stats_after), (select driven_total_km from stats_before),
  'a reading adds no distance driven'
);

select is(
  (select avg_consumption_l_per_100km from stats_after), (select avg_consumption_l_per_100km from stats_before),
  'a reading changes no consumption'
);

select is(
  (select expense_avg_year_eur from stats_after), 120.00::numeric,
  'a reading does not stretch the expenses per year'
);

select throws_ok(
  $$ insert into public.vibilia_odometer_readings (car_id, read_at, mileage)
     values ('0d0b0000-0000-4000-8000-000000000001', '2020-01-01 00:00+00', -5) $$,
  '23514',
  null,
  'a reading cannot be negative'
);

-- ── As a member of the car ─────────────────────────────────────────────────
set local request.jwt.claims to '{"sub":"0d0a0000-0000-4000-8000-000000000002","role":"authenticated"}';

select is(
  (select count(*) from public.vibilia_odometer_readings), 2::bigint,
  'a member sees the car''s readings'
);

select lives_ok(
  $$ update public.vibilia_odometer_readings set mileage = 64100 where mileage = 64000 $$,
  'a member corrects a reading'
);

-- ── As a stranger ──────────────────────────────────────────────────────────
set local request.jwt.claims to '{"sub":"0d0a0000-0000-4000-8000-000000000003","role":"authenticated"}';

select is(
  (select count(*) from public.get_car_odometer_plot('0d0b0000-0000-4000-8000-000000000001')), 0::bigint,
  'a stranger gets nothing from the odometer chart'
);

select throws_ok(
  $$ insert into public.vibilia_odometer_readings (car_id, read_at, mileage)
     values ('0d0b0000-0000-4000-8000-000000000001', '2020-01-01 00:00+00', 40000) $$,
  '42501',
  null,
  'a stranger cannot add a reading to someone else''s car'
);

-- ── Signed out ─────────────────────────────────────────────────────────────
reset role;

select ok(
  not has_function_privilege('anon', 'public.get_car_odometer_plot(uuid, timestamptz, timestamptz)', 'execute'),
  'anon cannot call get_car_odometer_plot'
);

select * from finish();

rollback;
