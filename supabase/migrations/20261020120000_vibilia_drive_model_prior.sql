-- Vibilia drive model: useful from the first recorded drive.
--
-- 20261019120000 learned one km-per-driving-hour figure, only from tracked
-- legs, so a car whose tracking began mid-tank got its first drive-based
-- estimate two refuels later. This migration:
--
--   * converts each drive into kilometres with an assumed speed curve —
--     short drives are slow, long ones fast — and lets tracked legs scale
--     that curve (speed_factor) instead of replacing it
--   * starts every tracked car on the curve as given (factor 1, weighted like
--     3 hours of driving), so the model is ready at the first recorded drive
--   * estimates the tank in which tracking began by the calendar up to the
--     first drive, and by driving after it
--
-- A leg that tracking covered only partly still teaches the model nothing:
-- its odometer distance includes driving nobody timed. That rule is
-- get_car_refuel_legs.tracked and is unchanged.
-- Reasoning: .agents/brain/decisions/2026-10-20-assumed-speed-until-learned.md

-- ---------------------------------------------------------------------------
-- The assumed speed curve
-- ---------------------------------------------------------------------------

-- Kilometres of one drive of the given length, on this curve of speed over
-- distance:
--
--     up to 10 km   30 km/h
--     10 → 20 km    rising linearly to 50 km/h
--     20 → 100 km   rising linearly to 100 km/h
--     beyond        100 km/h
--
-- A drive records time, not distance, so this solves d = speed(d) × hours.
-- Each segment's solution is monotonic and they meet at 20 min (10 km),
-- 24 min (20 km) and 60 min (100 km).
-- The Android and web apps carry copies for a single drive (DriveCurve.kt,
-- assumedDriveKm in data.ts); this is the original.
create or replace function public.vibilia_assumed_drive_km(p_hours numeric)
returns numeric
language sql
immutable
set search_path = public
as $$
  select case
    when p_hours is null or p_hours <= 0 then 0
    when p_hours * 30 <= 10 then 30 * p_hours
    when p_hours * 50 <= 20 then 10 * p_hours / (1 - 2 * p_hours)
    when p_hours <= 1 then 37.5 * p_hours / (1 - 0.625 * p_hours)
    else 100 * p_hours
  end;
$$;

-- ---------------------------------------------------------------------------
-- The model
-- ---------------------------------------------------------------------------

-- What a car does per hour of driving. As in 20261019120000 for the samples
-- (tracked legs within the speed bounds, age-weighted with a 180-day
-- half-life), but what is learned is now:
--
--   speed_factor      the leg's odometer km over what the curve gives for its
--                     drives, as a ratio of weighted sums, blended with 1 (the
--                     curve as given) weighted like 3 hours of driving:
--                     (learned × H + 1 × 3) / (H + 3), H the real, unweighted
--                     hours of tracked legs. Unweighted, so old legs do not
--                     hand the model back to the assumption.
--   liters_per_100km  as before; the car's last year when no tracked leg
--                     has fuel.
--   km_per_hour       for display and for clients built before the curve:
--                     the factor times the curve's average over the last 90
--                     days of drives (50 km/h without any).
--   liters_per_hour   km_per_hour × liters_per_100km.
--
-- sample_count = 0 means the factor is assumed. A car with no drives at all
-- still gets nulls: nothing about it is tracked.
drop function if exists public.get_car_drive_model(uuid, timestamptz);

create function public.get_car_drive_model(
  p_car_id uuid,
  p_now timestamptz default now()
)
returns table (
  tracking_since timestamptz,
  last_drive_at timestamptz,
  sample_count integer,
  sample_hours numeric,
  sample_km numeric,
  km_per_hour numeric,
  liters_per_hour numeric,
  liters_per_100km numeric,
  speed_factor numeric
)
language sql
stable
set search_path = public
as $$
  with prior as (
    select 1::numeric as factor, 3::numeric as hours
  ),
  periods as (
    select * from public.get_car_drive_periods(p_car_id)
  ),
  legs as (
    select
      l.*,
      l.driving_seconds / 3600.0 as hours,
      coalesce((
        select sum(public.vibilia_assumed_drive_km(
          extract(epoch from least(p.ended_at, l.to_at) - greatest(p.started_at, l.from_at)) / 3600.0
        ))
        from periods p
        where p.ended_at > l.from_at
          and p.started_at < l.to_at
      ), 0) as curve_km
    from public.get_car_refuel_legs(p_car_id) l
  ),
  samples as (
    select
      l.*,
      power(0.5, greatest(0, extract(epoch from p_now - l.to_at)) / 86400.0 / 180.0) as weight
    from legs l
    where l.tracked
      and l.hours > 0
      and l.km / l.hours between 5 and 150
  ),
  fit as (
    select
      count(*)::int as sample_count,
      coalesce(sum(s.hours), 0) as sample_hours,
      coalesce(sum(s.km), 0) as sample_km,
      sum(s.weight * s.km) / nullif(sum(s.weight * s.curve_km), 0) as speed_factor,
      sum(s.weight * s.fuel_l) filter (where s.fuel_l is not null)
        / nullif(sum(s.weight * s.km) filter (where s.fuel_l is not null), 0) * 100 as liters_per_100km
    from samples s
  ),
  overall as (
    select sum(l.fuel_l) / nullif(sum(l.km), 0) * 100 as liters_per_100km
    from legs l
    where l.fuel_l is not null
      and l.to_at > p_now - interval '365 days'
  ),
  recent as (
    select
      sum(public.vibilia_assumed_drive_km(extract(epoch from p.ended_at - p.started_at) / 3600.0))
        / nullif(sum(extract(epoch from p.ended_at - p.started_at) / 3600.0), 0) as curve_km_per_hour
    from periods p
    where p.ended_at > p_now - interval '90 days'
      and p.started_at < p_now
  ),
  tracking as (
    select min(i.started_at) as since, max(i.ended_at) as last_drive_at
    from public.vibilia_drive_intervals i
    where i.car_id = p_car_id
  ),
  blended as (
    select
      f.sample_count,
      f.sample_hours,
      f.sample_km,
      case when t.since is not null then
        (coalesce(f.speed_factor, 0) * f.sample_hours + p.factor * p.hours)
          / (f.sample_hours + p.hours)
      end as speed_factor,
      coalesce(r.curve_km_per_hour, 50) as curve_km_per_hour,
      coalesce(f.liters_per_100km, o.liters_per_100km) as liters_per_100km
    from fit f
    cross join prior p
    cross join overall o
    cross join recent r
    cross join tracking t
  )
  select
    t.since,
    t.last_drive_at,
    b.sample_count,
    b.sample_hours,
    b.sample_km,
    b.speed_factor * b.curve_km_per_hour,
    b.speed_factor * b.curve_km_per_hour * b.liters_per_100km / 100,
    b.liters_per_100km,
    b.speed_factor
  from blended b
  cross join tracking t
  where public.user_can_access_car(p_car_id, auth.uid());
$$;

-- ---------------------------------------------------------------------------
-- Distance and fuel per day
-- ---------------------------------------------------------------------------

-- As in 20261019120000, except that the open leg since the last refuel is
-- converted drive by drive: each drive's curve km times the speed factor,
-- shared between the days it crossed in proportion to time. Litres follow at
-- the model's litres per 100 km.
create or replace function public.get_car_daily_distance(
  p_car_id uuid,
  p_since timestamptz default '-infinity',
  p_until timestamptz default 'infinity'
)
returns table (
  day date,
  km numeric,
  liters numeric,
  estimated_km numeric,
  estimated_liters numeric,
  driving_seconds numeric
)
language sql
stable
set search_path = public
as $$
  with tz as (
    select public.nexus_time_zone() as name
  ),
  legs as (
    select * from public.get_car_refuel_legs(p_car_id)
  ),
  last_refuel as (
    select max(r.fueled_at) as at
    from public.refuel_events r
    where r.car_id = p_car_id
      and public.user_can_access_car(p_car_id, auth.uid())
  ),
  model as (
    select * from public.get_car_drive_model(p_car_id)
  ),
  periods as (
    select * from public.get_car_drive_periods(p_car_id)
  ),
  -- Driving time inside each window that gets spread: tracked legs with any
  -- driving, plus the open leg after the last refuel (refuel_id null).
  windows as (
    select l.refuel_id, l.from_at, l.to_at, l.km, l.fuel_l, l.driving_seconds
    from legs l
    where l.tracked and l.driving_seconds > 0
    union all
    select null, lr.at, 'infinity'::timestamptz, null, null, null
    from last_refuel lr
    where lr.at is not null
  ),
  slices as (
    select
      w.refuel_id,
      w.km,
      w.fuel_l,
      w.driving_seconds as window_seconds,
      extract(epoch from c.e - c.s)::numeric as drive_seconds,
      g.day::date as day,
      extract(epoch from
        least(c.e, ((g.day::date + 1)::timestamp at time zone tz.name))
        - greatest(c.s, (g.day::date::timestamp at time zone tz.name))
      )::numeric as seconds
    from windows w
    join periods p
      on p.ended_at > w.from_at
     and p.started_at < w.to_at
    cross join tz
    cross join lateral (
      select greatest(p.started_at, w.from_at) as s, least(p.ended_at, w.to_at) as e
    ) c
    cross join lateral generate_series(
      (c.s at time zone tz.name)::date,
      (c.e at time zone tz.name)::date,
      interval '1 day'
    ) as g(day)
  ),
  contributions as (
    -- tracked legs, spread by driving time
    select
      s.day,
      s.km * s.seconds / s.window_seconds as km,
      s.fuel_l * s.seconds / s.window_seconds as liters,
      0::numeric as estimated_km,
      0::numeric as estimated_liters,
      s.seconds
    from slices s
    where s.refuel_id is not null
      and s.seconds > 0
    union all
    -- the open leg, drive by drive on the speed curve
    select
      s.day,
      0,
      0,
      coalesce(e.km, 0),
      coalesce(e.km * m.liters_per_100km / 100, 0),
      s.seconds
    from slices s
    cross join model m
    cross join lateral (
      select m.speed_factor
        * public.vibilia_assumed_drive_km(s.drive_seconds / 3600.0)
        * s.seconds / s.drive_seconds as km
    ) e
    where s.refuel_id is null
      and s.seconds > 0
    union all
    -- every other leg, on the day of the refuel that closed it
    select
      (l.to_at at time zone tz.name)::date,
      l.km,
      l.fuel_l,
      0,
      0,
      0
    from legs l
    cross join tz
    where not (l.tracked and l.driving_seconds > 0)
  )
  select
    c.day,
    round(sum(c.km), 3),
    round(sum(c.liters), 3),
    round(sum(c.estimated_km), 3),
    round(sum(c.estimated_liters), 3),
    round(sum(c.seconds), 0)
  from contributions c
  cross join tz
  where c.day >= (p_since at time zone tz.name)::date
    and c.day <= (p_until at time zone tz.name)::date
  group by c.day
  order by c.day;
$$;

-- ---------------------------------------------------------------------------
-- Fuel level estimate
-- ---------------------------------------------------------------------------

-- As in 20261019120000, except:
--
--   * driving since the last refuel is converted drive by drive on the speed
--     curve (times the speed factor), and to litres at litres per 100 km
--   * the tank in which tracking began uses the drive method too: litres per
--     day × the days up to the first recorded drive (calendar_liters), then
--     driving. Its estimated_km converts the calendar part at litres per
--     100 km. Such a tank needs the calendar figure; without one there is no
--     estimate, as before.
--
-- New columns, appended so older clients keep reading the rest:
--   driving_since       where driving_hours start counting: the last refuel,
--                       or the first recorded drive if that came later
--   calendar_liters     the calendar part above; 0 for a fully tracked tank
--   model_sample_count  tracked legs behind the model; 0 = assumed curve
drop function if exists public.get_car_fuel_level_estimate(uuid, timestamptz);

create function public.get_car_fuel_level_estimate(
  p_car_id uuid,
  p_now timestamptz default now()
)
returns table (
  method text,
  last_refuel_at timestamptz,
  last_mileage integer,
  tank_capacity numeric,
  starting_liters numeric,
  consumed_liters numeric,
  remaining_liters numeric,
  fill_ratio numeric,
  driving_hours numeric,
  estimated_km numeric,
  estimated_mileage numeric,
  km_per_hour numeric,
  liters_per_hour numeric,
  liters_per_day numeric,
  driving_since timestamptz,
  calendar_liters numeric,
  model_sample_count integer
)
language sql
stable
set search_path = public
as $$
  with car as (
    select c.tank_capacity::numeric as tank_capacity
    from public.cars c
    where c.id = p_car_id
      and c.tank_capacity > 0
      and public.user_can_access_car(p_car_id, auth.uid())
  ),
  events as (
    select
      r.id,
      r.fueled_at,
      r.mileage,
      r.liters::numeric as liters,
      r.fuel_level_after::numeric as fuel_level_after,
      r.missed_previous_refuel,
      r.created_at
    from public.refuel_events r
    where r.car_id = p_car_id
      and public.user_can_access_car(p_car_id, auth.uid())
  ),
  latest as (
    select e.*
    from events e
    order by e.fueled_at desc, e.created_at desc
    limit 1
  ),
  -- The calendar method, as fetchFuelLevelEstimateForCar computed it.
  daily_samples as (
    select
      (o.prev_level + o.liters - o.fuel_level_after)
        / (extract(epoch from o.fueled_at - o.prev_at) / 86400.0) as liters_per_day
    from (
      select
        e.*,
        lag(e.fueled_at) over w as prev_at,
        lag(e.fuel_level_after) over w as prev_level
      from events e
      window w as (order by e.fueled_at, e.created_at)
    ) o
    where o.prev_at is not null
      and not o.missed_previous_refuel
      and o.fueled_at >= p_now - interval '365 days'
      and o.fueled_at > o.prev_at
      and o.prev_level is not null
      and o.fuel_level_after is not null
      and o.prev_level + o.liters - o.fuel_level_after >= 0
  ),
  calendar as (
    select avg(d.liters_per_day) as liters_per_day
    from daily_samples d
  ),
  model as (
    select * from public.get_car_drive_model(p_car_id, p_now)
  ),
  window_start as (
    select greatest(l.fueled_at, m.tracking_since) as at
    from latest l
    cross join model m
  ),
  driving as (
    select
      coalesce(sum(c.hours), 0) as hours,
      coalesce(sum(public.vibilia_assumed_drive_km(c.hours)), 0) as curve_km
    from window_start w
    join public.get_car_drive_periods(p_car_id, w.at, p_now) p on true
    cross join lateral (
      select extract(epoch from least(p.ended_at, p_now) - greatest(p.started_at, w.at)) / 3600.0 as hours
    ) c
  ),
  chosen as (
    select
      l.fueled_at,
      l.mileage,
      c.tank_capacity,
      least(c.tank_capacity, l.fuel_level_after) as starting_liters,
      m.km_per_hour,
      m.liters_per_hour,
      m.liters_per_100km,
      m.sample_count,
      cal.liters_per_day,
      d.hours,
      m.speed_factor * d.curve_km as driven_km,
      w.at as driving_since,
      greatest(0, extract(epoch from least(w.at, p_now) - l.fueled_at) / 86400.0) as calendar_days,
      case
        when m.liters_per_hour is not null and m.tracking_since <= l.fueled_at then 'drive'
        when m.liters_per_hour is not null and cal.liters_per_day is not null then 'drive'
        when cal.liters_per_day is not null then 'time'
      end as method
    from latest l
    cross join car c
    cross join calendar cal
    cross join model m
    cross join driving d
    cross join window_start w
    where l.fuel_level_after is not null
      and l.fuel_level_after >= 0
  ),
  consumed as (
    select
      ch.*,
      case ch.method
        when 'drive' then coalesce(ch.calendar_days * ch.liters_per_day, 0)
      end as calendar_liters,
      case ch.method
        when 'drive' then coalesce(ch.calendar_days * ch.liters_per_day, 0)
          + ch.driven_km * ch.liters_per_100km / 100
        when 'time' then greatest(0, extract(epoch from p_now - ch.fueled_at) / 86400.0) * ch.liters_per_day
      end as consumed_liters
    from chosen ch
    where ch.method is not null
  ),
  distance as (
    select
      x.*,
      case when x.method = 'drive' then
        x.driven_km + coalesce(x.calendar_liters / nullif(x.liters_per_100km, 0) * 100, 0)
      end as estimated_km
    from consumed x
  )
  select
    x.method,
    x.fueled_at,
    x.mileage,
    x.tank_capacity,
    x.starting_liters,
    x.consumed_liters,
    greatest(0, x.starting_liters - x.consumed_liters),
    greatest(0, least(1, (x.starting_liters - x.consumed_liters) / x.tank_capacity)),
    case when x.method = 'drive' then x.hours end,
    x.estimated_km,
    x.mileage + x.estimated_km,
    x.km_per_hour,
    x.liters_per_hour,
    x.liters_per_day,
    case when x.method = 'drive' then x.driving_since end,
    x.calendar_liters,
    x.sample_count
  from distance x;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

-- As in 20261019120000: the functions run as the caller and check
-- user_can_access_car themselves. The curve reads nothing.
revoke all on function public.vibilia_assumed_drive_km(numeric) from public, anon;
revoke all on function public.get_car_drive_model(uuid, timestamptz) from public, anon;
revoke all on function public.get_car_fuel_level_estimate(uuid, timestamptz) from public, anon;

grant execute on function public.vibilia_assumed_drive_km(numeric) to authenticated, service_role;
grant execute on function public.get_car_drive_model(uuid, timestamptz) to authenticated, service_role;
grant execute on function public.get_car_fuel_level_estimate(uuid, timestamptz) to authenticated, service_role;
