-- Vibilia drive tracking, pinned against hand-computed numbers.
--
-- What must hold: two phones in one car count each minute once; a leg's exact
-- distance and fuel are spread over the days that were driven (cut at
-- Europe/Berlin midnight), and only for legs tracking fully covered; the open
-- leg is estimated drive by drive on the assumed speed curve, which tracked
-- legs scale (starting from the curve as given, worth 3 hours of driving);
-- a tank in which tracking began is
-- estimated by the calendar up to the first drive and by driving time after,
-- and teaches the model nothing; untracked cars keep the old refuel-day
-- attribution; and nothing is visible to someone the car is not shared with.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(45);

-- ── Fixtures ───────────────────────────────────────────────────────────────
-- Owner, a member the car is shared with, and a stranger.
insert into auth.users (id, email) values
  ('dddd0000-0000-4000-8000-000000000001', 'drive-owner@test.local'),
  ('dddd0000-0000-4000-8000-000000000002', 'drive-member@test.local'),
  ('dddd0000-0000-4000-8000-000000000003', 'drive-stranger@test.local');

insert into public.profiles (id, email) values
  ('dddd0000-0000-4000-8000-000000000001', 'drive-owner@test.local'),
  ('dddd0000-0000-4000-8000-000000000002', 'drive-member@test.local'),
  ('dddd0000-0000-4000-8000-000000000003', 'drive-stranger@test.local')
on conflict (id) do nothing;

insert into public.cars (id, name, owner_id, tank_capacity) values
  ('eeee0000-0000-4000-8000-000000000001', 'Tracked Car', 'dddd0000-0000-4000-8000-000000000001', 50),
  ('eeee0000-0000-4000-8000-000000000002', 'Untracked Car', 'dddd0000-0000-4000-8000-000000000001', 50),
  ('eeee0000-0000-4000-8000-000000000003', 'New Year Car', 'dddd0000-0000-4000-8000-000000000001', 50);

insert into public.car_access (car_id, user_id) values
  ('eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000002');

-- Tracked Car. Tracking starts before the first refuel, so the leg between
-- the two refuels is fully covered.
--   leg e1 → e2: 600 km, 48 L (full tank both times), driven 6 h:
--     30 Jan 08:00–10:00Z (2 h; a second phone overlaps 08:30–09:30Z)
--     1 Feb 21:00Z – 2 Feb 01:00Z (4 h; Berlin midnight is 23:00Z, so 2 h on
--       each side)
--   → the curve gives 200 km for the 2 h drive and 400 km for the 4 h one
--     (100 km/h from 100 min), exactly the odometer's 600 km: speed factor 1,
--     8 L/100km. 200 km on each of 30 Jan, 1 Feb and 2 Feb.
--   open leg after e2: 5 Feb 10:00–11:30Z, 90 min at 93.75 km/h → 140.625 km,
--   11.25 L estimated.
insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 10000, 40, 60.00, 1.500, 50, true, false),
  ('eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000001', '2026-02-03 10:00+00', 10600, 48, 72.00, 1.500, 50, true, false);

insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at) values
  ('ffff0000-0000-4000-8000-000000000001', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2025-12-31 08:00+00', '2025-12-31 08:30+00'),
  ('ffff0000-0000-4000-8000-000000000002', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-30 08:00+00', '2026-01-30 10:00+00'),
  ('ffff0000-0000-4000-8000-000000000003', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000002', 'phone-b', '2026-01-30 08:30+00', '2026-01-30 09:30+00'),
  ('ffff0000-0000-4000-8000-000000000004', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2026-02-01 21:00+00', '2026-02-02 01:00+00'),
  ('ffff0000-0000-4000-8000-000000000005', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2026-02-05 10:00+00', '2026-02-05 11:30+00');

-- Untracked Car: no drives at all. 500 km and 30 L between two refuels ten
-- days apart (50 + 30 − 50), i.e. 3 L a day by the calendar method.
insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('eeee0000-0000-4000-8000-000000000002', 'dddd0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 1000, 40, 60.00, 1.500, 50, true, false),
  ('eeee0000-0000-4000-8000-000000000002', 'dddd0000-0000-4000-8000-000000000001', '2026-01-11 10:00+00', 1500, 30, 45.00, 1.500, 50, true, false);

-- Half Tank Car: refuelled on 1 and 11 January, 500 km and 30 L apart
-- (6 L/100km, 3 L a day). Tracking starts on 14 January, three days into the
-- tank: one 1-hour drive on 14 January.
--   model: no tracked leg → factor 1; 60 min on the curve averages 75 km/h
--   on 15 January: 3 days × 3 L = 9 L by the calendar, 75 km × 6 L/100km =
--   4.5 L by driving → 36.5 L left; 9 L is 150 km → 1500 + 225 = 1725 km.
insert into public.cars (id, name, owner_id, tank_capacity) values
  ('eeee0000-0000-4000-8000-000000000004', 'Half Tank Car', 'dddd0000-0000-4000-8000-000000000001', 50),
  ('eeee0000-0000-4000-8000-000000000005', 'Half Tracked Leg Car', 'dddd0000-0000-4000-8000-000000000001', 50);

insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('eeee0000-0000-4000-8000-000000000004', 'dddd0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 1000, 40, 60.00, 1.500, 50, true, false),
  ('eeee0000-0000-4000-8000-000000000004', 'dddd0000-0000-4000-8000-000000000001', '2026-01-11 10:00+00', 1500, 30, 45.00, 1.500, 50, true, false);

insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at) values
  ('ffff0000-0000-4000-8000-000000000041', 'eeee0000-0000-4000-8000-000000000004', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-14 10:00+00', '2026-01-14 11:00+00');

-- Half Tracked Leg Car: the same, but the tank in which tracking began has
-- been closed by a refuel. The leg must not teach the model, because nobody
-- timed its first days (500 km over 2 tracked hours is no speed at all).
insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('eeee0000-0000-4000-8000-000000000005', 'dddd0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 1000, 40, 60.00, 1.500, 50, true, false),
  ('eeee0000-0000-4000-8000-000000000005', 'dddd0000-0000-4000-8000-000000000001', '2026-01-11 10:00+00', 1500, 30, 45.00, 1.500, 50, true, false);

insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at) values
  ('ffff0000-0000-4000-8000-000000000051', 'eeee0000-0000-4000-8000-000000000005', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-05 10:00+00', '2026-01-05 12:00+00');

-- Slow Car: one tracked leg whose single 2 h drive the curve puts at 200 km,
-- but the odometer says 100 km. Learned factor 0.5 over 2 h, blended with 1
-- over 3 h: (0.5 × 2 + 1 × 3) / 5 = 0.8.
insert into public.cars (id, name, owner_id, tank_capacity) values
  ('eeee0000-0000-4000-8000-000000000006', 'Slow Car', 'dddd0000-0000-4000-8000-000000000001', 50);

insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('eeee0000-0000-4000-8000-000000000006', 'dddd0000-0000-4000-8000-000000000001', '2026-01-01 10:00+00', 1000, 40, 60.00, 1.500, 50, true, false),
  ('eeee0000-0000-4000-8000-000000000006', 'dddd0000-0000-4000-8000-000000000001', '2026-01-20 10:00+00', 1100, 6, 9.00, 1.500, 50, true, false);

insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at) values
  ('ffff0000-0000-4000-8000-000000000060', 'eeee0000-0000-4000-8000-000000000006', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2025-12-31 10:00+00', '2025-12-31 10:05+00'),
  ('ffff0000-0000-4000-8000-000000000061', 'eeee0000-0000-4000-8000-000000000006', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-10 10:00+00', '2026-01-10 12:00+00');

-- New Year Car: one leg across New Year. 200 km driven in December, 300 km in
-- January, refuelled on 10 January. The statistics must split it by year.
insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated, fuel_level_after, is_full_refuel, missed_previous_refuel)
values
  ('eeee0000-0000-4000-8000-000000000003', 'dddd0000-0000-4000-8000-000000000001', '2025-12-01 10:00+00', 5000, 40, 60.00, 1.500, 50, true, false),
  ('eeee0000-0000-4000-8000-000000000003', 'dddd0000-0000-4000-8000-000000000001', '2026-01-10 10:00+00', 5500, 35, 52.50, 1.500, 50, true, false);

insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at) values
  ('ffff0000-0000-4000-8000-000000000011', 'eeee0000-0000-4000-8000-000000000003', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2025-11-30 10:00+00', '2025-11-30 10:10+00'),
  ('ffff0000-0000-4000-8000-000000000012', 'eeee0000-0000-4000-8000-000000000003', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2025-12-20 10:00+00', '2025-12-20 12:00+00'),
  ('ffff0000-0000-4000-8000-000000000013', 'eeee0000-0000-4000-8000-000000000003', 'dddd0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-05 10:00+00', '2026-01-05 13:00+00');

-- ── As the owner ───────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims to '{"sub":"dddd0000-0000-4000-8000-000000000001","role":"authenticated"}';

-- Deduplication
select is(
  (select count(*)::int from public.get_car_drive_periods('eeee0000-0000-4000-8000-000000000001')),
  4,
  'five intervals from two phones merge into four driving periods'
);

select is(
  (select reporter_count from public.get_car_drive_periods('eeee0000-0000-4000-8000-000000000001')
   where started_at = '2026-01-30 08:00+00'),
  2,
  'the overlapping period is reported by both phones'
);

select is(
  (select ended_at from public.get_car_drive_periods('eeee0000-0000-4000-8000-000000000001')
   where started_at = '2026-01-30 08:00+00'),
  '2026-01-30 10:00+00'::timestamptz,
  'the overlap counts once: 08:00–10:00, not 3 hours'
);

-- Legs
select is(
  (select driving_seconds from public.get_car_refuel_legs('eeee0000-0000-4000-8000-000000000001')),
  21600::numeric,
  'the leg between the two refuels holds 6 hours of driving'
);

select ok(
  (select tracked from public.get_car_refuel_legs('eeee0000-0000-4000-8000-000000000001')),
  'a leg that starts after tracking began is tracked'
);

-- The speed curve
select results_eq(
  $$ select round(public.vibilia_assumed_drive_km(h), 3)
     from unnest(array[0, 1/6::numeric, 0.25, 1/3::numeric, 1, 5/3::numeric, 2]) with ordinality as t(h, n) order by n $$,
  $$ values (0.000), (5.000), (10.000), (16.667), (75.000), (166.667), (200.000) $$,
  'the curve averages 30 km/h to 10 min, 50 km/h at 20 min, 75 km/h at 1 h and 100 km/h from 100 min'
);

-- Model
select is(
  (select round(speed_factor, 3) from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000001', '2026-02-06 00:00+00')),
  1.000,
  'the odometer agrees with the curve: factor 1'
);

select is(
  (select round(km_per_hour, 3) from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000001', '2026-02-06 00:00+00')),
  96.094,
  'km per hour averages the curve over the last 90 days: (28.125 + 200 + 400 + 140.625) km / 8 h'
);

select is(
  (select round(speed_factor, 3) from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000006', '2026-01-21 00:00+00')),
  0.800,
  'a car slower than the curve: 0.5 learned over 2 h, 1 assumed over 3 h'
);

select is(
  (select round(liters_per_100km, 3) from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000001', '2026-02-06 00:00+00')),
  8.000,
  'and 8 L/100km'
);

select is(
  (select tracking_since from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000001')),
  '2025-12-31 08:00+00'::timestamptz,
  'tracking_since is the first reported connection'
);

-- Daily distribution
select is(
  (select km from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001') where day = '2026-01-30'),
  200.000,
  '30 Jan (2 of 6 hours) gets a third of the leg'
);

select is(
  (select km from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001') where day = '2026-02-01'),
  200.000,
  'the drive across midnight is cut at Berlin midnight: 2 h on 1 Feb'
);

select is(
  (select km from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001') where day = '2026-02-02'),
  200.000,
  '... and 2 h on 2 Feb'
);

select is(
  (select liters from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001') where day = '2026-01-30'),
  16.000,
  'fuel is spread the same way: 48 L / 3'
);

select is(
  (select count(*)::int from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001') where day = '2026-02-03'),
  0,
  'nothing lands on the refuel day itself, nobody drove then'
);

select is(
  (select sum(km) from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001')),
  600.000,
  'redistribution conserves the odometer distance exactly'
);

select is(
  (select estimated_km from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001') where day = '2026-02-05'),
  140.625,
  'the open leg is estimated: a 90-minute drive is 140.625 km on the curve'
);

select is(
  (select km from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001') where day = '2026-02-05'),
  0.000,
  '... and kept apart from measured distance'
);

-- Per month
select results_eq(
  $$ select month_key, km_driven from public.get_car_distance_per_month('eeee0000-0000-4000-8000-000000000001', '2026-01-01 00:00+00', '2026-03-01 00:00+00') $$,
  $$ values ('2026-01-01'::date, 200.0::numeric), ('2026-02-01'::date, 400.0::numeric) $$,
  'January keeps the 200 km driven in January instead of February getting all 600'
);

select results_eq(
  $$ select month_key, km_driven from public.get_car_distance_per_month('eeee0000-0000-4000-8000-000000000002', '2026-01-01 00:00+00', '2026-03-01 00:00+00') $$,
  $$ select month_key, km_driven from public.get_car_km_per_month('eeee0000-0000-4000-8000-000000000002', '2026-01-01 00:00+00', '2026-03-01 00:00+00') $$,
  'an untracked car gets exactly what get_car_km_per_month always returned'
);

-- Fuel level estimate
select is(
  (select method from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000001', '2026-02-06 00:00+00')),
  'drive',
  'a tracked car is estimated from driving time'
);

select is(
  (select round(remaining_liters, 3) from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000001', '2026-02-06 00:00+00')),
  38.750,
  'full tank minus 140.625 km at 8 L/100km = 38.75 L'
);

select is(
  (select round(estimated_mileage, 1) from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000001', '2026-02-06 00:00+00')),
  10740.6,
  'and the odometer is estimated at 10600 + 140.6 km'
);

select is(
  (select calendar_liters from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000001', '2026-02-06 00:00+00')),
  0::numeric,
  'a fully tracked tank needs no calendar part'
);

-- Tracking started mid-tank
select results_eq(
  $$ select sample_count, speed_factor, round(km_per_hour, 3), round(liters_per_hour, 3) from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000004', '2026-01-15 10:00+00') $$,
  $$ values (0, 1::numeric, 75.000, 4.500) $$,
  'without a tracked leg the curve is taken as given: 1 h is 75 km/h, 4.5 L/h at 6 L/100km'
);

select is(
  (select method from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000004', '2026-01-15 10:00+00')),
  'drive',
  'the tank in which tracking began is already estimated from driving'
);

select results_eq(
  $$ select round(calendar_liters, 3), round(remaining_liters, 3), round(driving_hours, 3), driving_since
     from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000004', '2026-01-15 10:00+00') $$,
  $$ values (9.000, 36.500, 1.000, '2026-01-14 10:00+00'::timestamptz) $$,
  '3 days × 3 L by the calendar, then 75 km at 6 L/100km: 50 − 9 − 4.5 = 36.5 L'
);

select is(
  (select round(estimated_mileage, 1) from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000004', '2026-01-15 10:00+00')),
  1725.0,
  'the odometer adds 9 L at 6 L/100km and the 75 km drive: 1500 + 150 + 75'
);

select is(
  (select model_sample_count from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000004', '2026-01-15 10:00+00')),
  0,
  'and says the curve is assumed'
);

select is(
  (select estimated_km from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000004') where day = '2026-01-14'),
  75.000,
  'the first drive already shows on its day as an estimate'
);

select ok(
  not (select tracked from public.get_car_refuel_legs('eeee0000-0000-4000-8000-000000000005')),
  'a leg that began before tracking is not tracked, even with drives in it'
);

select results_eq(
  $$ select sample_count, speed_factor from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000005', '2026-01-15 10:00+00') $$,
  $$ values (0, 1::numeric) $$,
  '... and teaches the model nothing'
);

select is(
  (select km from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000005') where day = '2026-01-11'),
  500.000,
  '... and stays on its refuel day'
);

select is(
  (select km_per_hour from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000002')),
  null::numeric,
  'a car without drives has no model at all'
);

select is(
  (select method from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000002', '2026-01-16 10:00+00')),
  'time',
  'an untracked car falls back to the calendar method'
);

select is(
  (select round(remaining_liters, 3) from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000002', '2026-01-16 10:00+00')),
  35.000,
  'full tank minus 5 days × 3 L/day = 35 L'
);

-- Statistics by year
select results_eq(
  $$ select driven_this_year_km, driven_last_year_km from public.get_car_refuel_statistics('eeee0000-0000-4000-8000-000000000003', '2026-03-01 00:00+00') $$,
  $$ values (300::numeric, 200::numeric) $$,
  'a leg across New Year is split by when it was driven, not when it was refuelled'
);

select results_eq(
  $$ select fuel_used_this_year_l, fuel_used_last_year_l from public.get_car_refuel_statistics('eeee0000-0000-4000-8000-000000000003', '2026-03-01 00:00+00') $$,
  $$ values (21::numeric, 14::numeric) $$,
  'fuel follows the same split (35 L as 3 : 2)'
);

select is(
  (select fuel_cost_this_year_eur from public.get_car_refuel_statistics('eeee0000-0000-4000-8000-000000000003', '2026-03-01 00:00+00')),
  52.50::numeric,
  'cost stays on the day it was paid'
);

-- ── As the member the car is shared with ──────────────────────────────────
set local request.jwt.claims to '{"sub":"dddd0000-0000-4000-8000-000000000002","role":"authenticated"}';

select throws_ok(
  $$ insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at)
     values ('ffff0000-0000-4000-8000-000000000021', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000001', 'phone-b', '2026-02-07 10:00+00', '2026-02-07 11:00+00') $$,
  '42501',
  null,
  'a member cannot report a drive in someone else''s name'
);

select lives_ok(
  $$ insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at)
     values ('ffff0000-0000-4000-8000-000000000022', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000002', 'phone-b', '2026-02-07 10:00+00', '2026-02-07 11:00+00') $$,
  'a member reports their own drive'
);

-- ── As a stranger ─────────────────────────────────────────────────────────
set local request.jwt.claims to '{"sub":"dddd0000-0000-4000-8000-000000000003","role":"authenticated"}';

select is(
  (select count(*)::int from public.vibilia_drive_intervals where car_id = 'eeee0000-0000-4000-8000-000000000001')
  + (select count(*)::int from public.get_car_drive_periods('eeee0000-0000-4000-8000-000000000001'))
  + (select count(*)::int from public.get_car_daily_distance('eeee0000-0000-4000-8000-000000000001'))
  + (select count(*)::int from public.get_car_drive_model('eeee0000-0000-4000-8000-000000000001'))
  + (select count(*)::int from public.get_car_fuel_level_estimate('eeee0000-0000-4000-8000-000000000001')),
  0,
  'a stranger sees no drives, periods, distances, model or estimate'
);

select throws_ok(
  $$ insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at)
     values ('ffff0000-0000-4000-8000-000000000031', 'eeee0000-0000-4000-8000-000000000001', 'dddd0000-0000-4000-8000-000000000003', 'phone-c', '2026-02-07 10:00+00', '2026-02-07 11:00+00') $$,
  '42501',
  null,
  'a stranger cannot report drives for the car'
);

select throws_ok(
  $$ insert into public.vibilia_car_bluetooth_devices (car_id, address, name)
     values ('eeee0000-0000-4000-8000-000000000001', 'AA:BB:CC:DD:EE:FF', 'Head unit') $$,
  '42501',
  null,
  'a stranger cannot link a Bluetooth device to the car'
);

select * from finish();

rollback;
