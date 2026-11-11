-- Distance driven per day, from Bluetooth drives only — the logbook's
-- "Distance driven" graph.
--
-- get_car_daily_distance mixes two kinds of day: tracked legs spread over the
-- days that were driven (and the open leg estimated drive by drive), and every
-- other leg booked whole on the day of the refuel that closed it. The graph
-- wants only the first kind, so this takes get_car_daily_distance and removes
-- the refuel-day lumps again, rather than repeating its spreading logic.
--
-- One row per Europe/Berlin day from the first recorded drive (or p_since,
-- whichever is later) to today, 0 where nothing was driven. No drives, no
-- rows. Access as get_car_daily_distance: its inner functions check
-- user_can_access_car.

create or replace function public.get_car_distance_driven_per_day(
  p_car_id uuid,
  p_since timestamptz default '-infinity'
)
returns table (
  day date,
  km numeric
)
language sql
stable
set search_path = public
as $$
  with tz as (
    select public.nexus_time_zone() as name
  ),
  bounds as (
    select
      greatest(
        (min(p.started_at) at time zone tz.name)::date,
        (p_since at time zone tz.name)::date
      ) as since,
      (now() at time zone tz.name)::date as today
    from public.get_car_drive_periods(p_car_id) p
    cross join tz
    group by tz.name
    having min(p.started_at) is not null
  ),
  daily as (
    select d.day, d.km + d.estimated_km as km
    from public.get_car_daily_distance(p_car_id, p_since) d
  ),
  -- The legs get_car_daily_distance books on their refuel day.
  lumps as (
    select (l.to_at at time zone tz.name)::date as day, sum(l.km) as km
    from public.get_car_refuel_legs(p_car_id) l
    cross join tz
    where not (l.tracked and l.driving_seconds > 0)
    group by 1
  )
  select
    g.day::date,
    round(greatest(coalesce(d.km, 0) - coalesce(lu.km, 0), 0), 1)
  from bounds b
  cross join lateral generate_series(b.since, b.today, interval '1 day') as g(day)
  left join daily d on d.day = g.day::date
  left join lumps lu on lu.day = g.day::date
  order by 1;
$$;

revoke all on function public.get_car_distance_driven_per_day(uuid, timestamptz) from public, anon;
grant execute on function public.get_car_distance_driven_per_day(uuid, timestamptz) to authenticated, service_role;
