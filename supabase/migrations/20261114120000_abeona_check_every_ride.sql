-- Abeona: every ride of an upcoming trip is checked against the timetable,
-- not only the ones intake could not find.
--
-- A train found when the trip was saved can be gone after a timetable change
-- (engineering works) just the same. The clients now check every ride
-- (intake/match.ts checkSavedLeg, Match.kt), so:
--   * abeona_mark_leg_dropped marks a found ride too (it keeps its trip ref,
--     which the live refresh then simply no longer finds);
--   * abeona_attach_leg also takes the timetable's ride for a ride that has a
--     trip ref, when it is dropped (back again) or its trip ref changed (a
--     feed renumbers its trips, and the old ref would refresh nothing).
-- Same signatures as before: older APKs keep calling them as they did.

create or replace function public.abeona_mark_leg_dropped(p_trip_id uuid, p_idx smallint)
returns boolean
language plpgsql
security invoker
set search_path = public
as $$
begin
  update public.abeona_legs set cancelled = true, dropped_at = now()
  where trip_id = p_trip_id
    and idx = p_idx
    and mode <> 'walk'
    and not final
    and dropped_at is null;
  if not found then
    return false;
  end if;
  update public.abeona_trips set updated_at = now() where id = p_trip_id;
  return true;
end;
$$;

revoke all on function public.abeona_mark_leg_dropped(uuid, smallint) from public, anon;
grant execute on function public.abeona_mark_leg_dropped(uuid, smallint) to authenticated;

create or replace function public.abeona_attach_leg(p_trip_id uuid, p_idx smallint, p_leg jsonb)
returns boolean
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_km double precision := public.abeona_polyline_km(p_leg->'polyline');
begin
  if nullif(p_leg->>'trip_ref', '') is null then
    return false;
  end if;

  update public.abeona_legs l set
    trip_ref = p_leg->>'trip_ref',
    product = coalesce(p_leg->>'product', l.product),
    headsign = coalesce(p_leg->>'headsign', l.headsign),
    operator = coalesce(p_leg->>'operator', l.operator),
    from_id = coalesce(p_leg->'from'->>'id', l.from_id),
    from_lat = coalesce((p_leg->'from'->>'lat')::double precision, l.from_lat),
    from_lon = coalesce((p_leg->'from'->>'lon')::double precision, l.from_lon),
    from_country = coalesce(public.abeona_stop_country(p_leg->'from'->>'country', null), l.from_country),
    to_id = coalesce(p_leg->'to'->>'id', l.to_id),
    to_lat = coalesce((p_leg->'to'->>'lat')::double precision, l.to_lat),
    to_lon = coalesce((p_leg->'to'->>'lon')::double precision, l.to_lon),
    to_country = coalesce(public.abeona_stop_country(p_leg->'to'->>'country', null), l.to_country),
    departure = (p_leg->'from'->>'current')::timestamptz,
    arrival = (p_leg->'to'->>'current')::timestamptz,
    planned_departure_platform = coalesce(p_leg->'from'->>'planned_platform', l.planned_departure_platform),
    departure_platform = coalesce(p_leg->'from'->>'platform', l.departure_platform),
    planned_arrival_platform = coalesce(p_leg->'to'->>'planned_platform', l.planned_arrival_platform),
    arrival_platform = coalesce(p_leg->'to'->>'platform', l.arrival_platform),
    cancelled = coalesce((p_leg->>'cancelled')::boolean, false),
    realtime = coalesce((p_leg->>'realtime')::boolean, l.realtime),
    stopovers = coalesce(p_leg->'stopovers', l.stopovers),
    polyline = coalesce(p_leg->'polyline', l.polyline),
    distance_km = coalesce(round(v_km::numeric, 1), l.distance_km),
    dropped_at = null
  where l.trip_id = p_trip_id
    and l.idx = p_idx
    and not l.final
    and (l.trip_ref is null or l.dropped_at is not null or l.trip_ref <> p_leg->>'trip_ref')
    and abs(extract(epoch from (p_leg->'from'->>'planned')::timestamptz - l.planned_departure)) < 60
    and abs(extract(epoch from (p_leg->'to'->>'planned')::timestamptz - l.planned_arrival)) < 60;

  if not found then
    return false;
  end if;

  update public.abeona_trips set refreshed_at = now(), updated_at = now()
  where id = p_trip_id;
  return true;
end;
$$;
