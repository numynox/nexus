-- abeona_attach_leg: a ride saved without the timetable takes the timetable's
-- ride once found — only that ride, only while unfound and not final, and
-- only for the household's members. abeona_mark_leg_dropped: a ride the
-- timetable no longer has is cancelled, once, which lifts the binding; found
-- again, it is not dropped any more.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(22);

insert into auth.users (id, email) values
  ('abe0a700-0000-4000-8000-000000000001', 'abeona-at-a@test.local'),
  ('abe0a700-0000-4000-8000-000000000002', 'abeona-at-b@test.local');

insert into public.profiles (id, email, full_name) values
  ('abe0a700-0000-4000-8000-000000000001', 'abeona-at-a@test.local', 'Ada'),
  ('abe0a700-0000-4000-8000-000000000002', 'abeona-at-b@test.local', 'Ben')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table at_ids (key text primary key, id uuid);
grant select, insert, update on at_ids to authenticated;

-- Verona → München as printed: no trip ref, no route, a month ahead.
create temporary table at_fixture as
select (now() + interval '30 days') as dep, (now() + interval '30 days' + interval '325 minutes') as arr;
grant select on at_fixture to authenticated;

create function pg_temp.found(p_ref text, p_dep timestamptz, p_arr timestamptz) returns jsonb language sql as $$
  select jsonb_build_object(
    'mode', 'train', 'product', 'RJ', 'line_name', 'RJ 86', 'trip_ref', p_ref,
    'headsign', 'München Hbf', 'operator', 'ÖBB',
    'from', jsonb_build_object('id', 'it:verona', 'name', 'Verona Porta Nuova', 'lat', 45.4290, 'lon', 10.9822,
      'country', 'IT', 'planned', p_dep::text, 'current', (p_dep + interval '2 minutes')::text, 'planned_platform', '4'),
    'to', jsonb_build_object('id', 'de:muc', 'name', 'München Hbf', 'lat', 48.1402, 'lon', 11.5586,
      'country', 'DE', 'planned', p_arr::text, 'current', null, 'planned_platform', '11'),
    'stopovers', jsonb_build_array(jsonb_build_object('id', 'it:bz', 'name', 'Bolzano', 'lat', 46.4967, 'lon', 11.3580)),
    'polyline', '[[10.9822, 45.4290], [11.3580, 46.4967], [11.5586, 48.1402]]'::jsonb,
    'cancelled', false, 'realtime', true
  );
$$;

set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0a700-0000-4000-8000-000000000001", "role": "authenticated"}';

insert into at_ids values ('household', public.abeona_ensure_household());
insert into at_ids values ('trip', public.abeona_save_trip('{"source": "pdf"}'::jsonb, jsonb_build_array(jsonb_build_object(
  'mode', 'train', 'product', 'RJ', 'line_name', 'RJ 86',
  'from', jsonb_build_object('id', null, 'name', 'Verona Porta Nuova', 'lat', 45.4290, 'lon', 10.9822,
    'planned', (select dep from at_fixture)::text, 'planned_platform', null),
  'to', jsonb_build_object('id', null, 'name', 'München Hbf Gl.5-10', 'lat', null, 'lon', null,
    'planned', (select arr from at_fixture)::text, 'planned_platform', '7')
))));

-- ── Not the same ride ──────────────────────────────────────────────────────
select is(public.abeona_attach_leg((select id from at_ids where key = 'trip'), 0::smallint,
    pg_temp.found('ref-86', (select dep + interval '1 hour' from at_fixture), (select arr + interval '1 hour' from at_fixture))),
  false, 'a ride at other minutes is not attached');
select is(public.abeona_attach_leg((select id from at_ids where key = 'trip'), 0::smallint,
    pg_temp.found(null, (select dep from at_fixture), (select arr from at_fixture))),
  false, 'a ride without a trip ref is not attached');
select is((select trip_ref from public.abeona_legs), null, 'and the leg is still unfound');

-- ── Another household ──────────────────────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0a700-0000-4000-8000-000000000002", "role": "authenticated"}';
select is(public.abeona_attach_leg((select id from at_ids where key = 'trip'), 0::smallint,
    pg_temp.found('ref-86', (select dep from at_fixture), (select arr from at_fixture))),
  false, 'another household cannot attach anything');

-- ── The same ride ──────────────────────────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0a700-0000-4000-8000-000000000001", "role": "authenticated"}';
select is(public.abeona_attach_leg((select id from at_ids where key = 'trip'), 0::smallint,
    pg_temp.found('ref-86', (select dep from at_fixture), (select arr from at_fixture))),
  true, 'the same ride is attached');

select is((select trip_ref from public.abeona_legs), 'ref-86', 'it takes the trip ref');
select is((select to_name from public.abeona_legs), 'München Hbf Gl.5-10', 'the printed name stays');
select is((select to_id || ' ' || to_lat || ' ' || to_country from public.abeona_legs), 'de:muc 48.1402 DE',
  'the stop takes its place and country');
select is((select jsonb_array_length(polyline) from public.abeona_legs), 3, 'the route line');
select is((select jsonb_array_length(stopovers) from public.abeona_legs), 1, 'the stops on the way');
select ok((select distance_km between 270 and 340 from public.abeona_legs), 'the distance along the route');
select is((select planned_departure_platform || '/' || planned_arrival_platform from public.abeona_legs), '4/11',
  'the timetable''s platforms');
select is((select extract(epoch from departure - planned_departure)::int from public.abeona_legs), 120, 'and the live values');

-- ── Once only ──────────────────────────────────────────────────────────────
select is(public.abeona_attach_leg((select id from at_ids where key = 'trip'), 0::smallint,
    pg_temp.found('ref-other', (select dep from at_fixture), (select arr from at_fixture))),
  false, 'a found ride is not replaced');

-- ── Dropped from the timetable ─────────────────────────────────────────────
insert into at_ids values ('bound', public.abeona_save_trip('{"source": "pdf", "fare_type": "saver", "train_binding": true}'::jsonb,
  jsonb_build_array(jsonb_build_object(
    'mode', 'train', 'product', 'RJ', 'line_name', 'RJ 86',
    'from', jsonb_build_object('id', 'it:verona', 'name', 'Verona Porta Nuova', 'planned', (select dep from at_fixture)::text),
    'to', jsonb_build_object('id', 'de:muc', 'name', 'München Hbf', 'planned', (select arr from at_fixture)::text)
  ))));

select ok(not (public.abeona_allowed_products((select id from at_ids where key = 'bound'))->>'binding_lifted')::boolean,
  'a saver is bound while its ride is in the plan');
select is(public.abeona_mark_leg_dropped((select id from at_ids where key = 'bound'), 0::smallint), true,
  'a ride the timetable no longer has is marked dropped');
select is(public.abeona_mark_leg_dropped((select id from at_ids where key = 'bound'), 0::smallint), false,
  'only once');
select ok((select cancelled and dropped_at is not null from public.abeona_legs
           where trip_id = (select id from at_ids where key = 'bound')), 'it is cancelled, and says why');
select is(public.abeona_allowed_products((select id from at_ids where key = 'bound'))->>'binding_lifted_reason', 'cancelled',
  'which lifts the binding');
select is(public.abeona_mark_leg_dropped((select id from at_ids where key = 'trip'), 0::smallint), false,
  'a found ride is never marked dropped');

select is(public.abeona_attach_leg((select id from at_ids where key = 'bound'), 0::smallint,
    pg_temp.found('ref-86b', (select dep from at_fixture), (select arr from at_fixture))),
  true, 'a dropped ride the timetable has again is attached');
select ok((select not cancelled and dropped_at is null from public.abeona_legs
           where trip_id = (select id from at_ids where key = 'bound')), 'and is no longer dropped or cancelled');

select * from finish();
rollback;
