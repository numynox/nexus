-- Abeona, phase 4: which trains a trip's ticket allows, kept as data.
--
-- The alternatives a client offers when a change is at risk or a train is
-- cancelled are marked by whether the ticket covers them. Those terms change
-- (DB has reworded its Sparpreis conditions more than once), so they live in
-- a table, not in either client: a correction is an UPDATE, not an APK
-- (decisions/2026-11-03-abeona-fare-rules-and-device-signals.md).
--
-- Only additions: older APKs keep reading what they read before.

-- ── Table ──────────────────────────────────────────────────────────────────

-- One row per abeona_trips.fare_type. 'other' has none on purpose: Abeona
-- does not know its terms, and the clients say "check the terms" for every
-- alternative.
create table if not exists public.abeona_fare_rules (
  fare_type text primary key
    check (fare_type in ('flex', 'saver', 'regional', 'deutschlandticket', 'bahncard100', 'other')),
  -- Whether IC, ICE, EC and the other long-distance products are covered at all.
  long_distance boolean not null,
  -- Products (as the transit contract names them: "ICE", "RJX", "FLX") the
  -- ticket never covers. Compared case-insensitively.
  excluded_products text[] not null default '{}',
  -- A ticket of this kind that is train-bound (abeona_trips.train_binding)
  -- covers only the booked trains while the binding holds.
  train_bound boolean not null default false,
  -- Whether DB lifts that binding: expected delay at the destination of at
  -- least 20 minutes (abeona_train_binding_lifted) or a cancelled train.
  binding_liftable boolean not null default false,
  -- Shown with the alternatives, always ending in "check the terms": the
  -- rules here are a summary, not the conditions of carriage.
  hint text not null check (btrim(hint) <> ''),
  updated_at timestamptz not null default now()
);

comment on table public.abeona_fare_rules is
  'Which trains each Abeona fare type covers: read by abeona_allowed_products() and the abeona-transit alternatives action.';

-- Long-distance products, as Transitous shows them for Germany, Austria,
-- Switzerland, Italy, Denmark and France, plus FlixTrain (never on a DB ticket).
insert into public.abeona_fare_rules
  (fare_type, long_distance, excluded_products, train_bound, binding_liftable, hint)
values
  ('flex', true, array['FLX'], false, false,
   'A Flexpreis covers any DB train on your route, including IC and ICE. Not FlixTrain. Check the terms on your ticket.'),
  ('saver', true, array['FLX'], true, true,
   'A Sparpreis is bound to the booked trains. Once DB expects you 20 minutes late or cancels a train, any train on your route counts. Check the terms on your ticket.'),
  ('regional', false,
   array['ICE', 'ICD', 'IC', 'EC', 'ECE', 'RJ', 'RJX', 'NJ', 'EN', 'TGV', 'EST', 'FR', 'FA', 'FB', 'IR', 'D', 'WB', 'FLX'],
   false, false,
   'A regional ticket covers RE, RB and S-Bahn trains only, never IC, EC or ICE. Check the terms on your ticket.'),
  ('deutschlandticket', false,
   array['ICE', 'ICD', 'IC', 'EC', 'ECE', 'RJ', 'RJX', 'NJ', 'EN', 'TGV', 'EST', 'FR', 'FA', 'FB', 'IR', 'D', 'WB', 'FLX'],
   false, false,
   'The Deutschlandticket covers local and regional trains in Germany, never IC, EC or ICE. Check the terms for trains abroad.'),
  ('bahncard100', true, array['FLX'], false, false,
   'A BahnCard 100 covers any DB train, including IC and ICE. Not FlixTrain. Check the terms for trains abroad.')
on conflict (fare_type) do nothing;

alter table public.abeona_fare_rules enable row level security;

-- Reference data: every signed-in user reads it, nobody writes it through the
-- API. Corrections come as migrations.
drop policy if exists abeona_fare_rules_select on public.abeona_fare_rules;
create policy abeona_fare_rules_select on public.abeona_fare_rules
  for select to authenticated
  using (true);

revoke all on table public.abeona_fare_rules from anon;
revoke insert, update, delete, truncate on table public.abeona_fare_rules from authenticated;
grant select on table public.abeona_fare_rules to authenticated;

-- ── What the ticket allows now ─────────────────────────────────────────────

-- What a trip's ticket covers at this moment, as one JSON object:
--   trip_id, fare_type
--   known              false when there is no rule for the fare type ('other')
--   long_distance      IC/ICE/EC covered at all
--   excluded_products  products never covered
--   train_bound        the binding holds now: only booked_lines count
--   binding_lifted     the binding was there and is lifted (delay or cancellation)
--   arrival_delay_minutes  final arrival delay from the last saved live values
--   booked_lines       the line names of the trip's rides ("ICE 25"), in order
--   hint               English text both clients show as is
-- Null for a trip the caller cannot see. Runs as the caller, so RLS on
-- abeona_trips is the access check.
--
-- The delay is read like abeona_statistics does: the last ride's current
-- arrival against its planned one. The binding rule is
-- abeona_train_binding_lifted() (mirrored in both clients), widened by a
-- cancelled ride, which also lifts DB's binding.
create or replace function public.abeona_allowed_products(p_trip_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with trip as (
    select t.id, t.fare_type, t.train_binding
    from public.abeona_trips t
    where t.id = p_trip_id
  ),
  rides as (
    select l.idx, l.line_name, l.cancelled, l.planned_arrival, l.arrival
    from public.abeona_legs l
    join trip on trip.id = l.trip_id
    where l.mode <> 'walk'
  ),
  facts as (
    select trip.*,
      coalesce((
        select round(extract(epoch from (coalesce(r.arrival, r.planned_arrival) - r.planned_arrival)) / 60)::integer
        from rides r order by r.idx desc limit 1
      ), 0) as delay,
      exists (select 1 from rides r where r.cancelled) as any_cancelled,
      coalesce((select jsonb_agg(r.line_name order by r.idx) from rides r where r.line_name is not null), '[]'::jsonb) as lines
    from trip
  )
  select jsonb_build_object(
    'trip_id', f.id,
    'fare_type', f.fare_type,
    'known', r.fare_type is not null,
    'long_distance', coalesce(r.long_distance, true),
    'excluded_products', to_jsonb(coalesce(r.excluded_products, '{}'::text[])),
    'train_bound',
      coalesce(r.train_bound, false) and f.train_binding
      and not (coalesce(r.binding_liftable, false)
               and (public.abeona_train_binding_lifted(f.fare_type, f.train_binding, f.delay) or f.any_cancelled)),
    'binding_lifted',
      coalesce(r.train_bound, false) and f.train_binding and coalesce(r.binding_liftable, false)
      and (public.abeona_train_binding_lifted(f.fare_type, f.train_binding, f.delay) or f.any_cancelled),
    'arrival_delay_minutes', f.delay,
    'booked_lines', f.lines,
    'hint', coalesce(r.hint, 'Abeona does not know this ticket''s terms. Check them before you board another train.')
  )
  from facts f
  left join public.abeona_fare_rules r on r.fare_type = f.fare_type;
$$;

revoke all on function public.abeona_allowed_products(uuid) from public, anon;
grant execute on function public.abeona_allowed_products(uuid) to authenticated;
