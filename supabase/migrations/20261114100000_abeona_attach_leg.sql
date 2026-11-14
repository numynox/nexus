-- Abeona: a ride saved without the timetable, attached to it once found.
--
-- Intake keeps a ride the timetable did not have (a date after the next
-- timetable change, usually) as printed: no trip ref, no route line, no
-- stops, never refreshed. Both clients look such a ride up again now and
-- then (intake/match.ts findSavedLeg, Match.kt) and, once the timetable has
-- it, hand it here. One function, so web and Android write the same columns.
--
-- Only an unfound ride takes it (trip_ref still null, not final), and only the
-- same ride: the planned departure and arrival must be the saved minutes. The
-- printed names stay, as the trip's ends were taken from them; the places,
-- route, stops, platforms and live values become the timetable's.
--
-- security invoker: abeona_legs' RLS (household members) decides who may.
-- Returns whether a leg was attached.

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
    cancelled = coalesce((p_leg->>'cancelled')::boolean, l.cancelled),
    realtime = coalesce((p_leg->>'realtime')::boolean, l.realtime),
    stopovers = coalesce(p_leg->'stopovers', l.stopovers),
    polyline = coalesce(p_leg->'polyline', l.polyline),
    distance_km = coalesce(round(v_km::numeric, 1), l.distance_km)
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

revoke all on function public.abeona_attach_leg(uuid, smallint, jsonb) from public, anon;
grant execute on function public.abeona_attach_leg(uuid, smallint, jsonb) to authenticated;
