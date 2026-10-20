-- Vibilia drive model: useful from the first recorded drive.
--
-- 20261019120000 learned km per driving hour only from tracked legs, so a car
-- whose tracking began mid-tank got its first drive-based estimate two refuels
-- later. Two changes shorten that:
--
--   * get_car_drive_model starts from an assumed 50 km/h, weighted like 3 hours
--     of driving, and lets tracked legs outweigh it as they arrive. Litres per
--     hour follow from km per hour and the car's litres per 100 km, which the
--     refuel history already knows.
--   * get_car_fuel_level_estimate handles the tank in which tracking started:
--     the calendar method up to the first recorded drive, driving time after.
--
-- A leg that tracking covered only partly still teaches the model nothing:
-- its odometer distance includes driving nobody timed. That rule is
-- get_car_refuel_legs.tracked and is unchanged.
-- Reasoning: .agents/brain/decisions/2026-10-20-assumed-speed-until-learned.md

-- ---------------------------------------------------------------------------
-- The model
-- ---------------------------------------------------------------------------

-- As in 20261019120000, except:
--
--   km_per_hour      the learned speed blended with an assumed 50 km/h worth
--                    3 hours of driving: (learned × H + 50 × 3) / (H + 3),
--                    where H is the real, unweighted hours of tracked legs.
--                    50 km/h with no tracked leg; close to the learned speed
--                    after a tank or two. The age weighting still decides the
--                    learned speed, but not how much evidence there is, so
--                    old legs do not hand the model back to the assumption.
--   liters_per_hour  km_per_hour × liters_per_100km, so the two agree.
--
-- sample_count = 0 means the speed is assumed. A car with no drives at all
-- still gets nulls: nothing about it is tracked.
create or replace function public.get_car_drive_model(
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
  liters_per_100km numeric
)
language sql
stable
set search_path = public
as $$
  with prior as (
    select 50::numeric as km_per_hour, 3::numeric as hours
  ),
  legs as (
    select l.*, l.driving_seconds / 3600.0 as hours
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
      sum(s.weight * s.km) / nullif(sum(s.weight * s.hours), 0) as km_per_hour,
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
  tracking as (
    select min(i.started_at) as since, max(i.ended_at) as last_drive_at
    from public.vibilia_drive_intervals i
    where i.car_id = p_car_id
  ),
  blended as (
    select
      f.*,
      case when t.since is not null then
        (coalesce(f.km_per_hour, 0) * f.sample_hours + p.km_per_hour * p.hours)
          / (f.sample_hours + p.hours)
      end as blended_km_per_hour,
      coalesce(f.liters_per_100km, o.liters_per_100km) as blended_liters_per_100km
    from fit f
    cross join prior p
    cross join overall o
    cross join tracking t
  )
  select
    t.since,
    t.last_drive_at,
    b.sample_count,
    b.sample_hours,
    b.sample_km,
    b.blended_km_per_hour,
    b.blended_km_per_hour * b.blended_liters_per_100km / 100,
    b.blended_liters_per_100km
  from blended b
  cross join tracking t
  where public.user_can_access_car(p_car_id, auth.uid());
$$;

-- ---------------------------------------------------------------------------
-- Fuel level estimate
-- ---------------------------------------------------------------------------

-- As in 20261019120000, with the drive method extended to the tank in which
-- tracking began. Its consumption is then two parts:
--
--   calendar_liters  litres per day × the days from the last refuel to the
--                    first recorded drive
--   driving          driving hours since then × litres per hour
--
-- and estimated_km converts the calendar part with litres per 100 km. Such a
-- tank needs the calendar figure; without one there is no estimate, as before.
--
-- New columns, appended so older clients keep reading the rest:
--   driving_since       where driving_hours start counting: the last refuel,
--                       or the first recorded drive if that came later
--   calendar_liters     the calendar part above; 0 for a fully tracked tank
--   model_sample_count  tracked legs behind the model; 0 = assumed speed
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
    select coalesce(sum(extract(epoch from least(p.ended_at, p_now) - greatest(p.started_at, w.at))), 0) / 3600.0 as hours
    from window_start w
    join public.get_car_drive_periods(p_car_id, w.at, p_now) p on true
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
        when 'drive' then coalesce(ch.calendar_days * ch.liters_per_day, 0) + ch.hours * ch.liters_per_hour
        when 'time' then greatest(0, extract(epoch from p_now - ch.fueled_at) / 86400.0) * ch.liters_per_day
      end as consumed_liters
    from chosen ch
    where ch.method is not null
  ),
  distance as (
    select
      x.*,
      case when x.method = 'drive' then
        x.hours * x.km_per_hour
          + coalesce(x.calendar_liters / nullif(x.liters_per_100km, 0) * 100, 0)
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

revoke all on function public.get_car_fuel_level_estimate(uuid, timestamptz) from public, anon;
grant execute on function public.get_car_fuel_level_estimate(uuid, timestamptz) to authenticated, service_role;
