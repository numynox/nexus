-- Abeona after the ride: the final snapshot (which trips the job picks, final
-- values that stay final), the recorded replacement, the compensation rules
-- at their edges, and claims under the household's RLS.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(50);

insert into auth.users (id, email) values
  ('abe0af00-0000-4000-8000-000000000001', 'abeona-ar-a@test.local'),
  ('abe0af00-0000-4000-8000-000000000002', 'abeona-ar-b@test.local');

insert into public.profiles (id, email, full_name) values
  ('abe0af00-0000-4000-8000-000000000001', 'abeona-ar-a@test.local', 'Ada'),
  ('abe0af00-0000-4000-8000-000000000002', 'abeona-ar-b@test.local', 'Ben')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table ar_ids (key text primary key, id uuid);
grant select, insert, update on ar_ids to authenticated;

-- One ICE from Frankfurt to Würzburg, arriving at p_arrival.
create function pg_temp.ride(p_arrival timestamptz) returns jsonb language sql as $$
  select jsonb_build_array(jsonb_build_object(
    'mode', 'train', 'product', 'ICE', 'line_name', 'ICE 25', 'trip_ref', 'ref-25',
    'from', jsonb_build_object('id', 'x:ffm', 'name', 'Frankfurt Hbf', 'planned', (p_arrival - interval '68 minutes')::text),
    'to', jsonb_build_object('id', 'x:wue', 'name', 'Würzburg Hbf', 'planned', p_arrival::text)
  ));
$$;
-- ICE 25, then RE 10 to Bamberg arriving at p_arrival.
create function pg_temp.two_rides(p_arrival timestamptz) returns jsonb language sql as $$
  select pg_temp.ride(p_arrival - interval '85 minutes') || jsonb_build_array(jsonb_build_object(
    'mode', 'train', 'product', 'RE', 'line_name', 'RE 10', 'trip_ref', 'ref-10',
    'from', jsonb_build_object('id', 'x:wue', 'name', 'Würzburg Hbf', 'planned', (p_arrival - interval '72 minutes')::text),
    'to', jsonb_build_object('id', 'x:bam', 'name', 'Bamberg', 'planned', p_arrival::text)
  ));
$$;
grant execute on function pg_temp.ride(timestamptz), pg_temp.two_rides(timestamptz) to authenticated;

-- A trip arriving at 14:00 in Berlin, 50 € unless said otherwise.
create function pg_temp.late(p_trip uuid, p_minutes integer) returns void language sql as $$
  update public.abeona_legs set arrival = planned_arrival + make_interval(mins => p_minutes) where trip_id = p_trip;
$$;
grant execute on function pg_temp.late(uuid, integer) to authenticated;

-- ── Grants ─────────────────────────────────────────────────────────────────

select ok(not has_function_privilege('authenticated', 'public.abeona_finalize_trip(uuid, jsonb)', 'execute'),
  'a signed-in user cannot finalize a trip');
select ok(not has_function_privilege('authenticated', 'public.abeona_trips_to_finalize(integer, interval, interval)', 'execute'),
  'a signed-in user cannot list the trips to finalize');
select ok(has_function_privilege('service_role', 'public.abeona_finalize_trip(uuid, jsonb)', 'execute'),
  'the service role finalizes');
select ok(not has_function_privilege('authenticated', 'public.invoke_abeona_refresh_trips()', 'execute'),
  'nobody signed in may call the scheduler''s wrapper');
select ok(not has_function_privilege('anon', 'public.abeona_compensation(uuid)', 'execute'),
  'anon cannot ask for compensation');
select ok(not has_table_privilege('anon', 'public.abeona_claims', 'select'),
  'anon cannot read claims');
select is((select schedule from cron.job where jobname = 'abeona-refresh-trips-every-15min'), '4,19,34,49 * * * *',
  'the job runs every 15 minutes');

-- ── User A's trips ─────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0af00-0000-4000-8000-000000000001", "role": "authenticated"}';

select public.abeona_ensure_household();

insert into ar_ids values
  ('due', public.abeona_save_trip('{"fare_type": "flex", "price_cents": 5000}'::jsonb, pg_temp.ride(now() - interval '30 minutes'))),
  ('soon', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, pg_temp.ride(now() - interval '5 minutes'))),
  ('old', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, pg_temp.ride(now() - interval '3 hours'))),
  ('flex', public.abeona_save_trip('{"fare_type": "flex", "price_cents": 5000}'::jsonb, pg_temp.ride('2026-10-14 14:00 Europe/Berlin'))),
  ('cheap', public.abeona_save_trip('{"fare_type": "saver", "price_cents": 1590}'::jsonb, pg_temp.ride('2026-10-14 14:00 Europe/Berlin'))),
  ('noprice', public.abeona_save_trip('{"fare_type": "saver"}'::jsonb, pg_temp.ride('2026-10-14 14:00 Europe/Berlin'))),
  ('dticket', public.abeona_save_trip('{"fare_type": "deutschlandticket"}'::jsonb, pg_temp.ride('2026-10-14 14:00 Europe/Berlin'))),
  ('bc2', public.abeona_save_trip('{"fare_type": "bahncard100", "travel_class": 2}'::jsonb, pg_temp.ride('2026-10-14 14:00 Europe/Berlin'))),
  ('bc1', public.abeona_save_trip('{"fare_type": "bahncard100", "travel_class": 1}'::jsonb, pg_temp.ride('2026-10-14 14:00 Europe/Berlin'))),
  ('night', public.abeona_save_trip('{"fare_type": "flex", "price_cents": 8000}'::jsonb, pg_temp.ride('2026-10-14 01:30 Europe/Berlin'))),
  ('missed', public.abeona_save_trip('{"fare_type": "saver", "price_cents": 3490}'::jsonb, pg_temp.two_rides('2026-10-14 14:00 Europe/Berlin')));

reset role;

-- ── The job's query and write (as the service role would) ──────────────────

select ok((select id from ar_ids where key = 'due') in (select trip_id from public.abeona_trips_to_finalize(50)),
  'a trip that arrived 30 minutes ago is due');
select ok((select id from ar_ids where key = 'soon') not in (select trip_id from public.abeona_trips_to_finalize(50)),
  'a trip that arrived 5 minutes ago is not due yet');
select ok((select id from ar_ids where key = 'old') not in (select trip_id from public.abeona_trips_to_finalize(50)),
  'a trip that arrived 3 hours ago is past the window');

-- 40 minutes late on the live values: arrived 30 minutes ago in the plan,
-- but expected 10 minutes from now, so not due yet.
update public.abeona_legs set arrival = planned_arrival + interval '40 minutes'
where trip_id = (select id from ar_ids where key = 'due');
select ok((select id from ar_ids where key = 'due') not in (select trip_id from public.abeona_trips_to_finalize(50)),
  'a late trip waits for its expected arrival');
update public.abeona_legs set arrival = null where trip_id = (select id from ar_ids where key = 'due');

select is(
  (select legs->0->>'trip_ref' from public.abeona_trips_to_finalize(50) where trip_id = (select id from ar_ids where key = 'due')),
  'ref-25', 'the job gets what a refresh needs');

select is(public.abeona_finalize_trip((select id from ar_ids where key = 'due'),
  jsonb_build_array(jsonb_build_object('idx', 0,
    'arrival', (select planned_arrival + interval '62 minutes' from public.abeona_legs where trip_id = (select id from ar_ids where key = 'due')),
    'arrival_platform', '5', 'realtime', true))),
  true, 'with every ride answered, the trip is final');
select ok((select finalized_at is not null from public.abeona_trips where id = (select id from ar_ids where key = 'due')),
  'finalized_at is set');
select ok((select id from ar_ids where key = 'due') not in (select trip_id from public.abeona_trips_to_finalize(50)),
  'a final trip is not picked again');

select is(public.abeona_finalize_trip((select id from ar_ids where key = 'soon'), '[]'::jsonb), false,
  'without an answer, nothing is final');

select is((public.abeona_compensation((select id from ar_ids where key = 'due'))->>'delay_source'), 'final',
  'the compensation reads the final arrival');

-- ── Final stays final (as user A) ──────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0af00-0000-4000-8000-000000000001", "role": "authenticated"}';

select public.abeona_update_trip_live((select id from ar_ids where key = 'due'),
  jsonb_build_array(jsonb_build_object('idx', 0, 'arrival', now()::text)));
update public.abeona_legs set arrival = now(), final = false where trip_id = (select id from ar_ids where key = 'due');

select is((select (extract(epoch from arrival - planned_arrival) / 60)::int from public.abeona_legs where trip_id = (select id from ar_ids where key = 'due')),
  62, 'a live refresh or a direct update does not overwrite a final arrival');
select ok((select final from public.abeona_legs where trip_id = (select id from ar_ids where key = 'due')),
  'and the leg stays final');
select is((public.abeona_compensation((select id from ar_ids where key = 'due'))->>'delay_minutes')::int, 62,
  'final vs live: the final delay counts');

-- A trip without a final snapshot reads its live values.
select pg_temp.late((select id from ar_ids where key = 'flex'), 59);
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'delay_source'), 'live',
  'without a snapshot, the last live refresh counts');

-- ── The thresholds ─────────────────────────────────────────────────────────
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'verdict'), 'not_eligible', '59 min: nothing');
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->'reasons'->>0),
  'Arrived 59 min late. Compensation starts at 60 minutes.', '59 min: says why');

select pg_temp.late((select id from ar_ids where key = 'flex'), 60);
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'verdict'), 'eligible', '60 min: eligible');
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'amount_cents')::int, 1250, '60 min: 25 % of 50 €');

select pg_temp.late((select id from ar_ids where key = 'flex'), 119);
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'percent')::numeric, 25::numeric, '119 min: still 25 %');

select pg_temp.late((select id from ar_ids where key = 'flex'), 120);
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'amount_cents')::int, 2500, '120 min: 50 % of 50 €');
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'deadline'), '2027-01-14',
  'the deadline is three months after the trip');
select ok((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'summary_de') like '%Verspätung am Ziel: 120 Minuten%Fahrpreis: 50,00 €%',
  'the German summary names the delay and the fare');

-- ── The €4 minimum, a missing price, force majeure ─────────────────────────
select pg_temp.late((select id from ar_ids where key = 'cheap'), 65);
select is((public.abeona_compensation((select id from ar_ids where key = 'cheap'))->>'verdict'), 'below_minimum',
  '25 % of 15.90 € is under the €4 minimum');
select is((public.abeona_compensation((select id from ar_ids where key = 'cheap'))->>'amount_cents')::int, 398,
  'and says how much it would have been');
update public.abeona_trips set price_cents = 1600 where id = (select id from ar_ids where key = 'cheap');
select is((public.abeona_compensation((select id from ar_ids where key = 'cheap'))->>'verdict'), 'eligible',
  '25 % of 16 € is exactly €4');

select pg_temp.late((select id from ar_ids where key = 'noprice'), 90);
select is((public.abeona_compensation((select id from ar_ids where key = 'noprice'))->>'verdict'), 'unknown_price',
  'without a price, the verdict asks for one');
select is((public.abeona_compensation((select id from ar_ids where key = 'noprice'))->'amount_cents'), 'null'::jsonb,
  'and has no amount');

update public.abeona_trips set force_majeure = true where id = (select id from ar_ids where key = 'flex');
select is((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'verdict'), 'not_eligible',
  'force majeure excludes compensation');

-- ── Flat rates ─────────────────────────────────────────────────────────────
select pg_temp.late((select id from ar_ids where key = 'dticket'), 75);
select ok((select c->>'verdict' = 'below_minimum' and (c->>'amount_cents')::int = 150 and (c->>'poolable')::boolean
           from (select public.abeona_compensation((select id from ar_ids where key = 'dticket')) as c) x),
  'a Deutschlandticket gets €1.50 a case, to be pooled');
update public.abeona_trips set price_cents = 400 where id = (select id from ar_ids where key = 'dticket');
select is((public.abeona_compensation((select id from ar_ids where key = 'dticket'))->>'amount_cents')::int, 100,
  'capped at 25 % of the ticket''s value');

select pg_temp.late((select id from ar_ids where key = 'bc2'), 61);
select pg_temp.late((select id from ar_ids where key = 'bc1'), 61);
select is((public.abeona_compensation((select id from ar_ids where key = 'bc2'))->>'amount_cents')::int, 1000, 'BahnCard 100, 2nd class: €10');
select is((public.abeona_compensation((select id from ar_ids where key = 'bc1'))->>'amount_cents')::int, 1500, 'BahnCard 100, 1st class: €15');
select is((public.abeona_compensation((select id from ar_ids where key = 'bc1'))->>'verdict'), 'eligible', 'and €15 is paid out');

-- ── The night ──────────────────────────────────────────────────────────────
select pg_temp.late((select id from ar_ids where key = 'night'), 70);
select ok((public.abeona_compensation((select id from ar_ids where key = 'night'))->>'hints') like '%taxi up to €120%',
  'arriving at 1:30 an hour late: the taxi hint');
select ok((public.abeona_compensation((select id from ar_ids where key = 'flex'))->>'hints') not like '%taxi%',
  'not in the afternoon');

-- ── A missed connection ────────────────────────────────────────────────────
update public.abeona_trips set missed_leg_idx = 1 where id = (select id from ar_ids where key = 'missed');
select is((public.abeona_compensation((select id from ar_ids where key = 'missed'))->>'verdict'), 'unknown_arrival',
  'a missed connection without a replacement: the arrival is unknown');

update public.abeona_trips set replacement_legs = jsonb_build_array(jsonb_build_object(
  'mode', 'train', 'line_name', 'RE 20',
  'from', jsonb_build_object('name', 'Würzburg Hbf', 'planned', '2026-10-14 13:48 Europe/Berlin'::timestamptz),
  'to', jsonb_build_object('name', 'Bamberg', 'planned', '2026-10-14 15:00 Europe/Berlin'::timestamptz,
                           'current', '2026-10-14 15:05 Europe/Berlin'::timestamptz)))
where id = (select id from ar_ids where key = 'missed');
select ok((select c->>'delay_source' = 'replacement' and (c->>'delay_minutes')::int = 65 and c->>'verdict' = 'eligible'
           from (select public.abeona_compensation((select id from ar_ids where key = 'missed')) as c) x),
  'the replacement''s live arrival is the real arrival: 65 minutes');
select ok((public.abeona_compensation((select id from ar_ids where key = 'missed'))->>'summary_de') like '%Stattdessen gefahren: RE 20%',
  'the summary names the train taken instead');

-- ── Claims ─────────────────────────────────────────────────────────────────
insert into public.abeona_claims (trip_id, status, amount_cents, claimed_at)
values ((select id from ar_ids where key = 'bc1'), 'paid', 1500, now()),
       ((select id from ar_ids where key = 'due'), 'claimed', 1550, now());
update public.abeona_claims set paid_at = now() where status = 'paid';

select is((public.abeona_claims_overview(extract(year from now())::int)->>'earned_cents')::int, 1500,
  'the year''s total earned back');
select is(jsonb_array_length(public.abeona_claims_overview(extract(year from now())::int)->'waiting'), 1,
  'a claimed trip waits for an answer');

-- ── User B sees none of it ─────────────────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0af00-0000-4000-8000-000000000002", "role": "authenticated"}';
select public.abeona_ensure_household();

select is((select count(*)::int from public.abeona_claims), 0, 'another household sees no claims');
select throws_ok(
  format($$ insert into public.abeona_claims (trip_id) values (%L) $$, (select id from ar_ids where key = 'flex')),
  '42501', null, 'and cannot claim for a trip that is not theirs');
select is(public.abeona_compensation((select id from ar_ids where key = 'flex')), null,
  'nor see what it is worth');

select * from finish();

rollback;
