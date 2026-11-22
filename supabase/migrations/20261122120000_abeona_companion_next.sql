-- Abeona: coach and seats from the ticket, the top speed seen on board, and
-- what a finished trip adds up to (decisions/2026-11-22-abeona-coach-and-seat-from-the-ticket.md,
-- decisions/2026-11-22-abeona-arrival-postcard.md).
--
-- The columns are new and nullable: older APKs neither read nor send them,
-- and abeona_save_trip keeps them when a client re-saves a trip without
-- them. Members write them directly under the legs' RLS (the edit sheet, the
-- companion), as they already may.

alter table public.abeona_legs
  add column if not exists coach text,
  add column if not exists seats text,
  add column if not exists top_speed_kmh smallint;

alter table public.abeona_legs drop constraint if exists abeona_legs_coach_check;
alter table public.abeona_legs add constraint abeona_legs_coach_check
  check (coach is null or char_length(coach) between 1 and 8);
alter table public.abeona_legs drop constraint if exists abeona_legs_seats_check;
alter table public.abeona_legs add constraint abeona_legs_seats_check
  check (seats is null or char_length(seats) between 1 and 40);
alter table public.abeona_legs drop constraint if exists abeona_legs_top_speed_check;
alter table public.abeona_legs add constraint abeona_legs_top_speed_check
  check (top_speed_kmh is null or top_speed_kmh between 1 and 450);

comment on column public.abeona_legs.coach is
  'The coach number of the seat reservation, as on the ticket ("24"); from the ticket or typed in.';
comment on column public.abeona_legs.seats is
  'The reserved seats in that coach, as on the ticket ("66", "71 73", "45-48").';
comment on column public.abeona_legs.top_speed_kmh is
  'The highest speed the train''s Wi-Fi reported during this ride (Android companion); null when none was seen.';

-- As in 20261113140000_abeona_no_travellers, plus each leg's coach and seats
-- (p_legs[].coach, p_legs[].seats). A re-save keeps a ride's coach, seats and
-- top speed when the new leg does not say otherwise, matched by its departure
-- station and planned time.
create or replace function public.abeona_save_trip(p_trip jsonb, p_legs jsonb)
returns uuid
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_household uuid := public.abeona_ensure_household();
  v_trip_id uuid := nullif(p_trip->>'id', '')::uuid;
  v_source text := coalesce(nullif(p_trip->>'source', ''), 'search');
  v_import boolean;
  v_delay integer := nullif(p_trip->>'arrival_delay_minutes', '')::integer;
  v_leg jsonb;
  v_idx integer := 0;
  v_count integer;
  v_last_ride integer;
  v_first jsonb;
  v_last jsonb;
  v_km double precision;
  v_arrival timestamptz;
  -- What a re-save would otherwise lose: the ticket's coach and seats and the
  -- top speed seen on board, per ride (its departure station and planned time).
  v_kept jsonb := '{}'::jsonb;
  v_key text;
  v_travellers smallint := nullif(p_trip->>'traveller_count', '')::smallint;
  v_discounts smallint[] := case
    when jsonb_typeof(p_trip->'bahncard_discounts') = 'array' then
      coalesce((select array_agg(x::smallint order by x::smallint)
                from jsonb_array_elements_text(p_trip->'bahncard_discounts') as x), '{}')
    else '{}'
  end;
begin

  if jsonb_typeof(p_legs) <> 'array' or jsonb_array_length(p_legs) = 0 then
    raise exception 'A trip needs at least one leg.';
  end if;
  if v_delay is not null and (v_delay < 0 or v_delay > 1440) then
    raise exception 'The arrival delay must be between 0 and 1440 minutes.';
  end if;

  v_import := v_source = 'import';
  v_count := jsonb_array_length(p_legs);
  v_first := p_legs->0;
  v_last := p_legs->(v_count - 1);

  -- The last leg that is not a walk carries the real arrival.
  select max(n - 1) into v_last_ride
  from jsonb_array_elements(p_legs) with ordinality as e(leg, n)
  where coalesce(e.leg->>'mode', 'train') <> 'walk';

  if v_trip_id is null then
    insert into public.abeona_trips (
      household_id, created_by, title, source, provider, refresh_token,
      origin_name, destination_name, planned_departure, planned_arrival,
      fare_type, train_binding, travel_class, price_cents,
      traveller_count, bahncard_discounts,
      refreshed_at, finalized_at
    ) values (
      v_household, auth.uid(),
      nullif(btrim(p_trip->>'title'), ''),
      v_source,
      coalesce(p_trip->>'provider', 'transitous'),
      p_trip->>'refresh_token',
      v_first->'from'->>'name',
      v_last->'to'->>'name',
      (v_first->'from'->>'planned')::timestamptz,
      (v_last->'to'->>'planned')::timestamptz,
      coalesce(p_trip->>'fare_type', 'other'),
      coalesce((p_trip->>'train_binding')::boolean, false),
      coalesce((p_trip->>'travel_class')::smallint, 2),
      (p_trip->>'price_cents')::integer,
      v_travellers,
      v_discounts,
      now(),
      case when v_import then now() end
    )
    returning id into v_trip_id;
  else
    update public.abeona_trips set
      refresh_token = coalesce(p_trip->>'refresh_token', refresh_token),
      refreshed_at = now(),
      updated_at = now()
    where id = v_trip_id;

    if not found then
      raise exception 'Trip not found.';
    end if;

    select coalesce(jsonb_object_agg(
             coalesce(l.from_name, '') || '|' || coalesce(l.planned_departure::text, ''),
             jsonb_build_object('coach', l.coach, 'seats', l.seats, 'top_speed_kmh', l.top_speed_kmh)
           ), '{}'::jsonb)
      into v_kept
      from public.abeona_legs l
     where l.trip_id = v_trip_id
       and (l.coach is not null or l.seats is not null or l.top_speed_kmh is not null);

    delete from public.abeona_legs where trip_id = v_trip_id;
  end if;

  for v_leg in select * from jsonb_array_elements(p_legs) loop
    v_key := coalesce(v_leg->'from'->>'name', '') || '|' || coalesce(((v_leg->'from'->>'planned')::timestamptz)::text, '');
    v_km := public.abeona_polyline_km(v_leg->'polyline');
    -- Without a route, a distance the source knows (Träwelling's along the
    -- track, a CSV's column) beats the great-circle estimate.
    if v_km is null and nullif(v_leg->>'distance_km', '') is not null then
      v_km := greatest(0, (v_leg->>'distance_km')::double precision);
    end if;
    if v_km is null and coalesce(v_leg->>'mode', 'train') <> 'walk' then
      v_km := 1.1 * public.abeona_haversine_km(
        (v_leg->'from'->>'lat')::double precision, (v_leg->'from'->>'lon')::double precision,
        (v_leg->'to'->>'lat')::double precision, (v_leg->'to'->>'lon')::double precision
      );
    end if;

    v_arrival := (v_leg->'to'->>'current')::timestamptz;
    if v_import and v_delay is not null and v_idx = v_last_ride and v_arrival is null then
      v_arrival := (v_leg->'to'->>'planned')::timestamptz + make_interval(mins => v_delay);
    end if;

    insert into public.abeona_legs (
      trip_id, idx, mode, product, line_name, trip_ref, headsign, operator,
      from_id, from_name, from_lat, from_lon, from_country,
      to_id, to_name, to_lat, to_lon, to_country,
      planned_departure, departure, planned_arrival, arrival,
      planned_departure_platform, departure_platform,
      planned_arrival_platform, arrival_platform,
      cancelled, realtime, stopovers, polyline, distance_km, final,
      coach, seats, top_speed_kmh
    ) values (
      v_trip_id, v_idx,
      coalesce(v_leg->>'mode', 'train'),
      v_leg->>'product', v_leg->>'line_name', v_leg->>'trip_ref',
      v_leg->>'headsign', v_leg->>'operator',
      v_leg->'from'->>'id', v_leg->'from'->>'name',
      (v_leg->'from'->>'lat')::double precision, (v_leg->'from'->>'lon')::double precision,
      public.abeona_stop_country(v_leg->'from'->>'country', null),
      v_leg->'to'->>'id', v_leg->'to'->>'name',
      (v_leg->'to'->>'lat')::double precision, (v_leg->'to'->>'lon')::double precision,
      public.abeona_stop_country(v_leg->'to'->>'country', null),
      (v_leg->'from'->>'planned')::timestamptz, (v_leg->'from'->>'current')::timestamptz,
      (v_leg->'to'->>'planned')::timestamptz, v_arrival,
      v_leg->'from'->>'planned_platform', v_leg->'from'->>'platform',
      v_leg->'to'->>'planned_platform', v_leg->'to'->>'platform',
      coalesce((v_leg->>'cancelled')::boolean, false),
      coalesce((v_leg->>'realtime')::boolean, false),
      coalesce(v_leg->'stopovers', '[]'::jsonb),
      v_leg->'polyline',
      round(v_km::numeric, 1),
      v_import,
      coalesce(nullif(btrim(left(v_leg->>'coach', 8)), ''), v_kept->v_key->>'coach'),
      coalesce(nullif(btrim(left(v_leg->>'seats', 40)), ''), v_kept->v_key->>'seats'),
      (v_kept->v_key->>'top_speed_kmh')::smallint
    );
    v_idx := v_idx + 1;
  end loop;

  return v_trip_id;
end;
$$;

revoke all on function public.abeona_save_trip(jsonb, jsonb) from public, anon;
grant execute on function public.abeona_save_trip(jsonb, jsonb) to authenticated;

-- As in 20261104090000_abeona_history, plus
--   top_speed_kmh    the highest speed seen on board in the period, or null
--   top_speed_trip   { trip_id, from, to, line, kmh, date } of that ride, or null
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
    select t.id, t.origin_name, t.destination_name, t.planned_arrival, t.planned_departure,
      coalesce((public.abeona_trip_arrival(t.id)->>'delay_minutes')::numeric, 0) as delay_min,
      (select coalesce(sum(r.distance_km), 0) from rides r where r.trip_id = t.id) as km
    from trips t
  ),
  stations as (
    select from_name as name from rides
    union all
    select to_name from rides
  ),
  countries as (
    select public.abeona_stop_country(from_country, from_id) as code from rides
    union all
    select public.abeona_stop_country(to_country, to_id) from rides
  ),
  co2 as (
    select
      coalesce(sum(coalesce(r.distance_km, 0) * public.abeona_co2_g_per_km(r.mode, r.product)), 0) / 1000.0 as train_kg,
      coalesce(sum(coalesce(r.distance_km, 0) * (public.abeona_co2_factors()->>'car')::numeric), 0) / 1000.0 as car_kg
    from rides r
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
    ), '[]'::jsonb),
    'co2_train_kg', (select round(train_kg, 1) from co2),
    'co2_car_kg', (select round(car_kg, 1) from co2),
    'co2_saved_kg', (select round(greatest(0, car_kg - train_kg), 1) from co2),
    'co2_factors', public.abeona_co2_factors(),
    'countries', coalesce((
      select jsonb_agg(code order by n desc, code)
      from (select code, count(*) as n from countries where code is not null group by code) c
    ), '[]'::jsonb),
    'top_speed_kmh', (select max(top_speed_kmh) from rides),
    'top_speed_trip', (
      select jsonb_build_object(
        'trip_id', r.trip_id, 'from', r.from_name, 'to', r.to_name, 'line', r.line_name,
        'kmh', r.top_speed_kmh,
        'date', to_char(r.planned_departure at time zone public.nexus_time_zone(), 'YYYY-MM-DD')
      )
      from rides r
      where r.top_speed_kmh is not null
      order by r.top_speed_kmh desc, r.planned_departure
      limit 1
    ),
    'longest_trip', (
      select jsonb_build_object(
        'trip_id', id, 'from', origin_name, 'to', destination_name,
        'km', round(km::numeric, 0),
        'date', to_char(planned_departure at time zone public.nexus_time_zone(), 'YYYY-MM-DD')
      )
      from per_trip
      where km > 0
      order by km desc, planned_departure
      limit 1
    )
  );
$$;

revoke all on function public.abeona_statistics(timestamptz, timestamptz) from public, anon;
grant execute on function public.abeona_statistics(timestamptz, timestamptz) to authenticated;

-- What a finished trip adds up to, for its postcard: the rides as stored (a
-- connection followed instead keeps the plan's distance, which is close
-- enough for a postcard), the delay at the destination as abeona_trip_arrival
-- says, and the CO2 figures the year in review uses. Runs as the caller: RLS
-- decides, and a trip the caller cannot see gives null.
create or replace function public.abeona_trip_summary(p_trip_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with trip as (
    select t.id from public.abeona_trips t where t.id = p_trip_id
  ),
  rides as (
    select l.*
    from public.abeona_legs l
    join trip on trip.id = l.trip_id
    where l.mode <> 'walk' and not l.cancelled
  ),
  co2 as (
    select
      coalesce(sum(coalesce(r.distance_km, 0) * public.abeona_co2_g_per_km(r.mode, r.product)), 0) / 1000.0 as train_kg,
      coalesce(sum(coalesce(r.distance_km, 0) * (public.abeona_co2_factors()->>'car')::numeric), 0) / 1000.0 as car_kg
    from rides r
  )
  select case when not exists (select 1 from trip) then null else jsonb_build_object(
    'rides', (select count(*) from rides),
    'km', (select coalesce(round(sum(distance_km)::numeric, 0), 0) from rides),
    'minutes_on_train', (
      select coalesce(round(sum(extract(epoch from (coalesce(arrival, planned_arrival) - coalesce(departure, planned_departure))) / 60)), 0)
      from rides
    ),
    'delay_minutes', coalesce(round((public.abeona_trip_arrival(p_trip_id)->>'delay_minutes')::numeric), 0),
    'top_speed_kmh', (select max(top_speed_kmh) from rides),
    'co2_train_kg', (select round(train_kg, 1) from co2),
    'co2_car_kg', (select round(car_kg, 1) from co2),
    'co2_saved_kg', (select round(greatest(0, car_kg - train_kg), 1) from co2)
  ) end;
$$;

revoke all on function public.abeona_trip_summary(uuid) from public, anon;
grant execute on function public.abeona_trip_summary(uuid) to authenticated;
