-- Daily rollup for fuel prices.
--
-- fuel_prices grows by 7 stations x 3 fuel types x 6 readings/hour = ~3,000
-- rows a day and was 43% of the database after six months. Only three things
-- read it, and only one of them needs history:
--
--   * get_fuel_stations_current_prices  - newest reading per station and type
--   * get_fuel_price_plot_history       - a window of at most 7 days
--   * get_fuel_price_weekly_minima      - ranges of 1, 3, 5 years or "overall"
--
-- The weekly minima chart is the reason raw rows could not simply be deleted.
-- It is preserved exactly here rather than approximated, because the minimum of
-- daily minima *is* the weekly minimum. Days are Europe/Berlin calendar days
-- (see public.nexus_time_zone), so a reading at 00:30 local time counts towards
-- the day the reader would call it, not the UTC one.

-- The one place the display timezone is defined. Rollup buckets and week
-- boundaries both derive from it.
create or replace function public.nexus_time_zone()
returns text
language sql
immutable
as $$
  select 'Europe/Berlin'::text;
$$;

comment on function public.nexus_time_zone() is
  'Calendar timezone for reporting buckets (daily rollups, week boundaries).';

create table if not exists public.fuel_prices_daily (
  station_id uuid not null references public.fuel_stations(id) on update cascade on delete cascade,
  fuel_type text not null,
  day date not null,
  min_price numeric(5, 3) not null,
  max_price numeric(5, 3) not null,
  avg_price numeric(6, 4) not null,
  reading_count integer not null,
  updated_at timestamptz not null default now(),
  primary key (station_id, fuel_type, day)
);

comment on table public.fuel_prices_daily is
  'Daily min/max/avg per station and fuel type, in Europe/Berlin days. Long-term history; fuel_prices keeps only recent raw readings.';

create index if not exists fuel_prices_daily_fuel_type_day_idx
  on public.fuel_prices_daily (fuel_type, day);

alter table public.fuel_prices_daily enable row level security;

-- Matches fuel_prices: prices are public data, and the apps read them with the
-- anon key.
drop policy if exists "Public read fuel_prices_daily" on public.fuel_prices_daily;
create policy "Public read fuel_prices_daily"
  on public.fuel_prices_daily for select
  using (true);

grant select on public.fuel_prices_daily to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Rollup
-- ---------------------------------------------------------------------------

-- Rolls raw readings into daily buckets, up to and including yesterday (today
-- is still incomplete). Starts from the last day already stored — recomputing
-- it, since it may have been written from a partial day — so a run that was
-- missed for any number of days catches up on its own. Pass p_from_day to force
-- a wider recompute.
create or replace function public.rollup_fuel_prices_daily(p_from_day date default null)
returns integer
language plpgsql
set search_path = public
as $$
declare
  v_tz text := public.nexus_time_zone();
  v_start date;
  v_end date;
  v_rows integer := 0;
begin
  v_end := ((now() at time zone v_tz)::date - 1);

  v_start := coalesce(
    p_from_day,
    (select max(day) from public.fuel_prices_daily),
    (select min((checked_at at time zone v_tz)::date) from public.fuel_prices)
  );

  -- No raw data at all, or nothing new since the last run.
  if v_start is null or v_start > v_end then
    return 0;
  end if;

  insert into public.fuel_prices_daily as d (
    station_id, fuel_type, day, min_price, max_price, avg_price, reading_count, updated_at
  )
  select
    fp.station_id,
    fp.fuel_type,
    (fp.checked_at at time zone v_tz)::date,
    min(fp.price),
    max(fp.price),
    round(avg(fp.price), 4),
    count(*),
    now()
  from public.fuel_prices fp
  where fp.station_id is not null
    and (fp.checked_at at time zone v_tz)::date between v_start and v_end
  group by 1, 2, 3
  on conflict (station_id, fuel_type, day) do update
    set min_price = excluded.min_price,
        max_price = excluded.max_price,
        avg_price = excluded.avg_price,
        reading_count = excluded.reading_count,
        updated_at = excluded.updated_at;

  get diagnostics v_rows = row_count;
  return v_rows;
end;
$$;

comment on function public.rollup_fuel_prices_daily(date) is
  'Aggregates fuel_prices into fuel_prices_daily through yesterday (Europe/Berlin). Idempotent; catches up any missed days.';

revoke all on function public.rollup_fuel_prices_daily(date) from public;
grant execute on function public.rollup_fuel_prices_daily(date) to service_role;

-- ---------------------------------------------------------------------------
-- Weekly minima now read the rollup
-- ---------------------------------------------------------------------------

-- Same signature and same output as before. p_time_zone is retained so that an
-- already-deployed frontend keeps working (the websites deploy separately from
-- the database), but bucketing now always follows public.nexus_time_zone():
-- the daily rows are Berlin days, and a Berlin day cannot be re-cut into
-- another timezone after the fact. In practice the caller passes Europe/Berlin.
create or replace function public.get_fuel_price_weekly_minima(
  p_fuel_type text,
  p_since timestamp with time zone,
  p_until timestamp with time zone default now(),
  p_time_zone text default 'UTC'
)
returns table (
  week_start date,
  min_price numeric
)
language sql
stable
set search_path = public
as $$
  with bounds as (
    select
      public.nexus_time_zone() as tz,
      (p_since at time zone public.nexus_time_zone())::date as from_day,
      (p_until at time zone public.nexus_time_zone())::date as to_day
  ),
  last_rolled as (
    select coalesce(max(d.day), '-infinity'::date) as day
    from public.fuel_prices_daily d
    where d.fuel_type = p_fuel_type
  ),
  rolled as (
    select d.day, d.min_price
    from public.fuel_prices_daily d, bounds b
    where d.fuel_type = p_fuel_type
      and d.day between b.from_day and b.to_day
  ),
  -- Days the rollup has not covered yet (today, plus anything since the last
  -- successful run) still come from the raw table.
  raw_tail as (
    select (fp.checked_at at time zone b.tz)::date as day, fp.price as min_price
    from public.fuel_prices fp, bounds b, last_rolled lr
    where fp.fuel_type = p_fuel_type
      and fp.checked_at >= p_since
      and fp.checked_at <= p_until
      and (fp.checked_at at time zone b.tz)::date > lr.day
  )
  select
    date_trunc('week', all_days.day)::date as week_start,
    min(all_days.min_price)::numeric as min_price
  from (
    select day, min_price from rolled
    union all
    select day, min_price from raw_tail
  ) all_days
  group by 1
  order by 1 asc;
$$;

-- ---------------------------------------------------------------------------
-- Backfill every day of existing history before anything is ever pruned.
-- ---------------------------------------------------------------------------
select public.rollup_fuel_prices_daily();
