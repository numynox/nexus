-- Vibilia commutes.
--
-- A member of a car describes their commute in it: the weekdays, a time
-- window for the way to work and one for the way home, and where home and work
-- are (a point and a radius each). The expected travel time is the median of
-- that person's own Bluetooth drives in the car that started inside the window.
-- The Android app speaks the arrival time through the car when the phone
-- connects inside a window, near the place the trip starts from, and can text
-- one contact the same news (sms_* columns; sent by the phone, not a server).
--
-- Per person and per car: two people sharing a car have different commutes,
-- and home and work locations are nobody else's business. Nothing here is
-- shared with the car's other members.
-- Reasoning: .agents/brain/decisions/2026-11-02-commute-arrival-announcement.md

create table if not exists public.vibilia_commutes (
  user_id uuid not null default auth.uid()
    references public.profiles(id) on update cascade on delete cascade,
  car_id uuid not null references public.cars(id) on update cascade on delete cascade,
  -- ISO weekdays, Monday = 1 … Sunday = 7.
  weekdays smallint[] not null default '{1,2,3,4,5}',
  -- Local time in public.nexus_time_zone(). A window does not cross midnight.
  to_work_from time not null,
  to_work_until time not null,
  to_home_from time not null,
  to_home_until time not null,
  home_lat double precision not null,
  home_lng double precision not null,
  home_radius_m integer not null default 300,
  work_lat double precision not null,
  work_lng double precision not null,
  work_radius_m integer not null default 300,
  -- One person to text the arrival time to, by direction. The phone sends it
  -- from its own SIM; the server only remembers whom.
  sms_name text,
  sms_number text,
  sms_to_work boolean not null default false,
  sms_to_home boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (user_id, car_id),
  constraint vibilia_commutes_weekdays_valid
    check (cardinality(weekdays) between 1 and 7 and weekdays <@ '{1,2,3,4,5,6,7}'::smallint[]),
  constraint vibilia_commutes_to_work_window check (to_work_from < to_work_until),
  constraint vibilia_commutes_to_home_window check (to_home_from < to_home_until),
  constraint vibilia_commutes_home_point
    check (home_lat between -90 and 90 and home_lng between -180 and 180),
  constraint vibilia_commutes_work_point
    check (work_lat between -90 and 90 and work_lng between -180 and 180),
  constraint vibilia_commutes_home_radius check (home_radius_m between 50 and 5000),
  constraint vibilia_commutes_work_radius check (work_radius_m between 50 and 5000),
  constraint vibilia_commutes_sms_number_format check (sms_number ~ '^\+?[0-9]{3,20}$'),
  constraint vibilia_commutes_sms_name_length check (length(sms_name) <= 100),
  constraint vibilia_commutes_sms_needs_number
    check (sms_number is not null or not (sms_to_work or sms_to_home))
);

comment on table public.vibilia_commutes is
  'One person''s commute in one car: weekdays, time windows, home and work. Private to that person.';

alter table public.vibilia_commutes enable row level security;

-- Only your own commute, and only in a car you may use. Losing access to the
-- car hides the row; deleting the car deletes it.
drop policy if exists "Users read own commutes" on public.vibilia_commutes;
create policy "Users read own commutes"
  on public.vibilia_commutes for select to authenticated
  using (user_id = auth.uid() and public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Users create own commutes" on public.vibilia_commutes;
create policy "Users create own commutes"
  on public.vibilia_commutes for insert to authenticated
  with check (user_id = auth.uid() and public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Users change own commutes" on public.vibilia_commutes;
create policy "Users change own commutes"
  on public.vibilia_commutes for update to authenticated
  using (user_id = auth.uid() and public.user_can_access_car(car_id, auth.uid()))
  with check (user_id = auth.uid() and public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Users delete own commutes" on public.vibilia_commutes;
create policy "Users delete own commutes"
  on public.vibilia_commutes for delete to authenticated
  using (user_id = auth.uid());

grant select, insert, update, delete on public.vibilia_commutes to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Expected travel time
-- ---------------------------------------------------------------------------

-- One row per direction of the caller's commute in the car: the median length
-- of the caller's own drive intervals that started on a commute weekday inside
-- that direction's window, over the 20 most recent such drives in the last
-- 180 days. Recent drives only, so a new job or a new route takes over within
-- a month; the median, so the odd detour to the supermarket in the window does
-- not move it.
--
-- Own intervals rather than merged periods (get_car_drive_periods): a
-- passenger's phone reports the same drive, but someone else's commute in the
-- same car must not count. No rows without a commute; median_seconds is null
-- and sample_count 0 while no drive matches.
--
-- Runs as the caller: RLS on both tables already limits it to the caller's
-- commute and to cars the caller may see.
create or replace function public.get_vibilia_commute_expectation(
  p_car_id uuid,
  p_now timestamptz default now()
)
returns table (
  direction text,
  median_seconds integer,
  sample_count integer
)
language sql
stable
set search_path = public
as $$
  with commute as (
    select c.*
    from public.vibilia_commutes c
    where c.car_id = p_car_id
      and c.user_id = auth.uid()
  ),
  windows as (
    select 'to_work'::text as direction, c.to_work_from as w_from, c.to_work_until as w_until, c.weekdays
    from commute c
    union all
    select 'to_home'::text, c.to_home_from, c.to_home_until, c.weekdays
    from commute c
  ),
  drives as (
    select
      i.started_at,
      extract(epoch from i.ended_at - i.started_at)::numeric as seconds,
      i.started_at at time zone public.nexus_time_zone() as local_start
    from public.vibilia_drive_intervals i
    where i.car_id = p_car_id
      and i.user_id = auth.uid()
      and i.started_at > p_now - interval '180 days'
      and i.started_at <= p_now
  ),
  matched as (
    select
      w.direction,
      d.seconds,
      row_number() over (partition by w.direction order by d.started_at desc) as recency
    from windows w
    join drives d
      on extract(isodow from d.local_start)::smallint = any (w.weekdays)
     and d.local_start::time between w.w_from and w.w_until
  )
  select
    w.direction,
    (
      select round(percentile_cont(0.5) within group (order by m.seconds))::integer
      from matched m
      where m.direction = w.direction and m.recency <= 20
    ) as median_seconds,
    (
      select count(*)::integer
      from matched m
      where m.direction = w.direction and m.recency <= 20
    ) as sample_count
  from windows w
  order by w.direction = 'to_home', w.direction;
$$;

comment on function public.get_vibilia_commute_expectation(uuid, timestamptz) is
  'Median travel time per commute direction from the caller''s own drives in the car.';

revoke all on function public.get_vibilia_commute_expectation(uuid, timestamptz) from public;
grant execute on function public.get_vibilia_commute_expectation(uuid, timestamptz) to authenticated, service_role;
