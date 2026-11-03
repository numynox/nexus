-- Abeona, #38 phase 5: record what happened when the plan broke.
--
-- A traveller who missed a change or whose train was cancelled took other
-- trains. Their real arrival, not the plan's, decides the delay and with it
-- the compensation. Kept small: two columns on the trip, in the transit
-- contract's leg shape, so a connection picked from the alternatives is
-- stored as it came and a manual entry is a leg with only what is known.
--
-- abeona_trip_arrival() is the one place that says when a trip really
-- arrived; the compensation and the statistics read it.
--
-- Only additions: older APKs keep reading what they read before.

alter table public.abeona_trips
  -- The ride (abeona_legs.idx) the traveller missed or that was cancelled
  -- under them; null when nothing was recorded.
  add column if not exists missed_leg_idx smallint
    check (missed_leg_idx is null or missed_leg_idx >= 0),
  -- The rides actually taken from there on, as transit-contract legs
  -- ({ mode, line_name, from: {name, planned, current}, to: {…}, … }).
  -- The last one's arrival (current, else planned) is the real arrival.
  add column if not exists replacement_legs jsonb
    check (replacement_legs is null or jsonb_typeof(replacement_legs) = 'array');

-- When a trip really arrived, as one JSON object:
--   planned        the last ride's planned arrival
--   actual         the real arrival, or null when it is not known
--   delay_minutes  actual − planned in whole minutes, never negative; null when unknown
--   source         'replacement' (recorded trains), 'final' (frozen by the job),
--                  'live' (the last refresh), 'planned' (no realtime at all),
--                  'unknown' (a ride was cancelled or missed and nothing replaced it)
--   final          whether the trip's own legs are final
-- Null for a trip the caller cannot see; runs as the caller.
create or replace function public.abeona_trip_arrival(p_trip_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with trip as (
    select t.id, t.planned_arrival, t.missed_leg_idx, t.replacement_legs, t.finalized_at
    from public.abeona_trips t
    where t.id = p_trip_id
  ),
  last_ride as (
    select l.planned_arrival, l.arrival, l.final, l.cancelled
    from public.abeona_legs l
    join trip on trip.id = l.trip_id
    where l.mode <> 'walk'
    order by l.idx desc
    limit 1
  ),
  replaced as (
    select coalesce(nullif(r.leg->'to'->>'current', ''), nullif(r.leg->'to'->>'planned', ''))::timestamptz as arrival
    from trip, lateral (
      select e.leg, e.n
      from jsonb_array_elements(coalesce(trip.replacement_legs, '[]'::jsonb)) with ordinality as e(leg, n)
      where coalesce(e.leg->>'mode', 'train') <> 'walk'
      order by e.n desc
      limit 1
    ) r
  ),
  facts as (
    select
      coalesce(lr.planned_arrival, trip.planned_arrival) as planned,
      case
        when (select arrival from replaced) is not null then 'replacement'
        when trip.missed_leg_idx is not null then 'unknown'
        when exists (
          select 1 from public.abeona_legs l
          where l.trip_id = trip.id and l.mode <> 'walk' and l.cancelled
        ) then 'unknown'
        when lr.final and lr.arrival is not null then 'final'
        when lr.arrival is not null then 'live'
        else 'planned'
      end as source,
      trip.finalized_at is not null as final,
      lr.arrival as live_arrival
    from trip
    left join last_ride lr on true
  ),
  resolved as (
    select f.*,
      case f.source
        when 'replacement' then (select arrival from replaced)
        when 'unknown' then null
        when 'planned' then f.planned
        else f.live_arrival
      end as actual
    from facts f
  )
  select jsonb_build_object(
    'planned', r.planned,
    'actual', r.actual,
    'delay_minutes', case when r.actual is null then null
      else greatest(0, round(extract(epoch from (r.actual - r.planned)) / 60))::integer end,
    'source', r.source,
    'final', r.final
  )
  from resolved r;
$$;

revoke all on function public.abeona_trip_arrival(uuid) from public, anon;
grant execute on function public.abeona_trip_arrival(uuid) to authenticated;

-- The statistics read the same arrival: final values once the job froze
-- them, the recorded replacement when there is one. Unchanged otherwise.
create or replace function public.abeona_statistics(p_from timestamptz, p_to timestamptz)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with trips as (
    select t.*
    from public.abeona_trips t
    where t.household_id = public.abeona_current_household()
      and t.status = 'planned'
      and t.planned_arrival >= p_from
      and t.planned_arrival < least(p_to, now())
  ),
  rides as (
    select l.*
    from public.abeona_legs l
    join trips t on t.id = l.trip_id
    where l.mode <> 'walk' and not l.cancelled
  ),
  per_trip as (
    select t.id, t.origin_name, t.destination_name, t.planned_arrival,
      coalesce((public.abeona_trip_arrival(t.id)->>'delay_minutes')::numeric, 0) as delay_min,
      (select coalesce(sum(r.distance_km), 0) from rides r where r.trip_id = t.id) as km
    from trips t
  ),
  stations as (
    select from_name as name from rides
    union all
    select to_name from rides
  )
  select jsonb_build_object(
    'trips', (select count(*) from per_trip),
    'km', (select coalesce(round(sum(km)::numeric, 0), 0) from per_trip),
    'minutes_on_train', (select coalesce(round(sum(extract(epoch from (coalesce(arrival, planned_arrival) - coalesce(departure, planned_departure))) / 60)), 0) from rides),
    'rides', (select count(*) from rides),
    'delay_minutes', (select coalesce(round(sum(delay_min)), 0) from per_trip),
    'punctual_share', (select case when count(*) = 0 then null else round(avg(case when delay_min < 6 then 1.0 else 0.0 end), 3) end from per_trip),
    'longest_km', (select coalesce(round(max(km)::numeric, 0), 0) from per_trip),
    'max_delay_minutes', (select coalesce(round(max(delay_min)), 0) from per_trip),
    'stations', (select count(distinct name) from stations),
    'top_route', (
      select jsonb_build_object('from', origin_name, 'to', destination_name, 'count', n)
      from (
        select least(origin_name, destination_name) as origin_name,
               greatest(origin_name, destination_name) as destination_name,
               count(*) as n
        from per_trip group by 1, 2 order by n desc, 1 limit 1
      ) r
    ),
    'top_station', (
      select jsonb_build_object('name', name, 'count', n)
      from (select name, count(*) as n from stations group by name order by n desc, name limit 1) s
    ),
    'products', coalesce((
      select jsonb_object_agg(product, n)
      from (select coalesce(product, 'other') as product, count(*) as n from rides group by 1) p
    ), '{}'::jsonb),
    'by_month', coalesce((
      select jsonb_agg(jsonb_build_object('month', month, 'trips', trips, 'km', km) order by month)
      from (
        select to_char(planned_arrival at time zone public.nexus_time_zone(), 'YYYY-MM') as month,
               count(*) as trips,
               round(sum(km)::numeric, 0) as km
        from per_trip group by 1
      ) m
    ), '[]'::jsonb)
  );
$$;

revoke all on function public.abeona_statistics(timestamptz, timestamptz) from public, anon;
grant execute on function public.abeona_statistics(timestamptz, timestamptz) to authenticated;
