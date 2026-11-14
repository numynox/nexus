-- Abeona: a ride the timetable no longer has, on a date it covers.
--
-- A ride saved without the timetable is looked up again by both clients
-- (intake/match.ts checkSavedLeg, Match.kt). When the timetable covers its
-- date (later trains of the same kind run between its stops) and the ride is
-- not there, it no longer runs as booked: a timetable change, usually
-- engineering works, which DB answers by asking for another connection and
-- lifting the train binding.
--
-- Such a ride is marked cancelled, so everything that already reads a
-- cancellation follows: abeona_allowed_products lifts the binding
-- ('cancelled'), the alternatives are offered, compensation waits for the
-- trains taken instead, and older APKs see it as cancelled. dropped_at says
-- why, and when it was noticed, so the clients can say "no longer in the
-- timetable" rather than "cancelled".
--
-- Nothing is dropped or renamed: older APKs read abeona_legs as before.

alter table public.abeona_legs add column if not exists dropped_at timestamptz;

comment on column public.abeona_legs.dropped_at is
  'When a ride saved without the timetable was found missing from it on a date it covers; the ride is then also cancelled.';

-- Marks a ride dropped. Only a ride still unfound (no trip ref), not final and
-- not marked yet; returns whether this call marked it, so a client tells the
-- traveller once. security invoker: abeona_legs' RLS decides who may.
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
    and trip_ref is null
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

-- As in 20261114100000_abeona_attach_leg, plus: a ride the timetable has
-- again is no longer dropped, and its cancellation is the timetable's.
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
    and l.trip_ref is null
    and not l.final
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
