-- Abeona, #38 phases 6–7: the history grows up.
--
-- - Countries: a nullable country on each end of a leg, filled by
--   abeona_save_trip from the transit contract's StopTime.country (which the
--   Edge Function derives from the stop's time zone). Legs saved before, or by
--   an APK that drops the field, fall back to the country code inside the
--   stop id (DELFI's "de:09162:100", "at:45:50002", "ch:1:sloid:10").
-- - Past trips: abeona_save_trip with source 'import' saves the legs final and
--   the trip finalized, so neither the snapshot job nor a refresh touches what
--   the user entered. An optional arrival_delay_minutes sets the last ride's
--   real arrival when the legs carry none.
-- - Statistics: CO₂ saved against the car, countries, the longest trip.
--   Fields are only added; older APKs read the old ones.
-- - The map of everything travelled: abeona_travel_map, the household's rides
--   in a window, one simplified line per pair of stations.
--
-- Only additions: older APKs keep reading and writing what they did before.

-- ── Countries ──────────────────────────────────────────────────────────────

alter table public.abeona_legs
  add column if not exists from_country text
    check (from_country is null or from_country ~ '^[A-Z]{2}$'),
  add column if not exists to_country text
    check (to_country is null or to_country ~ '^[A-Z]{2}$');

-- The ISO country of a stop: the stored one, else the two letters before the
-- first ":<digits>" of its id. Null when neither says.
create or replace function public.abeona_stop_country(p_country text, p_stop_id text)
returns text
language sql
immutable
as $$
  select coalesce(
    case when p_country ~ '^[A-Za-z]{2}$' then upper(p_country) end,
    upper(substring(coalesce(p_stop_id, '') from '([a-z]{2}):[0-9]+'))
  );
$$;

grant execute on function public.abeona_stop_country(text, text) to authenticated;

-- ── Saving a trip ──────────────────────────────────────────────────────────

-- As in 20261103110000_abeona_bahncards, plus:
-- - each leg's from.country / to.country (ISO alpha-2, else null);
-- - each leg's distance_km, used when it has no route line;
-- - p_trip.source = 'import' (a past trip, from the search, Träwelling or the
--   CSV script): the legs are saved final and the trip finalized, so nothing
--   refreshes them later; p_trip.arrival_delay_minutes, if given, sets the
--   last ride's arrival to its planned arrival plus that many minutes when the
--   leg brings no current arrival of its own.
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
      fare_type, train_binding, travel_class, price_cents, bahncard_discount,
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
      (p_trip->>'bahncard_discount')::smallint,
      now(),
      case when v_import then now() end
    )
    returning id into v_trip_id;

    if jsonb_typeof(p_trip->'traveller_ids') = 'array' then
      insert into public.abeona_trip_travellers (trip_id, traveller_id)
      select v_trip_id, t.id
      from public.abeona_travellers t
      where t.household_id = v_household
        and t.id in (select x::uuid from jsonb_array_elements_text(p_trip->'traveller_ids') as x)
      on conflict do nothing;
    else
      insert into public.abeona_trip_travellers (trip_id, traveller_id)
      select v_trip_id, t.id
      from public.abeona_travellers t
      where t.household_id = v_household
        and t.user_id = auth.uid()
      on conflict do nothing;
    end if;
  else
    update public.abeona_trips set
      refresh_token = coalesce(p_trip->>'refresh_token', refresh_token),
      refreshed_at = now(),
      updated_at = now()
    where id = v_trip_id;

    if not found then
      raise exception 'Trip not found.';
    end if;

    delete from public.abeona_legs where trip_id = v_trip_id;
  end if;

  for v_leg in select * from jsonb_array_elements(p_legs) loop
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
      cancelled, realtime, stopovers, polyline, distance_km, final
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
      v_import
    );
    v_idx := v_idx + 1;
  end loop;

  return v_trip_id;
end;
$$;

revoke all on function public.abeona_save_trip(jsonb, jsonb) from public, anon;
grant execute on function public.abeona_save_trip(jsonb, jsonb) to authenticated;

-- ── CO₂ ────────────────────────────────────────────────────────────────────

-- Grams of CO₂-equivalent per passenger-kilometre, APPROXIMATE. Source:
-- Umweltbundesamt (UBA), "Vergleich der durchschnittlichen Emissionen
-- einzelner Verkehrsmittel im Personenverkehr in Deutschland", TREMOD,
-- reference year 2022, as published in 2023/24 (umweltbundesamt.de,
-- "Emissionsdaten"). Verify there before relying on the figures; they move
-- every year with the electricity mix and the occupancy. "Saved" is what the
-- same distance would have cost by car, minus what the train did, for one
-- traveller.
create or replace function public.abeona_co2_factors()
returns jsonb
language sql
immutable
as $$
  select jsonb_build_object(
    'car', 166,           -- Pkw
    'rail_long', 46,      -- Eisenbahn, Fernverkehr
    'rail_regional', 55,  -- Eisenbahn, Nahverkehr (incl. S-Bahn)
    'bus', 96,            -- Linienbus, Nahverkehr
    'source', 'Umweltbundesamt (TREMOD), reference year 2022; approximate',
    'unit', 'g CO2e per passenger-km'
  );
$$;

grant execute on function public.abeona_co2_factors() to authenticated;

-- What a ride emits per km: long-distance products, buses, everything else
-- as regional rail.
create or replace function public.abeona_co2_g_per_km(p_mode text, p_product text)
returns numeric
language sql
immutable
as $$
  select (public.abeona_co2_factors()->>(
    case
      when p_mode = 'bus' then 'bus'
      when upper(coalesce(p_product, '')) in
        ('ICE', 'ICD', 'IC', 'EC', 'ECE', 'RJ', 'RJX', 'NJ', 'EN', 'TGV', 'EST', 'FR', 'FA', 'FB', 'IR', 'D', 'WB', 'FLX')
        then 'rail_long'
      else 'rail_regional'
    end
  ))::numeric;
$$;

grant execute on function public.abeona_co2_g_per_km(text, text) to authenticated;

-- ── Statistics ─────────────────────────────────────────────────────────────

-- As in 20261103150000_abeona_trip_disruption, plus:
--   co2_saved_kg     what the rides saved against the car, one traveller (approximate)
--   co2_train_kg     what the rides emitted
--   co2_car_kg       what the same distance would have emitted by car
--   co2_factors      abeona_co2_factors(), so the clients can name the source
--   countries        ISO codes of the stations ridden from or to, most often first
--   longest_trip     { trip_id, from, to, km, date } or null
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

-- ── The map of everything travelled ────────────────────────────────────────

-- Every [[lon, lat], …] line thinned to at most p_max points by taking every
-- n-th point, keeping both ends. Enough for a map of a year at country scale.
create or replace function public.abeona_thin_polyline(p_polyline jsonb, p_max integer default 48)
returns jsonb
language sql
immutable
as $$
  with pts as (
    select pt, ord, count(*) over () as total
    from jsonb_array_elements(
      case when jsonb_typeof(p_polyline) = 'array' then p_polyline else '[]'::jsonb end
    ) with ordinality as e(pt, ord)
  )
  select case when count(*) < 2 then null else jsonb_agg(pt order by ord) end
  from pts
  where total <= greatest(p_max, 2)
     or ord = 1
     or ord = total
     or (ord - 1) % ceil(total::numeric / (greatest(p_max, 2) - 1))::integer = 0;
$$;

-- The household's rides that arrived in [p_from, p_to), one line per pair of
-- stations (either direction), with how often it was ridden. The line is the
-- most recent ride's route, thinned; without one, straight between the stops.
-- Runs as the caller: RLS and the household filter decide.
create or replace function public.abeona_travel_map(p_from timestamptz, p_to timestamptz)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with rides as (
    select l.*, t.planned_departure as trip_departure
    from public.abeona_legs l
    join public.abeona_trips t on t.id = l.trip_id
    where t.household_id = public.abeona_current_household()
      and t.status = 'planned'
      and t.planned_arrival >= p_from
      and t.planned_arrival < least(p_to, now())
      and l.mode <> 'walk'
      and not l.cancelled
      and l.from_lat is not null and l.from_lon is not null
      and l.to_lat is not null and l.to_lon is not null
  ),
  keyed as (
    select r.*,
      least(r.from_name, r.to_name) || ' | ' || greatest(r.from_name, r.to_name) as pair,
      row_number() over (
        partition by least(r.from_name, r.to_name), greatest(r.from_name, r.to_name)
        order by (r.polyline is not null) desc, r.trip_departure desc
      ) as rn,
      count(*) over (partition by least(r.from_name, r.to_name), greatest(r.from_name, r.to_name)) as n
    from rides r
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'from', jsonb_build_object('name', from_name, 'lat', from_lat, 'lon', from_lon),
    'to', jsonb_build_object('name', to_name, 'lat', to_lat, 'lon', to_lon),
    'product', product,
    'count', n,
    'polyline', coalesce(
      public.abeona_thin_polyline(polyline, 48),
      jsonb_build_array(jsonb_build_array(from_lon, from_lat), jsonb_build_array(to_lon, to_lat))
    )
  ) order by n desc, pair), '[]'::jsonb)
  from keyed
  where rn = 1;
$$;

revoke all on function public.abeona_travel_map(timestamptz, timestamptz) from public, anon;
grant execute on function public.abeona_travel_map(timestamptz, timestamptz) to authenticated;
