-- Abeona history and polish (#38 phases 6–7): countries and past trips in
-- abeona_save_trip, the statistics' additions, the map of everything
-- travelled and its household scoping, share links under RLS and what a token
-- opens, the transit cache and its pruning, and the orphaned-file finder.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(58);

insert into auth.users (id, email) values
  ('abe0b100-0000-4000-8000-000000000001', 'abeona-hi-a@test.local'),
  ('abe0b100-0000-4000-8000-000000000002', 'abeona-hi-b@test.local');

insert into public.profiles (id, email, full_name) values
  ('abe0b100-0000-4000-8000-000000000001', 'abeona-hi-a@test.local', 'Ada'),
  ('abe0b100-0000-4000-8000-000000000002', 'abeona-hi-b@test.local', 'Ben')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table hi_ids (key text primary key, id uuid, token text);
grant select, insert, update on hi_ids to authenticated, service_role;

-- Munich → Salzburg (DE → AT), with the contract's country on the stops and a
-- route of 60 points; departing at p_departure.
create function pg_temp.salzburg(p_departure timestamptz) returns jsonb language sql as $$
  select jsonb_build_array(jsonb_build_object(
    'mode', 'train', 'product', 'RJX', 'line_name', 'RJX 61', 'trip_ref', 'ref-61',
    'from', jsonb_build_object('id', 'de-DELFI_de:09162:100', 'name', 'München Hbf', 'lat', 48.1402, 'lon', 11.5586,
      'country', 'DE', 'planned', p_departure::text),
    'to', jsonb_build_object('id', 'de-DELFI_at:45:50002', 'name', 'Salzburg Hbf', 'lat', 47.8131, 'lon', 13.0458,
      'country', 'AT', 'planned', (p_departure + interval '105 minutes')::text),
    'polyline', (select jsonb_agg(jsonb_build_array(11.5586 + i * (13.0458 - 11.5586) / 59, 48.1402 + i * (47.8131 - 48.1402) / 59) order by i)
                 from generate_series(0, 59) as i)
  ));
$$;
-- Basel SBB → Freiburg (CH → DE) from an older client: no country fields, the
-- stop ids say it.
create function pg_temp.basel(p_departure timestamptz) returns jsonb language sql as $$
  select jsonb_build_array(jsonb_build_object(
    'mode', 'train', 'product', 'ICE', 'line_name', 'ICE 70',
    'from', jsonb_build_object('id', 'ch-opentransportdataswiss26_Parentch:1:sloid:10', 'name', 'Basel SBB', 'lat', 47.5474, 'lon', 7.5896,
      'planned', p_departure::text),
    'to', jsonb_build_object('id', 'de-DELFI_de:08311:6508', 'name', 'Freiburg Hbf', 'lat', 47.9977, 'lon', 7.8412,
      'planned', (p_departure + interval '45 minutes')::text)
  ));
$$;
grant execute on function pg_temp.salzburg(timestamptz), pg_temp.basel(timestamptz) to authenticated;

-- ── Grants ─────────────────────────────────────────────────────────────────

select ok(not has_function_privilege('authenticated', 'public.abeona_shared_trip(text)', 'execute'),
  'a signed-in user cannot open a token through the database');
select ok(not has_function_privilege('anon', 'public.abeona_shared_trip(text)', 'execute'),
  'anon cannot open a token through the database');
select ok(has_function_privilege('service_role', 'public.abeona_shared_trip(text)', 'execute'),
  'the service role opens tokens (abeona-public)');
select ok(not has_table_privilege('anon', 'public.abeona_share_links', 'select'),
  'anon cannot read share links');
select ok(not has_table_privilege('authenticated', 'public.abeona_share_links', 'update'),
  'a share link is never edited, only replaced');
select ok(not has_table_privilege('authenticated', 'public.abeona_transit_cache', 'select'),
  'a signed-in user cannot read the transit cache');
select ok(not has_table_privilege('anon', 'public.abeona_transit_cache', 'insert'),
  'anon cannot write the transit cache');
select ok(not has_function_privilege('authenticated', 'public.abeona_orphaned_files(integer, interval)', 'execute'),
  'a signed-in user cannot list orphaned files');
select ok(not has_function_privilege('anon', 'public.abeona_travel_map(timestamptz, timestamptz)', 'execute'),
  'anon cannot ask for the map');

-- ── User A: trips ──────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0b100-0000-4000-8000-000000000001", "role": "authenticated"}';

select public.abeona_ensure_household();

insert into hi_ids (key, id) values
  ('salzburg', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, pg_temp.salzburg('2026-10-10 08:00 Europe/Berlin'))),
  ('salzburg2', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, pg_temp.salzburg('2026-10-20 08:00 Europe/Berlin'))),
  ('basel', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, pg_temp.basel('2026-10-12 09:00 Europe/Berlin'))),
  ('past', public.abeona_save_trip('{"fare_type": "saver", "source": "import", "arrival_delay_minutes": 23}'::jsonb,
                                   pg_temp.basel(now() - interval '2 hours'))),
  ('future', public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, pg_temp.salzburg(now() + interval '2 days')));

-- Countries
select is((select from_country || '>' || to_country from public.abeona_legs where trip_id = (select id from hi_ids where key = 'salzburg')),
  'DE>AT', 'the contract''s countries are stored on the leg');
select ok((select from_country is null from public.abeona_legs where trip_id = (select id from hi_ids where key = 'basel')),
  'a leg without countries stores none');
select is(public.abeona_stop_country(null, 'ch-opentransportdataswiss26_Parentch:1:sloid:10'), 'CH',
  'the country falls back to the code in the stop id');
select is(public.abeona_stop_country('at', 'de-DELFI_de:1:2'), 'AT', 'a stored country wins over the id');
select is(public.abeona_stop_country(null, 'no-country-here'), null, 'no country when neither says');

-- A past trip, imported
select ok((select finalized_at is not null from public.abeona_trips where id = (select id from hi_ids where key = 'past')),
  'an imported trip is saved finalized');
select ok((select bool_and(final) from public.abeona_legs where trip_id = (select id from hi_ids where key = 'past')),
  'an imported trip''s legs are final');
select is((select source from public.abeona_trips where id = (select id from hi_ids where key = 'past')), 'import',
  'an imported trip says so');
select is((public.abeona_trip_arrival((select id from hi_ids where key = 'past'))->>'delay_minutes')::integer, 23,
  'the delay entered by hand is the real arrival''s');
select is(public.abeona_trip_arrival((select id from hi_ids where key = 'past'))->>'source', 'final',
  'and it reads as final');
select ok((select arrival is null and final from public.abeona_legs where trip_id = (select id from hi_ids where key = 'salzburg')) is false,
  'a searched trip''s legs are not final');
select throws_ok(
  $$select public.abeona_save_trip('{"source": "import", "arrival_delay_minutes": -5}'::jsonb, pg_temp.basel(now() - interval '1 day'))$$,
  'P0001', 'The arrival delay must be between 0 and 1440 minutes.', 'a negative delay is refused');

-- Final legs stay as entered when a client refreshes them anyway.
select public.abeona_update_trip_live((select id from hi_ids where key = 'past'),
  '[{"idx": 0, "arrival": "2020-01-01T00:00:00Z"}]'::jsonb);
select is((public.abeona_trip_arrival((select id from hi_ids where key = 'past'))->>'delay_minutes')::integer, 23,
  'a refresh does not overwrite an imported arrival');

-- Statistics: the additions, and the old fields still there
select is((public.abeona_statistics('2026-10-01', '2026-11-01')->>'trips')::integer, 3, 'three trips in October');
select ok(public.abeona_statistics('2026-10-01', '2026-11-01') ?& array['km', 'longest_km', 'by_month', 'products', 'top_route'],
  'the fields older clients read are all still there');
select is(public.abeona_statistics('2026-10-01', '2026-11-01')->'countries', '["DE", "AT", "CH"]'::jsonb,
  'countries, most often first, with the id fallback for the older leg');
select is(public.abeona_statistics('2026-10-01', '2026-11-01')->'longest_trip'->>'from', 'München Hbf',
  'the longest trip is Munich–Salzburg');
select is(public.abeona_statistics('2026-10-01', '2026-11-01')->'longest_trip'->>'date', '2026-10-10',
  'the first of two equally long trips, by date');
select ok((public.abeona_statistics('2026-10-01', '2026-11-01')->>'co2_saved_kg')::numeric > 0, 'trains saved CO₂');
select is(
  (select round(sum(distance_km) * (166 - 46) / 1000.0, 1) from public.abeona_legs
   where trip_id in (select id from hi_ids where key in ('salzburg', 'salzburg2', 'basel'))),
  (public.abeona_statistics('2026-10-01', '2026-11-01')->>'co2_saved_kg')::numeric,
  'CO₂ saved = km × (car − long-distance rail), all three are long-distance');
select is(public.abeona_statistics('2026-10-01', '2026-11-01')->'co2_factors'->>'car', '166', 'the factors are named');
select is((public.abeona_statistics('2030-01-01', '2031-01-01')->>'co2_saved_kg')::numeric, 0::numeric, 'nothing saved without trips');
select is(public.abeona_statistics('2030-01-01', '2031-01-01')->'longest_trip', 'null'::jsonb, 'no longest trip without trips');

-- The map
select is(jsonb_array_length(public.abeona_travel_map('2026-10-01', '2026-11-01')), 2,
  'two lines: Munich–Salzburg (twice) and Basel–Freiburg');
select is((public.abeona_travel_map('2026-10-01', '2026-11-01')->0->>'count')::integer, 2, 'the line ridden twice comes first, counted');
select ok(jsonb_array_length(public.abeona_travel_map('2026-10-01', '2026-11-01')->0->'polyline') <= 48,
  'a route is thinned to 48 points at most');
select is(jsonb_array_length(public.abeona_travel_map('2026-10-01', '2026-11-01')->1->'polyline'), 2,
  'a ride without a route is a straight line between its stops');
select is(jsonb_array_length(public.abeona_travel_map(now(), now() + interval '1 year')), 0,
  'trips that have not arrived are not on the map');

-- Share links
insert into public.abeona_share_links (trip_id, kind, token, expires_at)
values ((select id from hi_ids where key = 'future'), 'status', 'aaaa', '2100-01-01');
insert into public.abeona_share_links (trip_id, kind)
values ((select id from hi_ids where key = 'future'), 'ics');
update hi_ids set token = (select token from public.abeona_share_links where kind = 'status' and trip_id = hi_ids.id) where key = 'future';

select ok((select token ~ '^[0-9a-f]{36}$' from hi_ids where key = 'future'), 'the database makes the token, whatever the insert said');
select ok((select expires_at < now() + interval '3 days' from public.abeona_share_links where kind = 'status' and trip_id = (select id from hi_ids where key = 'future')),
  'a status link expires six hours after the trip, not when the client said');
select ok((select expires_at > now() + interval '30 days' from public.abeona_share_links where kind = 'ics' and trip_id = (select id from hi_ids where key = 'future')),
  'a calendar link lasts 30 days past the trip');
select is((select created_by from public.abeona_share_links where kind = 'ics' and trip_id = (select id from hi_ids where key = 'future')),
  'abe0b100-0000-4000-8000-000000000001'::uuid, 'the creator is recorded');
select throws_ok(
  $$insert into public.abeona_share_links (trip_id, kind) values ((select id from hi_ids where key = 'future'), 'status')$$,
  '23505', null, 'one link per trip and kind');

-- ── User B: another household ──────────────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0b100-0000-4000-8000-000000000002", "role": "authenticated"}';
select public.abeona_ensure_household();

select is((select count(*)::integer from public.abeona_share_links), 0, 'another household sees no share links');
select throws_ok(
  $$insert into public.abeona_share_links (trip_id, kind) values ((select id from hi_ids where key = 'salzburg'), 'status')$$,
  '42501', null, 'another household cannot share a trip');
delete from public.abeona_share_links;
select is(jsonb_array_length(public.abeona_travel_map('2026-10-01', '2026-11-01')), 0, 'another household''s map is empty');
select is((public.abeona_statistics('2026-10-01', '2026-11-01')->>'trips')::integer, 0, 'and so are its statistics');

-- ── Back as A: the delete above did nothing; revoking works ────────────────
set local request.jwt.claims = '{"sub": "abe0b100-0000-4000-8000-000000000001", "role": "authenticated"}';
select is((select count(*)::integer from public.abeona_share_links), 2, 'B''s delete did not touch A''s links');
delete from public.abeona_share_links where kind = 'ics';
select is((select count(*)::integer from public.abeona_share_links), 1, 'a member revokes a link by deleting it');

-- ── The service role: what a token opens ───────────────────────────────────
reset role;
set local role service_role;

select is(public.abeona_shared_trip((select token from hi_ids where key = 'future'))->>'kind', 'status', 'a live token opens its trip');
select is(public.abeona_shared_trip((select token from hi_ids where key = 'future'))->'trip'->>'destination', 'Salzburg Hbf',
  'with its route');
select ok(not (public.abeona_shared_trip((select token from hi_ids where key = 'future'))->'trip' ?| array['title', 'price_cents', 'household_id', 'ticket_path', 'id']),
  'but not its title, price, household, ticket or id');
select is(public.abeona_shared_trip('0123456789abcdef0123456789abcdef0123'), null, 'an unknown token opens nothing');
select is(public.abeona_shared_trip('aaaa'), null, 'nor does a malformed one');
reset role;
update public.abeona_share_links set expires_at = now() - interval '1 minute';
set local role service_role;
select is(public.abeona_shared_trip((select token from hi_ids where key = 'future')), null, 'an expired token opens nothing');

-- ── Pruning ────────────────────────────────────────────────────────────────
reset role;
insert into public.abeona_transit_cache (key, payload, fetched_at) values
  ('hi-old', '{}'::jsonb, now() - interval '3 days'),
  ('hi-fresh', '{}'::jsonb, now() - interval '1 hour');
update public.abeona_share_links set expires_at = now() - interval '8 days';
select ok(public.run_nexus_maintenance() ?& array['abeona_transit_cache_deleted', 'abeona_share_links_deleted'],
  'the nightly maintenance runs both Abeona steps');
select is((select array_agg(key order by key) from public.abeona_transit_cache where key like 'hi-%'), array['hi-fresh'],
  'old cache rows go, fresh ones stay');
select is((select count(*)::integer from public.abeona_share_links where trip_id = (select id from hi_ids where key = 'future')), 0,
  'long-expired links are tidied away');

-- ── Orphaned files ─────────────────────────────────────────────────────────
update public.abeona_trips
set ticket_path = (select household_id from public.abeona_trips where id = (select id from hi_ids where key = 'salzburg'))::text
                  || '/' || (select id from hi_ids where key = 'salzburg')::text || '/kept.pdf'
where id = (select id from hi_ids where key = 'salzburg');

insert into storage.objects (bucket_id, name, created_at) values
  ('abeona-tickets', (select ticket_path from public.abeona_trips where id = (select id from hi_ids where key = 'salzburg')), now() - interval '3 days'),
  ('abeona-tickets', 'abe0b100-0000-4000-8000-00000000000a/' || (select id from hi_ids where key = 'salzburg')::text || '/replaced.pdf', now() - interval '3 days'),
  ('abeona-tickets', 'abe0b100-0000-4000-8000-00000000000a/bahncards/abe0b100-0000-4000-8000-0000000000cc/card.pdf', now() - interval '3 days'),
  ('abeona-tickets', 'abe0b100-0000-4000-8000-00000000000a/bahncards/abe0b100-0000-4000-8000-0000000000cd/new.pdf', now() - interval '1 hour'),
  ('abeona-tickets', 'somewhere/else.pdf', now() - interval '3 days');

set local role service_role;
select set_eq(
  $$select n from public.abeona_orphaned_files(50) as n where n like 'abe0b100%' or n like 'somewhere%'$$,
  array[
    'abe0b100-0000-4000-8000-00000000000a/' || (select id from hi_ids where key = 'salzburg')::text || '/replaced.pdf',
    'abe0b100-0000-4000-8000-00000000000a/bahncards/abe0b100-0000-4000-8000-0000000000cc/card.pdf'
  ],
  'orphans are the replaced ticket and the deleted card''s file; not the kept ticket, a fresh upload or a foreign path');

select * from finish();
rollback;
