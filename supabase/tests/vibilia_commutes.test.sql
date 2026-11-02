-- Vibilia commutes, pinned against hand-computed medians.
--
-- What must hold: the expected travel time is the median of the caller's own
-- drives in the car that started on a commute weekday inside the direction's
-- window (local time, Europe/Berlin); weekend drives, drives outside the
-- windows and another member's drives in the same car do not count; and a
-- commute — home and work included — is visible to its owner only.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(15);

-- ── Fixtures ───────────────────────────────────────────────────────────────
insert into auth.users (id, email) values
  ('c0aa0000-0000-4000-8000-000000000001', 'commute-owner@test.local'),
  ('c0aa0000-0000-4000-8000-000000000002', 'commute-member@test.local'),
  ('c0aa0000-0000-4000-8000-000000000003', 'commute-stranger@test.local');

insert into public.profiles (id, email) values
  ('c0aa0000-0000-4000-8000-000000000001', 'commute-owner@test.local'),
  ('c0aa0000-0000-4000-8000-000000000002', 'commute-member@test.local'),
  ('c0aa0000-0000-4000-8000-000000000003', 'commute-stranger@test.local')
on conflict (id) do nothing;

insert into public.cars (id, name, owner_id) values
  ('c0bb0000-0000-4000-8000-000000000001', 'Commute Car', 'c0aa0000-0000-4000-8000-000000000001');

insert into public.car_access (car_id, user_id) values
  ('c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000002');

-- January: Berlin is UTC+1. 5 Jan 2026 is a Monday.
--   to work, Mon–Fri 06:30–09:30 local:
--     Mon 07:00 30 min · Tue 07:10 40 min · Wed 07:20 50 min → median 40 min
--   to home, Mon–Fri 16:00–19:00 local:
--     Mon 17:00 35 min · Tue 17:30 55 min → median 45 min
--   not counted: Sat 07:00 (weekend), Thu 12:00 (no window),
--     Thu 06:20 local (05:20Z, before the window), and the member's Thu 07:00.
insert into public.vibilia_drive_intervals (id, car_id, user_id, device_id, started_at, ended_at) values
  ('c0cc0000-0000-4000-8000-000000000001', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-05 06:00+00', '2026-01-05 06:30+00'),
  ('c0cc0000-0000-4000-8000-000000000002', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-06 06:10+00', '2026-01-06 06:50+00'),
  ('c0cc0000-0000-4000-8000-000000000003', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-07 06:20+00', '2026-01-07 07:10+00'),
  ('c0cc0000-0000-4000-8000-000000000004', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-05 16:00+00', '2026-01-05 16:35+00'),
  ('c0cc0000-0000-4000-8000-000000000005', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-06 16:30+00', '2026-01-06 17:25+00'),
  ('c0cc0000-0000-4000-8000-000000000006', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-10 06:00+00', '2026-01-10 08:00+00'),
  ('c0cc0000-0000-4000-8000-000000000007', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-08 11:00+00', '2026-01-08 11:10+00'),
  ('c0cc0000-0000-4000-8000-000000000008', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000001', 'phone-a', '2026-01-08 05:20+00', '2026-01-08 05:35+00'),
  ('c0cc0000-0000-4000-8000-000000000009', 'c0bb0000-0000-4000-8000-000000000001', 'c0aa0000-0000-4000-8000-000000000002', 'phone-b', '2026-01-08 06:00+00', '2026-01-08 07:40+00');

-- ── As the owner ──────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims to '{"sub":"c0aa0000-0000-4000-8000-000000000001","role":"authenticated"}';

select is_empty(
  $$ select * from public.get_vibilia_commute_expectation('c0bb0000-0000-4000-8000-000000000001', '2026-01-12 00:00+00') $$,
  'no commute, no expectation'
);

insert into public.vibilia_commutes (
  car_id, weekdays, to_work_from, to_work_until, to_home_from, to_home_until,
  home_lat, home_lng, home_radius_m, work_lat, work_lng, work_radius_m
) values (
  'c0bb0000-0000-4000-8000-000000000001', '{1,2,3,4,5}', '06:30', '09:30', '16:00', '19:00',
  49.60, 11.00, 300, 49.45, 11.08, 500
);

select results_eq(
  $$ select direction, median_seconds, sample_count
     from public.get_vibilia_commute_expectation('c0bb0000-0000-4000-8000-000000000001', '2026-01-12 00:00+00') $$,
  $$ values ('to_work'::text, 2400, 3), ('to_home'::text, 2700, 2) $$,
  'median of own weekday drives inside each window'
);

select results_eq(
  $$ select direction, median_seconds, sample_count
     from public.get_vibilia_commute_expectation('c0bb0000-0000-4000-8000-000000000001', '2026-01-06 12:00+00') $$,
  $$ values ('to_work'::text, 2100, 2), ('to_home'::text, 2100, 1) $$,
  'drives after p_now do not count'
);

select results_eq(
  $$ select direction, median_seconds, sample_count
     from public.get_vibilia_commute_expectation('c0bb0000-0000-4000-8000-000000000001', '2026-09-01 00:00+00') $$,
  $$ values ('to_work'::text, null::integer, 0), ('to_home'::text, null::integer, 0) $$,
  'drives older than 180 days do not count'
);

update public.vibilia_commutes set weekdays = '{1}'
where car_id = 'c0bb0000-0000-4000-8000-000000000001';

select results_eq(
  $$ select direction, median_seconds, sample_count
     from public.get_vibilia_commute_expectation('c0bb0000-0000-4000-8000-000000000001', '2026-01-12 00:00+00') $$,
  $$ values ('to_work'::text, 1800, 1), ('to_home'::text, 2100, 1) $$,
  'only the chosen weekdays count'
);

select throws_ok(
  $$ update public.vibilia_commutes set to_work_until = '06:00'
     where car_id = 'c0bb0000-0000-4000-8000-000000000001' $$,
  '23514',
  null,
  'a window must end after it starts'
);

select lives_ok(
  $$ update public.vibilia_commutes set sms_name = 'Alex', sms_number = '+491701234567', sms_to_home = true
     where car_id = 'c0bb0000-0000-4000-8000-000000000001' $$,
  'one contact to text on the way home'
);

select throws_ok(
  $$ update public.vibilia_commutes set sms_number = '0170 123 4567'
     where car_id = 'c0bb0000-0000-4000-8000-000000000001' $$,
  '23514',
  null,
  'a number is stored as digits only'
);

select throws_ok(
  $$ update public.vibilia_commutes set sms_number = null
     where car_id = 'c0bb0000-0000-4000-8000-000000000001' $$,
  '23514',
  null,
  'texting needs a number'
);

-- ── As the member ─────────────────────────────────────────────────────────
set local request.jwt.claims to '{"sub":"c0aa0000-0000-4000-8000-000000000002","role":"authenticated"}';

select is_empty(
  $$ select * from public.vibilia_commutes $$,
  'a member does not see the owner''s commute'
);

select is_empty(
  $$ select * from public.get_vibilia_commute_expectation('c0bb0000-0000-4000-8000-000000000001', '2026-01-12 00:00+00') $$,
  'a member without a commute gets no expectation from the owner''s'
);

select throws_ok(
  $$ insert into public.vibilia_commutes (user_id, car_id, to_work_from, to_work_until, to_home_from, to_home_until, home_lat, home_lng, work_lat, work_lng)
     values ('c0aa0000-0000-4000-8000-000000000001', 'c0bb0000-0000-4000-8000-000000000001', '07:00', '09:00', '16:00', '18:00', 49, 11, 49, 11) $$,
  '42501',
  null,
  'a member cannot write a commute in someone else''s name'
);

select lives_ok(
  $$ insert into public.vibilia_commutes (car_id, to_work_from, to_work_until, to_home_from, to_home_until, home_lat, home_lng, work_lat, work_lng)
     values ('c0bb0000-0000-4000-8000-000000000001', '07:00', '09:00', '16:00', '18:00', 49, 11, 49, 11) $$,
  'a member keeps their own commute in a shared car'
);

select results_eq(
  $$ select direction, median_seconds, sample_count
     from public.get_vibilia_commute_expectation('c0bb0000-0000-4000-8000-000000000001', '2026-01-12 00:00+00') $$,
  $$ values ('to_work'::text, 6000, 1), ('to_home'::text, null::integer, 0) $$,
  'a member''s expectation comes from the member''s drives only'
);

-- ── As a stranger ─────────────────────────────────────────────────────────
set local request.jwt.claims to '{"sub":"c0aa0000-0000-4000-8000-000000000003","role":"authenticated"}';

select throws_ok(
  $$ insert into public.vibilia_commutes (car_id, to_work_from, to_work_until, to_home_from, to_home_until, home_lat, home_lng, work_lat, work_lng)
     values ('c0bb0000-0000-4000-8000-000000000001', '07:00', '09:00', '16:00', '18:00', 49, 11, 49, 11) $$,
  '42501',
  null,
  'a stranger cannot add a commute in a car not shared with them'
);

select * from finish();

rollback;
