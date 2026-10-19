-- Vibilia drive tracking.
--
-- The Android app notices when a phone connects to a car's Bluetooth and
-- reports how long it stayed connected. Connected time is taken to be driving
-- time. Comparing it with the odometer between refuels teaches each car a
-- simple model (km per driving hour, litres per driving hour), which this
-- migration uses for three things:
--
--   * a fuel level estimate driven by actual driving rather than calendar days
--   * distance and fuel attributed to the days the car was driven, instead of
--     piling a whole tank onto the day of the next refuel
--   * the yearly statistics, which inherit that attribution
--
-- Everything degrades to the previous behaviour for a car with no drives:
-- distance falls on the refuel day, and the estimate counts calendar days.
-- Reasoning: .agents/brain/decisions/2026-10-19-bluetooth-drive-tracking.md

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

-- Which Bluetooth device *is* the car. Stored per car rather than per phone so
-- every member's phone recognises it once they turn tracking on. The address
-- is the car's own adapter address, the same for every phone paired with it.
create table if not exists public.vibilia_car_bluetooth_devices (
  car_id uuid not null references public.cars(id) on update cascade on delete cascade,
  address text not null,
  name text,
  created_by uuid default auth.uid() references public.profiles(id) on update cascade on delete set null,
  created_at timestamptz not null default now(),
  primary key (car_id, address),
  constraint vibilia_car_bluetooth_devices_address_format
    check (address ~ '^[0-9A-F]{2}(:[0-9A-F]{2}){5}$')
);

comment on table public.vibilia_car_bluetooth_devices is
  'Bluetooth adapters that identify a car. Linked from the Android app; read-only on the web.';

-- One row per connection, per phone. Several phones in one car report
-- overlapping intervals; nothing here deduplicates them on write. Readers merge
-- them into periods (get_car_drive_periods), so each minute counts once however
-- many phones saw it.
create table if not exists public.vibilia_drive_intervals (
  -- Generated on the phone, so retrying an upload is a no-op.
  id uuid primary key,
  car_id uuid not null references public.cars(id) on update cascade on delete cascade,
  user_id uuid default auth.uid() references public.profiles(id) on update cascade on delete set null,
  device_id text not null,
  started_at timestamptz not null,
  ended_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint vibilia_drive_intervals_positive check (ended_at > started_at),
  -- Nobody drives for 18 hours. A longer interval is a missed disconnect.
  constraint vibilia_drive_intervals_bounded check (ended_at - started_at <= interval '18 hours'),
  constraint vibilia_drive_intervals_device_id_length check (length(device_id) between 1 and 64)
);

comment on table public.vibilia_drive_intervals is
  'Raw Bluetooth connection intervals reported per phone. Overlaps between phones are merged on read.';

create index if not exists vibilia_drive_intervals_car_started_idx
  on public.vibilia_drive_intervals (car_id, started_at);

alter table public.vibilia_car_bluetooth_devices enable row level security;
alter table public.vibilia_drive_intervals enable row level security;

-- Same access model as every other per-car table: anyone the car is shared with.
drop policy if exists "Members read car bluetooth devices" on public.vibilia_car_bluetooth_devices;
create policy "Members read car bluetooth devices"
  on public.vibilia_car_bluetooth_devices for select to authenticated
  using (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Members link car bluetooth devices" on public.vibilia_car_bluetooth_devices;
create policy "Members link car bluetooth devices"
  on public.vibilia_car_bluetooth_devices for insert to authenticated
  with check (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Members rename car bluetooth devices" on public.vibilia_car_bluetooth_devices;
create policy "Members rename car bluetooth devices"
  on public.vibilia_car_bluetooth_devices for update to authenticated
  using (public.user_can_access_car(car_id, auth.uid()))
  with check (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Members unlink car bluetooth devices" on public.vibilia_car_bluetooth_devices;
create policy "Members unlink car bluetooth devices"
  on public.vibilia_car_bluetooth_devices for delete to authenticated
  using (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Members read drive intervals" on public.vibilia_drive_intervals;
create policy "Members read drive intervals"
  on public.vibilia_drive_intervals for select to authenticated
  using (public.user_can_access_car(car_id, auth.uid()));

-- A phone reports only as its own user, and only for cars that user may see.
drop policy if exists "Members report own drive intervals" on public.vibilia_drive_intervals;
create policy "Members report own drive intervals"
  on public.vibilia_drive_intervals for insert to authenticated
  with check (
    user_id = auth.uid()
    and public.user_can_access_car(car_id, auth.uid())
  );

-- Any member may remove a bogus drive, like any other entry in the car's log.
-- There is no update policy: intervals are immutable once reported.
drop policy if exists "Members delete drive intervals" on public.vibilia_drive_intervals;
create policy "Members delete drive intervals"
  on public.vibilia_drive_intervals for delete to authenticated
  using (public.user_can_access_car(car_id, auth.uid()));

grant select, insert, update, delete on public.vibilia_car_bluetooth_devices to authenticated, service_role;
grant select, insert, delete on public.vibilia_drive_intervals to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Merged driving periods
-- ---------------------------------------------------------------------------

-- Merges every phone's intervals for one car into non-overlapping periods
-- (classic gaps-and-islands: a row starts a new island when it begins after
-- every earlier row has ended). Two phones reporting 10:00–10:30 and
-- 10:01–10:29 yield one period, 10:00–10:30. Periods are returned unclipped;
-- callers clip them to their own window.
create or replace function public.get_car_drive_periods(
  p_car_id uuid,
  p_since timestamptz default '-infinity',
  p_until timestamptz default 'infinity'
)
returns table (
  started_at timestamptz,
  ended_at timestamptz,
  reporter_count integer
)
language sql
stable
set search_path = public
as $$
  with raw as (
    select i.started_at, i.ended_at, i.device_id
    from public.vibilia_drive_intervals i
    where i.car_id = p_car_id
      and public.user_can_access_car(p_car_id, auth.uid())
  ),
  marked as (
    select
      r.*,
      case
        when r.started_at <= max(r.ended_at) over (
          order by r.started_at, r.ended_at
          rows between unbounded preceding and 1 preceding
        ) then 0
        else 1
      end as starts_island
    from raw r
  ),
  islands as (
    select
      m.*,
      sum(m.starts_island) over (
        order by m.started_at, m.ended_at
        rows unbounded preceding
      ) as island
    from marked m
  ),
  periods as (
    select
      min(i.started_at) as started_at,
      max(i.ended_at) as ended_at,
      count(distinct i.device_id)::int as reporter_count
    from islands i
    group by i.island
  )
  select p.started_at, p.ended_at, p.reporter_count
  from periods p
  where p.ended_at > p_since
    and p.started_at < p_until
  order by p.started_at;
$$;

-- ---------------------------------------------------------------------------
-- Refuel legs
-- ---------------------------------------------------------------------------

-- One row per pair of consecutive refuels with a measurable distance: the
-- "leg" a tank of fuel was driven over. Uses exactly the rules of
-- get_car_refuel_statistics — distance from the odometer gap, fuel from the
-- tank-level correction, no fuel across a missed refuel, non-positive fuel
-- discarded — so distance and fuel totals agree with the statistics page.
--
-- `tracked` means drive tracking was already running when the leg began, so
-- its driving time is complete. Only tracked legs teach the model or get their
-- distance spread over the days that were driven.
create or replace function public.get_car_refuel_legs(p_car_id uuid)
returns table (
  refuel_id bigint,
  from_at timestamptz,
  to_at timestamptz,
  km numeric,
  fuel_l numeric,
  tracked boolean,
  driving_seconds numeric
)
language sql
stable
set search_path = public
as $$
  with events as (
    select
      r.id,
      r.fueled_at,
      r.mileage::numeric as mileage,
      r.liters::numeric as liters,
      r.fuel_level_after::numeric as fuel_level_after,
      r.missed_previous_refuel
    from public.refuel_events r
    where r.car_id = p_car_id
      and public.user_can_access_car(p_car_id, auth.uid())
  ),
  ordered as (
    select
      e.*,
      lag(e.fueled_at) over w as prev_at,
      lag(e.mileage) over w as prev_mileage,
      lag(e.fuel_level_after) over w as prev_level
    from events e
    window w as (order by e.fueled_at, e.id)
  ),
  legs as (
    select
      o.id,
      o.prev_at,
      o.fueled_at,
      o.mileage - o.prev_mileage as km,
      case
        when o.missed_previous_refuel then null
        when o.prev_level is not null and o.fuel_level_after is not null
          then o.liters + o.prev_level - o.fuel_level_after
        else o.liters
      end as fuel_raw
    from ordered o
    where o.prev_mileage is not null
      and o.mileage > o.prev_mileage
  ),
  tracking as (
    select min(i.started_at) as since
    from public.vibilia_drive_intervals i
    where i.car_id = p_car_id
  ),
  periods as (
    select * from public.get_car_drive_periods(p_car_id)
  )
  select
    l.id,
    l.prev_at,
    l.fueled_at,
    l.km,
    case when l.fuel_raw > 0 then l.fuel_raw else null end,
    coalesce(t.since <= l.prev_at, false),
    coalesce((
      select sum(extract(epoch from least(p.ended_at, l.fueled_at) - greatest(p.started_at, l.prev_at)))
      from periods p
      where p.ended_at > l.prev_at
        and p.started_at < l.fueled_at
    ), 0)::numeric
  from legs l
  cross join tracking t
  order by l.fueled_at;
$$;

-- ---------------------------------------------------------------------------
-- The model
-- ---------------------------------------------------------------------------

-- What a car does per hour of driving, learned from tracked legs: kilometres
-- per hour and litres per hour, each a ratio of sums (total km over total
-- hours), so a long leg weighs more than a short one. Legs are also weighted
-- by age with a 180-day half-life, so the model follows a change of commute
-- or season instead of averaging it away.
--
-- A leg whose average speed is implausible (below 5 or above 150 km/h) is left
-- out: that is a phone that stayed connected while parked, or a trip made
-- without a phone in the car, not a fact about the car.
--
-- Returns one row for a car the caller may see, with nulls until the first
-- tracked leg is complete. litres per 100 km falls back to the car's overall
-- consumption over the last year, so a fuel estimate is possible as soon as
-- km per hour is known.
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
  with legs as (
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
      sum(s.hours) as sample_hours,
      sum(s.km) as sample_km,
      sum(s.weight * s.km) / nullif(sum(s.weight * s.hours), 0) as km_per_hour,
      sum(s.weight * s.fuel_l) filter (where s.fuel_l is not null)
        / nullif(sum(s.weight * s.hours) filter (where s.fuel_l is not null), 0) as liters_per_hour,
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
  )
  select
    t.since,
    t.last_drive_at,
    f.sample_count,
    coalesce(f.sample_hours, 0),
    coalesce(f.sample_km, 0),
    f.km_per_hour,
    coalesce(f.liters_per_hour, f.km_per_hour * o.liters_per_100km / 100),
    coalesce(f.liters_per_100km, o.liters_per_100km)
  from fit f
  cross join overall o
  cross join tracking t
  where public.user_can_access_car(p_car_id, auth.uid());
$$;

-- ---------------------------------------------------------------------------
-- Distance and fuel per day
-- ---------------------------------------------------------------------------

-- Attributes every refuel leg to calendar days (Europe/Berlin, as everywhere
-- else in reporting — public.nexus_time_zone()):
--
--   * a tracked leg with driving time spreads its exact km and litres over the
--     days it was driven, in proportion to each day's driving time
--   * any other leg puts everything on the day of the refuel that closed it,
--     which is what the statistics did before drive tracking existed
--
-- The open leg since the last refuel has no odometer reading yet. Its driving
-- time is converted with the model and reported separately as estimated_km
-- and estimated_liters, so a chart can show this month before the next refuel
-- without mixing guesses into measurements. Once the next refuel is logged the
-- estimate is replaced by the exact, redistributed figures.
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
    -- the open leg, converted with the model
    select
      s.day,
      0,
      0,
      coalesce(s.seconds / 3600.0 * m.km_per_hour, 0),
      coalesce(s.seconds / 3600.0 * m.liters_per_hour, 0),
      s.seconds
    from slices s
    cross join model m
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

-- The monthly view of get_car_daily_distance. Replaces get_car_km_per_month,
-- which attributes a whole leg to the month of the refuel that closed it; that
-- function stays for clients built before this one.
create or replace function public.get_car_distance_per_month(
  p_car_id uuid,
  p_since timestamptz default '-infinity',
  p_until timestamptz default now()
)
returns table (
  month_key date,
  km_driven numeric,
  liters_used numeric,
  estimated_km numeric,
  estimated_liters numeric,
  driving_hours numeric
)
language sql
stable
set search_path = public
as $$
  select
    date_trunc('month', d.day)::date,
    round(sum(d.km), 1),
    round(sum(d.liters), 2),
    round(sum(d.estimated_km), 1),
    round(sum(d.estimated_liters), 2),
    round(sum(d.driving_seconds) / 3600.0, 2)
  from public.get_car_daily_distance(p_car_id, p_since, p_until) d
  group by 1
  order by 1;
$$;

-- ---------------------------------------------------------------------------
-- Fuel level estimate
-- ---------------------------------------------------------------------------

-- How much fuel is in the tank now. Two methods, best first:
--
--   drive — driving time since the last refuel times the model's litres per
--           hour. Used once the model knows litres per hour and tracking was
--           running at the last refuel.
--   time  — the calendar method the web app has always used: the mean litres
--           per day over the last year's refuel intervals, times the days since
--           the last refuel. Kept as the fallback for untracked cars.
--
-- Returns no row when neither method has enough data.
create or replace function public.get_car_fuel_level_estimate(
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
  liters_per_day numeric
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
  driving as (
    select coalesce(sum(extract(epoch from least(p.ended_at, p_now) - greatest(p.started_at, l.fueled_at))), 0) / 3600.0 as hours
    from latest l
    join public.get_car_drive_periods(p_car_id, l.fueled_at, p_now) p on true
  ),
  chosen as (
    select
      l.fueled_at,
      l.mileage,
      c.tank_capacity,
      least(c.tank_capacity, l.fuel_level_after) as starting_liters,
      m.km_per_hour,
      m.liters_per_hour,
      cal.liters_per_day,
      d.hours,
      case
        when m.liters_per_hour is not null and m.tracking_since <= l.fueled_at then 'drive'
        when cal.liters_per_day is not null then 'time'
      end as method
    from latest l
    cross join car c
    cross join calendar cal
    cross join model m
    cross join driving d
    where l.fuel_level_after is not null
      and l.fuel_level_after >= 0
  ),
  consumed as (
    select
      ch.*,
      case ch.method
        when 'drive' then ch.hours * ch.liters_per_hour
        when 'time' then greatest(0, extract(epoch from p_now - ch.fueled_at) / 86400.0) * ch.liters_per_day
      end as consumed_liters
    from chosen ch
    where ch.method is not null
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
    case when x.method = 'drive' then x.hours * x.km_per_hour end,
    case when x.method = 'drive' then x.mileage + x.hours * x.km_per_hour end,
    x.km_per_hour,
    x.liters_per_hour,
    x.liters_per_day
  from consumed x;
$$;

-- ---------------------------------------------------------------------------
-- Statistics: this year / last year follow the day the driving happened
-- ---------------------------------------------------------------------------

-- Unchanged from 20260412214000 except for four columns: driven_this_year_km,
-- driven_last_year_km, fuel_used_this_year_l and fuel_used_last_year_l now sum
-- get_car_daily_distance by Europe/Berlin calendar year, so a December drive
-- refuelled in January counts for December's year. Without drive tracking that
-- function puts each leg on its refuel day, so untracked cars get the same
-- numbers as before. Costs stay on the day the money was spent.
create or replace function public.get_car_refuel_statistics(
  p_car_id uuid,
  p_now timestamp with time zone default now()
)
returns table (
  latest_mileage integer,
  driven_total_km numeric,
  driven_this_year_km numeric,
  driven_last_year_km numeric,
  driven_avg_month_km numeric,
  driven_avg_day_km numeric,
  fuel_used_total_l numeric,
  fuel_used_this_year_l numeric,
  fuel_used_last_year_l numeric,
  fuel_used_avg_month_l numeric,
  fuel_used_avg_day_l numeric,
  fuel_cost_total_eur numeric,
  fuel_cost_this_year_eur numeric,
  fuel_cost_last_year_eur numeric,
  fuel_cost_avg_month_eur numeric,
  fuel_cost_avg_day_eur numeric,
  avg_consumption_l_per_100km numeric,
  min_consumption_l_per_100km numeric,
  max_consumption_l_per_100km numeric,
  avg_cost_per_km_eur numeric,
  min_cost_per_km_eur numeric,
  max_cost_per_km_eur numeric,
  expense_total_eur numeric,
  expense_this_year_eur numeric,
  expense_last_year_eur numeric,
  expense_avg_year_eur numeric
)
language sql
stable
set search_path = public
as $$
  with boundaries as (
    select
      date_trunc('year', p_now) as start_this_year,
      date_trunc('year', p_now) - interval '1 year' as start_last_year,
      date_trunc('year', p_now) + interval '1 year' as start_next_year,
      date_trunc('year', p_now at time zone public.nexus_time_zone())::date as day_this_year,
      (date_trunc('year', p_now at time zone public.nexus_time_zone()) - interval '1 year')::date as day_last_year,
      (date_trunc('year', p_now at time zone public.nexus_time_zone()) + interval '1 year')::date as day_next_year
  ),
  base_events as (
    select
      r.id,
      r.fueled_at,
      r.mileage::numeric as mileage,
      r.liters::numeric as liters,
      r.total_price::numeric as total_price,
      r.fuel_level_after::numeric as fuel_level_after,
      r.missed_previous_refuel
    from public.refuel_events r
    join public.cars c on c.id = r.car_id
    where r.car_id = p_car_id
      and (
        c.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access ca
          where ca.car_id = c.id and ca.user_id = auth.uid()
        )
      )
  ),
  base_expenses as (
    select
      e.id,
      e.expensed_at,
      e.amount::numeric as amount
    from public.car_expenses e
    join public.cars c on c.id = e.car_id
    where e.car_id = p_car_id
      and (
        c.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access ca
          where ca.car_id = c.id and ca.user_id = auth.uid()
        )
      )
  ),
  ordered_events as (
    select
      b.*,
      lead(b.mileage) over (order by b.fueled_at desc, b.id desc) as prev_mileage,
      lead(b.fuel_level_after) over (order by b.fueled_at desc, b.id desc) as prev_fuel_level
    from base_events b
  ),
  intervals as (
    select
      o.*,
      case
        when o.prev_mileage is not null and o.mileage > o.prev_mileage
          then o.mileage - o.prev_mileage
        else null
      end as km_delta,
      case
        when o.missed_previous_refuel then null
        when o.prev_mileage is null or o.mileage <= o.prev_mileage then null
        when o.prev_fuel_level is not null and o.fuel_level_after is not null
          then o.liters + o.prev_fuel_level - o.fuel_level_after
        else o.liters
      end as fuel_used_raw
    from ordered_events o
  ),
  metrics as (
    select
      i.*,
      case when i.fuel_used_raw > 0 then i.fuel_used_raw else null end as fuel_used,
      case
        when i.km_delta is not null and i.km_delta > 0 and i.fuel_used_raw > 0
          then (i.fuel_used_raw / i.km_delta) * 100
        else null
      end as consumption,
      case
        when i.km_delta is not null and i.km_delta > 0
          then i.total_price / i.km_delta
        else null
      end as cost_per_km
    from intervals i
  ),
  span as (
    select
      min(fueled_at) as min_at,
      max(fueled_at) as max_at,
      greatest(
        1,
        (
          extract(year from age(max(fueled_at), min(fueled_at)))::int * 12
          + extract(month from age(max(fueled_at), min(fueled_at)))::int
          + 1
        )
      ) as active_months,
      greatest(1, (max(fueled_at)::date - min(fueled_at)::date + 1))::numeric as active_days
    from base_events
  ),
  expense_span as (
    select
      min(expensed_at) as min_at,
      max(expensed_at) as max_at,
      greatest(
        1,
        extract(year from max(expensed_at))::int
        - extract(year from min(expensed_at))::int
        + 1
      ) as active_years
    from base_expenses
  ),
  by_year as (
    select
      coalesce(sum(d.km) filter (where d.day >= br.day_this_year and d.day < br.day_next_year), 0) as driven_this_year_km,
      coalesce(sum(d.km) filter (where d.day >= br.day_last_year and d.day < br.day_this_year), 0) as driven_last_year_km,
      coalesce(sum(d.liters) filter (where d.day >= br.day_this_year and d.day < br.day_next_year), 0) as fuel_used_this_year_l,
      coalesce(sum(d.liters) filter (where d.day >= br.day_last_year and d.day < br.day_this_year), 0) as fuel_used_last_year_l
    from boundaries br
    left join public.get_car_daily_distance(p_car_id) d on true
  ),
  fuel_agg as (
    select
      coalesce(max(b.mileage)::int, 0) as latest_mileage,
      coalesce(sum(m.km_delta), 0) as driven_total_km,
      case
        when max(s.min_at) is null then 0
        else coalesce(sum(m.km_delta), 0) / max(s.active_months)
      end as driven_avg_month_km,
      case
        when max(s.min_at) is null then 0
        else coalesce(sum(m.km_delta), 0) / max(s.active_days)
      end as driven_avg_day_km,

      coalesce(sum(m.fuel_used), 0) as fuel_used_total_l,
      case
        when max(s.min_at) is null then 0
        else coalesce(sum(m.fuel_used), 0) / max(s.active_months)
      end as fuel_used_avg_month_l,
      case
        when max(s.min_at) is null then 0
        else coalesce(sum(m.fuel_used), 0) / max(s.active_days)
      end as fuel_used_avg_day_l,

      coalesce(sum(b.total_price), 0) as fuel_cost_total_eur,
      coalesce(sum(b.total_price) filter (
        where b.fueled_at >= br.start_this_year and b.fueled_at < br.start_next_year
      ), 0) as fuel_cost_this_year_eur,
      coalesce(sum(b.total_price) filter (
        where b.fueled_at >= br.start_last_year and b.fueled_at < br.start_this_year
      ), 0) as fuel_cost_last_year_eur,
      case
        when max(s.min_at) is null then 0
        else coalesce(sum(b.total_price), 0) / max(s.active_months)
      end as fuel_cost_avg_month_eur,
      case
        when max(s.min_at) is null then 0
        else coalesce(sum(b.total_price), 0) / max(s.active_days)
      end as fuel_cost_avg_day_eur,

      coalesce(avg(m.consumption), 0) as avg_consumption_l_per_100km,
      coalesce(min(m.consumption), 0) as min_consumption_l_per_100km,
      coalesce(max(m.consumption), 0) as max_consumption_l_per_100km,

      coalesce(avg(m.cost_per_km), 0) as avg_cost_per_km_eur,
      coalesce(min(m.cost_per_km), 0) as min_cost_per_km_eur,
      coalesce(max(m.cost_per_km), 0) as max_cost_per_km_eur
    from boundaries br
    left join base_events b on true
    left join metrics m on m.id = b.id
    left join span s on true
  ),
  expense_agg as (
    select
      coalesce(sum(e.amount), 0) as expense_total_eur,
      coalesce(sum(e.amount) filter (
        where e.expensed_at >= br.start_this_year and e.expensed_at < br.start_next_year
      ), 0) as expense_this_year_eur,
      coalesce(sum(e.amount) filter (
        where e.expensed_at >= br.start_last_year and e.expensed_at < br.start_this_year
      ), 0) as expense_last_year_eur,
      case
        when max(es.min_at) is null then 0
        else coalesce(sum(e.amount), 0) / max(es.active_years)
      end as expense_avg_year_eur
    from boundaries br
    left join base_expenses e on true
    left join expense_span es on true
  )
  select
    f.latest_mileage,
    f.driven_total_km,
    y.driven_this_year_km,
    y.driven_last_year_km,
    f.driven_avg_month_km,
    f.driven_avg_day_km,
    f.fuel_used_total_l,
    y.fuel_used_this_year_l,
    y.fuel_used_last_year_l,
    f.fuel_used_avg_month_l,
    f.fuel_used_avg_day_l,
    f.fuel_cost_total_eur,
    f.fuel_cost_this_year_eur,
    f.fuel_cost_last_year_eur,
    f.fuel_cost_avg_month_eur,
    f.fuel_cost_avg_day_eur,
    f.avg_consumption_l_per_100km,
    f.min_consumption_l_per_100km,
    f.max_consumption_l_per_100km,
    f.avg_cost_per_km_eur,
    f.min_cost_per_km_eur,
    f.max_cost_per_km_eur,
    e.expense_total_eur,
    e.expense_this_year_eur,
    e.expense_last_year_eur,
    e.expense_avg_year_eur
  from fuel_agg f
  cross join by_year y
  cross join expense_agg e;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

-- Every function above runs as the caller (no SECURITY DEFINER), checks
-- user_can_access_car itself, and reads through RLS besides, so granting them
-- to authenticated hands out nothing the tables do not.
revoke all on function public.get_car_drive_periods(uuid, timestamptz, timestamptz) from public, anon;
revoke all on function public.get_car_refuel_legs(uuid) from public, anon;
revoke all on function public.get_car_drive_model(uuid, timestamptz) from public, anon;
revoke all on function public.get_car_daily_distance(uuid, timestamptz, timestamptz) from public, anon;
revoke all on function public.get_car_distance_per_month(uuid, timestamptz, timestamptz) from public, anon;
revoke all on function public.get_car_fuel_level_estimate(uuid, timestamptz) from public, anon;

grant execute on function public.get_car_drive_periods(uuid, timestamptz, timestamptz) to authenticated, service_role;
grant execute on function public.get_car_refuel_legs(uuid) to authenticated, service_role;
grant execute on function public.get_car_drive_model(uuid, timestamptz) to authenticated, service_role;
grant execute on function public.get_car_daily_distance(uuid, timestamptz, timestamptz) to authenticated, service_role;
grant execute on function public.get_car_distance_per_month(uuid, timestamptz, timestamptz) to authenticated, service_role;
grant execute on function public.get_car_fuel_level_estimate(uuid, timestamptz) to authenticated, service_role;

grant execute on function public.get_car_refuel_statistics(uuid, timestamp with time zone)
  to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Restores: carry the new user references across a profile merge
-- ---------------------------------------------------------------------------

-- Identical to 20260417235500 plus the two new tables in the merge branch.
-- (The non-merge branch updates profiles.id and cascades through the foreign
-- keys, which already covers them.)
create or replace function public.reassign_profile_user(
  source_profile_id uuid,
  target_user_id uuid,
  merge_target_profile boolean default false
)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  source_exists boolean;
  target_user_exists boolean;
  target_profile_exists boolean;
begin
  if source_profile_id is null or target_user_id is null then
    raise exception 'source_profile_id and target_user_id must be non-null';
  end if;

  if source_profile_id = target_user_id then
    return;
  end if;

  select exists(select 1 from public.profiles p where p.id = source_profile_id)
    into source_exists;
  if not source_exists then
    raise exception 'source profile % does not exist', source_profile_id;
  end if;

  select exists(select 1 from auth.users u where u.id = target_user_id)
    into target_user_exists;
  if not target_user_exists then
    raise exception 'target auth user % does not exist', target_user_id;
  end if;

  select exists(select 1 from public.profiles p where p.id = target_user_id)
    into target_profile_exists;

  if target_profile_exists then
    if not merge_target_profile then
      raise exception 'target profile % already exists (set merge_target_profile=true)', target_user_id;
    end if;

    -- Noctua ownership
    update public.sections
    set user_id = target_user_id
    where user_id = source_profile_id;

    insert into public.article_reads (user_id, article_id, read_at, created_at)
    select target_user_id, ar.article_id, ar.read_at, ar.created_at
    from public.article_reads ar
    where ar.user_id = source_profile_id
    on conflict (user_id, article_id)
    do update
      set read_at = greatest(public.article_reads.read_at, excluded.read_at),
          created_at = least(public.article_reads.created_at, excluded.created_at);

    delete from public.article_reads
    where user_id = source_profile_id;

    -- Vibilia ownership
    update public.cars
    set owner_id = target_user_id
    where owner_id = source_profile_id;

    insert into public.car_access (car_id, user_id)
    select ca.car_id, target_user_id
    from public.car_access ca
    where ca.user_id = source_profile_id
    on conflict (car_id, user_id) do nothing;

    delete from public.car_access
    where user_id = source_profile_id;

    update public.refuel_events
    set user_id = target_user_id
    where user_id = source_profile_id;

    update public.car_expenses
    set user_id = target_user_id
    where user_id = source_profile_id;

    update public.vibilia_car_bluetooth_devices
    set created_by = target_user_id
    where created_by = source_profile_id;

    update public.vibilia_drive_intervals
    set user_id = target_user_id
    where user_id = source_profile_id;

    update public.profiles p
    set full_name = coalesce(p.full_name, src.full_name),
        email = coalesce(p.email, src.email)
    from public.profiles src
    where p.id = target_user_id
      and src.id = source_profile_id;

    delete from public.profiles
    where id = source_profile_id;
  else
    -- Cascades through all profile-linked FKs.
    update public.profiles
    set id = target_user_id
    where id = source_profile_id;
  end if;
end;
$$;

revoke all on function public.reassign_profile_user(uuid, uuid, boolean) from public;
grant execute on function public.reassign_profile_user(uuid, uuid, boolean) to service_role;
