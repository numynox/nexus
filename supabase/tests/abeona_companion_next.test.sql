-- Abeona companion, next round: coach and seats on a ride (saved, kept on a
-- re-save, written directly under RLS), the top speed and its place in the
-- statistics, and the trip summary for the arrival postcard and who may read
-- it.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(21);

insert into auth.users (id, email) values
  ('abe0c500-0000-4000-8000-000000000001', 'abeona-cn-a@test.local'),
  ('abe0c500-0000-4000-8000-000000000002', 'abeona-cn-b@test.local');

insert into public.profiles (id, email, full_name) values
  ('abe0c500-0000-4000-8000-000000000001', 'abeona-cn-a@test.local', 'Ada'),
  ('abe0c500-0000-4000-8000-000000000002', 'abeona-cn-b@test.local', 'Ben')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table cn_ids (key text primary key, id uuid);
grant select, insert, update on cn_ids to authenticated, service_role;

-- Frankfurt → Würzburg (ICE, 120 km, coach 24 seat 66) → Bamberg (RE, no
-- reservation), departing at p_departure. p_coach replaces the ICE's coach.
create function pg_temp.bamberg(p_departure timestamptz, p_coach text default '24') returns jsonb language sql as $$
  select jsonb_build_array(
    jsonb_build_object(
      'mode', 'train', 'product', 'ICE', 'line_name', 'ICE 25', 'trip_ref', 'ref-25',
      'from', jsonb_build_object('id', 'de-DELFI_de:06412:10', 'name', 'Frankfurt(Main)Hbf', 'country', 'DE',
        'planned', p_departure::text, 'current', (p_departure + interval '7 minutes')::text),
      'to', jsonb_build_object('id', 'de-DELFI_de:09663:478', 'name', 'Würzburg Hbf', 'country', 'DE',
        'planned', (p_departure + interval '68 minutes')::text, 'current', (p_departure + interval '75 minutes')::text),
      'distance_km', 120, 'coach', p_coach, 'seats', '66'),
    jsonb_build_object(
      'mode', 'train', 'product', 'RE', 'line_name', 'RE 10', 'trip_ref', 'ref-10',
      'from', jsonb_build_object('id', 'de-DELFI_de:09663:478', 'name', 'Würzburg Hbf', 'country', 'DE',
        'planned', (p_departure + interval '81 minutes')::text),
      'to', jsonb_build_object('id', 'de-DELFI_de:09461:1', 'name', 'Bamberg', 'country', 'DE',
        'planned', (p_departure + interval '140 minutes')::text, 'current', (p_departure + interval '162 minutes')::text),
      'distance_km', 100)
  );
$$;
grant execute on function pg_temp.bamberg(timestamptz, text) to authenticated;

-- ── Grants ─────────────────────────────────────────────────────────────────

select ok(not has_function_privilege('anon', 'public.abeona_trip_summary(uuid)', 'execute'),
  'anon cannot ask for a trip summary');
select ok(has_function_privilege('authenticated', 'public.abeona_trip_summary(uuid)', 'execute'),
  'a signed-in user asks for a trip summary');

-- ── User A ─────────────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0c500-0000-4000-8000-000000000001", "role": "authenticated"}';

select public.abeona_ensure_household();

insert into cn_ids (key, id) values
  ('bamberg', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, pg_temp.bamberg('2026-10-10 13:00 Europe/Berlin')));

select is((select coach from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0),
  '24', 'the ticket''s coach is saved on its ride');
select is((select seats from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0),
  '66', 'and its seats');
select is((select coach from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 1),
  null, 'a ride without a reservation has no coach');

-- The companion and the edit sheet write directly, under RLS.
update public.abeona_legs set top_speed_kmh = 287
  where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0;
update public.abeona_legs set coach = '5', seats = '12 14'
  where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 1;
select is((select top_speed_kmh from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0),
  287::smallint, 'a member records the top speed on a ride');
select throws_ok(
  $$ update public.abeona_legs set top_speed_kmh = 900 where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0 $$,
  '23514', null, 'a top speed no train reaches is refused');
select throws_ok(
  $$ update public.abeona_legs set coach = '' where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0 $$,
  '23514', null, 'an empty coach is refused (null means none)');

-- A re-save from a client that does not send them (an older APK) keeps them.
select public.abeona_save_trip(
  jsonb_build_object('id', (select id from cn_ids where key = 'bamberg'), 'fare_type', 'flex'),
  (select jsonb_agg(l - 'coach' - 'seats') from jsonb_array_elements(pg_temp.bamberg('2026-10-10 13:00 Europe/Berlin')) l)
);
select is((select coach from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0),
  '24', 'a re-save without coaches keeps the saved coach');
select is((select seats from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 1),
  '12 14', 'and the seats typed in');
select is((select top_speed_kmh from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0),
  287::smallint, 'and the top speed seen on board');

-- A re-save that says another coach wins.
select public.abeona_save_trip(
  jsonb_build_object('id', (select id from cn_ids where key = 'bamberg'), 'fare_type', 'flex'),
  pg_temp.bamberg('2026-10-10 13:00 Europe/Berlin', '7')
);
select is((select coach from public.abeona_legs where trip_id = (select id from cn_ids where key = 'bamberg') and idx = 0),
  '7', 'a re-save with another coach changes it');

-- The summary.
select is((public.abeona_trip_summary((select id from cn_ids where key = 'bamberg'))->>'rides')::int, 2,
  'the summary counts the rides');
select is((public.abeona_trip_summary((select id from cn_ids where key = 'bamberg'))->>'km')::int, 220,
  'and their distance');
select is((public.abeona_trip_summary((select id from cn_ids where key = 'bamberg'))->>'minutes_on_train')::int, 149,
  'the minutes on board, live times where known (68 + 81)');
select is((public.abeona_trip_summary((select id from cn_ids where key = 'bamberg'))->>'top_speed_kmh')::int, 287,
  'the top speed seen on board');
select ok((public.abeona_trip_summary((select id from cn_ids where key = 'bamberg'))->>'co2_saved_kg')::numeric > 0,
  'and the CO2 saved against the car');

select is((public.abeona_statistics('2026-01-01', '2027-01-01')->>'top_speed_kmh')::int, 287,
  'the year''s top speed');
select is(public.abeona_statistics('2026-01-01', '2027-01-01')->'top_speed_trip'->>'line', 'ICE 25',
  'and the ride it was on');

-- ── User B: another household ──────────────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0c500-0000-4000-8000-000000000002", "role": "authenticated"}';

select public.abeona_ensure_household();

select is(public.abeona_trip_summary((select id from cn_ids where key = 'bamberg')), null,
  'another household gets no summary of the trip');
update public.abeona_legs set coach = '99' where trip_id = (select id from cn_ids where key = 'bamberg');
select is((select count(*)::int from public.abeona_legs where coach = '99'), 0,
  'nor can it write a coach on its rides');

reset role;

select * from finish();
rollback;
