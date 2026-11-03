-- Abeona, #38 phase 5: freeze what actually happened on a ride.
--
-- Transitous carries DB's realtime for the current day only. A trip nobody
-- opened after it arrived kept its last refreshed (or planned) times, and the
-- statistics and the compensation read those. The abeona-refresh-trips Edge
-- Function, run by pg_cron, refreshes trips 15 to 90 minutes after they
-- arrived and marks their legs final; from then on nothing overwrites them
-- (decisions/2026-11-03-abeona-final-snapshot-job.md).
--
-- Only additions: older APKs keep reading and writing what they did before.

-- ── Columns ────────────────────────────────────────────────────────────────

-- A final leg's live values (departure, arrival, platforms, cancelled,
-- realtime, stopovers) are what happened. Set by abeona_finalize_trip only.
alter table public.abeona_legs
  add column if not exists final boolean not null default false;

-- When every ride of the trip was final. Null until then; a trip the job
-- never reached keeps its last refreshed values and stays null.
alter table public.abeona_trips
  add column if not exists finalized_at timestamptz;

-- The job looks for trips that arrived a little while ago and are not final.
create index if not exists abeona_trips_unfinalized_arrival_idx
  on public.abeona_trips (planned_arrival)
  where finalized_at is null and status = 'planned';

-- ── Final values stay final ────────────────────────────────────────────────

-- Clients write live values through abeona_update_trip_live, and members may
-- update legs directly under RLS. Once a leg is final, its live values are
-- kept whatever the update says, and it cannot be made un-final. Planned
-- values were never meant to change either, so they are kept too.
create or replace function public.abeona_legs_keep_final()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if old.final then
    new.final := true;
    new.departure := old.departure;
    new.arrival := old.arrival;
    new.departure_platform := old.departure_platform;
    new.arrival_platform := old.arrival_platform;
    new.cancelled := old.cancelled;
    new.realtime := old.realtime;
    new.stopovers := old.stopovers;
    new.planned_departure := old.planned_departure;
    new.planned_arrival := old.planned_arrival;
  end if;
  return new;
end;
$$;

drop trigger if exists abeona_legs_keep_final on public.abeona_legs;
create trigger abeona_legs_keep_final
  before update on public.abeona_legs
  for each row execute function public.abeona_legs_keep_final();

-- Same signature as before; final legs are left alone (the trigger would keep
-- them anyway), and refreshed_at only moves when something was written.
create or replace function public.abeona_update_trip_live(p_trip_id uuid, p_legs jsonb)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  update public.abeona_legs l set
    departure = (x->>'departure')::timestamptz,
    arrival = (x->>'arrival')::timestamptz,
    departure_platform = coalesce(x->>'departure_platform', l.departure_platform),
    arrival_platform = coalesce(x->>'arrival_platform', l.arrival_platform),
    cancelled = coalesce((x->>'cancelled')::boolean, l.cancelled),
    realtime = coalesce((x->>'realtime')::boolean, l.realtime),
    stopovers = coalesce(x->'stopovers', l.stopovers)
  from jsonb_array_elements(p_legs) as x
  where l.trip_id = p_trip_id
    and l.idx = (x->>'idx')::smallint
    and not l.final;

  update public.abeona_trips set refreshed_at = now(), updated_at = now()
  where id = p_trip_id
    and finalized_at is null;
end;
$$;

revoke all on function public.abeona_update_trip_live(uuid, jsonb) from public, anon;
grant execute on function public.abeona_update_trip_live(uuid, jsonb) to authenticated;

-- ── For the job (service role only) ────────────────────────────────────────

-- Trips whose expected arrival passed between p_min_age and p_max_age ago and
-- that are not final yet, oldest first, at most p_limit — with what a refresh
-- needs. The expected arrival is the last ride's current arrival, else its
-- planned one; a cancelled trip (status) is skipped.
create or replace function public.abeona_trips_to_finalize(
  p_limit integer default 10,
  p_min_age interval default interval '15 minutes',
  p_max_age interval default interval '90 minutes'
)
returns table (
  trip_id uuid,
  refresh_token text,
  expected_arrival timestamptz,
  legs jsonb
)
language sql
stable
security invoker
set search_path = public
as $$
  with candidates as (
    select t.id, t.refresh_token,
      coalesce((
        select coalesce(l.arrival, l.planned_arrival)
        from public.abeona_legs l
        where l.trip_id = t.id and l.mode <> 'walk'
        order by l.idx desc limit 1
      ), t.planned_arrival) as expected_arrival
    from public.abeona_trips t
    where t.finalized_at is null
      and t.status = 'planned'
      -- The index's range: a delay of up to a day still finds the trip.
      and t.planned_arrival > now() - p_max_age - interval '1 day'
      and t.planned_arrival < now()
  )
  select c.id, c.refresh_token, c.expected_arrival,
    coalesce((
      select jsonb_agg(jsonb_build_object(
        'idx', l.idx, 'mode', l.mode, 'final', l.final, 'trip_ref', l.trip_ref,
        'from_id', l.from_id, 'to_id', l.to_id, 'from_name', l.from_name, 'to_name', l.to_name
      ) order by l.idx)
      from public.abeona_legs l where l.trip_id = c.id
    ), '[]'::jsonb)
  from candidates c
  where c.expected_arrival <= now() - p_min_age
    and c.expected_arrival > now() - p_max_age
  order by c.expected_arrival
  limit greatest(0, least(p_limit, 50));
$$;

-- Writes a refresh's values onto the legs it covers and marks them final;
-- legs without an answer stay as they are. A time the answer lacks (no
-- realtime any more) keeps the last live value rather than erasing it. Once every ride (not walk) is
-- final, the trip is finalized. Final legs are never written again (the
-- trigger). Returns whether the trip is final now.
-- p_legs: [ { idx, departure, arrival, departure_platform, arrival_platform,
--             cancelled, realtime, stopovers } ] — abeona_update_trip_live's shape.
create or replace function public.abeona_finalize_trip(p_trip_id uuid, p_legs jsonb)
returns boolean
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_final boolean;
begin
  if jsonb_typeof(p_legs) is distinct from 'array' then
    raise exception 'p_legs must be an array.';
  end if;

  update public.abeona_legs l set
    departure = coalesce((x->>'departure')::timestamptz, l.departure),
    arrival = coalesce((x->>'arrival')::timestamptz, l.arrival),
    departure_platform = coalesce(x->>'departure_platform', l.departure_platform),
    arrival_platform = coalesce(x->>'arrival_platform', l.arrival_platform),
    cancelled = coalesce((x->>'cancelled')::boolean, l.cancelled),
    realtime = coalesce((x->>'realtime')::boolean, l.realtime),
    stopovers = coalesce(x->'stopovers', l.stopovers),
    final = true
  from jsonb_array_elements(p_legs) as x
  where l.trip_id = p_trip_id
    and l.idx = (x->>'idx')::smallint
    and not l.final;

  -- Walks have nothing to refresh; they are final with the rides.
  select not exists (
    select 1 from public.abeona_legs l
    where l.trip_id = p_trip_id and l.mode <> 'walk' and not l.final
  ) into v_final;

  if v_final then
    update public.abeona_legs set final = true
    where trip_id = p_trip_id and mode = 'walk' and not final;
  end if;

  update public.abeona_trips set
    refreshed_at = now(),
    updated_at = now(),
    finalized_at = case when v_final then coalesce(finalized_at, now()) else finalized_at end
  where id = p_trip_id;

  return v_final;
end;
$$;

-- Only the Edge Function calls these two, with the service role, which
-- bypasses RLS: they run as the caller, and nobody else may call them.
revoke all on function public.abeona_trips_to_finalize(integer, interval, interval) from public, anon, authenticated;
revoke all on function public.abeona_finalize_trip(uuid, jsonb) from public, anon, authenticated;
grant execute on function public.abeona_trips_to_finalize(integer, interval, interval) to service_role;
grant execute on function public.abeona_finalize_trip(uuid, jsonb) to service_role;
