-- Abeona fare rules: the reference table, and what abeona_allowed_products()
-- says a trip's ticket covers — with the binding held, lifted by delay or by
-- a cancellation, and never IC/ICE on a Deutschlandticket.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(22);

insert into auth.users (id, email) values
  ('abe0fa00-0000-4000-8000-000000000001', 'abeona-fr-a@test.local'),
  ('abe0fa00-0000-4000-8000-000000000002', 'abeona-fr-b@test.local');

insert into public.profiles (id, email, full_name) values
  ('abe0fa00-0000-4000-8000-000000000001', 'abeona-fr-a@test.local', 'Ada'),
  ('abe0fa00-0000-4000-8000-000000000002', 'abeona-fr-b@test.local', 'Ben')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table fr_ids (key text primary key, id uuid);
grant select, insert, update on fr_ids to authenticated;

-- ICE 25 Frankfurt → Würzburg, then RE 10 to Bamberg, today.
create temporary table fr_legs as
select jsonb_build_array(
  jsonb_build_object(
    'mode', 'train', 'product', 'ICE', 'line_name', 'ICE 25',
    'from', jsonb_build_object('id', 'x:ffm', 'name', 'Frankfurt Hbf', 'planned', (now() + interval '1 hour')::text),
    'to', jsonb_build_object('id', 'x:wue', 'name', 'Würzburg Hbf', 'planned', (now() + interval '2 hours')::text)
  ),
  jsonb_build_object(
    'mode', 'train', 'product', 'RE', 'line_name', 'RE 10',
    'from', jsonb_build_object('id', 'x:wue', 'name', 'Würzburg Hbf', 'planned', (now() + interval '130 minutes')::text),
    'to', jsonb_build_object('id', 'x:bam', 'name', 'Bamberg', 'planned', (now() + interval '3 hours')::text)
  )
) as legs;
grant select on fr_legs to authenticated;

-- ── The table ──────────────────────────────────────────────────────────────

select is((select count(*)::int from public.abeona_fare_rules), 5,
  'every known fare type has a rule; other has none');

select ok(not has_table_privilege('anon', 'public.abeona_fare_rules', 'select'),
  'anon cannot read the fare rules');

select ok(not has_function_privilege('anon', 'public.abeona_allowed_products(uuid)', 'execute'),
  'anon cannot execute abeona_allowed_products');

select ok(has_function_privilege('authenticated', 'public.abeona_allowed_products(uuid)', 'execute'),
  'authenticated can execute abeona_allowed_products');

-- ── User A ─────────────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0fa00-0000-4000-8000-000000000001", "role": "authenticated"}';

select is((select count(*)::int from public.abeona_fare_rules), 5, 'a signed-in user reads the rules');

select throws_ok(
  $$ update public.abeona_fare_rules set long_distance = true where fare_type = 'deutschlandticket' $$,
  '42501', null, 'nobody rewrites the rules through the API');

select public.abeona_ensure_household();

insert into fr_ids values
  ('saver', public.abeona_save_trip('{"fare_type": "saver", "train_binding": true}'::jsonb, (select legs from fr_legs))),
  ('dticket', public.abeona_save_trip('{"fare_type": "deutschlandticket"}'::jsonb, (select legs from fr_legs))),
  ('regional', public.abeona_save_trip('{"fare_type": "regional"}'::jsonb, (select legs from fr_legs))),
  ('flex', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, (select legs from fr_legs))),
  ('bc100', public.abeona_save_trip('{"fare_type": "bahncard100"}'::jsonb, (select legs from fr_legs))),
  ('other', public.abeona_save_trip('{"fare_type": "other"}'::jsonb, (select legs from fr_legs)));

create temporary table fr_allowed (key text primary key, a jsonb);
insert into fr_allowed
select key, public.abeona_allowed_products(id) from fr_ids;

-- Saver, on time: bound to the booked trains.
select is((select a->>'train_bound' from fr_allowed where key = 'saver'), 'true',
  'a saver on time is bound to its trains');
select is((select a->>'binding_lifted' from fr_allowed where key = 'saver'), 'false',
  'and its binding is not lifted');
select is((select a->'booked_lines' from fr_allowed where key = 'saver'), '["ICE 25", "RE 10"]'::jsonb,
  'the booked lines are the rides, in order');

-- 19 minutes late at Bamberg: still bound.
update public.abeona_legs set arrival = planned_arrival + interval '19 minutes'
where trip_id = (select id from fr_ids where key = 'saver') and idx = 1;
select is((public.abeona_allowed_products((select id from fr_ids where key = 'saver'))->>'train_bound'), 'true',
  '19 minutes late: still bound');

-- 20 minutes late: lifted.
update public.abeona_legs set arrival = planned_arrival + interval '20 minutes'
where trip_id = (select id from fr_ids where key = 'saver') and idx = 1;
select is((public.abeona_allowed_products((select id from fr_ids where key = 'saver'))->>'train_bound'), 'false',
  '20 minutes late: the binding no longer holds');
select is((public.abeona_allowed_products((select id from fr_ids where key = 'saver'))->>'binding_lifted'), 'true',
  '20 minutes late: the binding is lifted');
select is((public.abeona_allowed_products((select id from fr_ids where key = 'saver'))->>'arrival_delay_minutes')::int, 20,
  'the delay is the final arrival delay');

-- On time again, but the RE is cancelled: lifted too.
update public.abeona_legs set arrival = null, cancelled = (idx = 1)
where trip_id = (select id from fr_ids where key = 'saver');
select is((public.abeona_allowed_products((select id from fr_ids where key = 'saver'))->>'binding_lifted'), 'true',
  'a cancelled ride lifts the binding');

-- Deutschlandticket: never IC/ICE, even when late or cancelled.
update public.abeona_legs set arrival = planned_arrival + interval '45 minutes', cancelled = true
where trip_id = (select id from fr_ids where key = 'dticket');
select is((public.abeona_allowed_products((select id from fr_ids where key = 'dticket'))->>'long_distance'), 'false',
  'a Deutschlandticket never covers long-distance trains');
select ok((public.abeona_allowed_products((select id from fr_ids where key = 'dticket'))->'excluded_products') ?& array['IC', 'ICE', 'EC'],
  'a Deutschlandticket excludes IC, ICE and EC, even 45 minutes late with a cancellation');
select is((public.abeona_allowed_products((select id from fr_ids where key = 'dticket'))->>'binding_lifted'), 'false',
  'a Deutschlandticket has no binding to lift');

select ok((select (a->'excluded_products') ? 'ICE' and a->>'long_distance' = 'false' from fr_allowed where key = 'regional'),
  'a regional ticket excludes the ICE');

select ok((select a->>'long_distance' = 'true' and a->>'train_bound' = 'false' and not (a->'excluded_products') ? 'ICE'
           from fr_allowed where key = 'flex'),
  'a Flexpreis covers any train');

select ok((select a->>'long_distance' = 'true' and not (a->'excluded_products') ? 'ICE' from fr_allowed where key = 'bc100'),
  'a BahnCard 100 covers the ICE');

select is((select a->>'known' from fr_allowed where key = 'other'), 'false',
  'an unknown fare is known to be unknown, so the clients say check the terms');

-- ── User B: a stranger's trip is not there ─────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0fa00-0000-4000-8000-000000000002", "role": "authenticated"}';

select is(public.abeona_allowed_products((select id from fr_ids where key = 'saver')), null,
  'another household''s trip yields nothing');

select * from finish();

rollback;
