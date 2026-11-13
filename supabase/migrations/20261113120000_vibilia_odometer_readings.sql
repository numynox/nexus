-- Vibilia odometer readings.
--
-- A dated odometer value with no cost attached: a reading from a service
-- record from before the car was bought, the reading at purchase, a TÜV
-- report. It completes the odometer chart without being a refuel or an
-- expense, so none of the statistics change:
--   * a back-dated refuel would make the distance between the old reading and
--     the first real refuel count as driven, with consumption across it;
--   * a 0 € expense would stretch "expenses per year" back to the old date.
-- Readings are only plotted (get_car_odometer_plot) and listed in the logbook.
-- Reasoning: .agents/brain/decisions/2026-11-13-vibilia-odometer-readings.md

create table if not exists public.vibilia_odometer_readings (
  id bigserial primary key,
  car_id uuid not null references public.cars(id) on update cascade on delete cascade,
  user_id uuid default auth.uid()
    references public.profiles(id) on update cascade on delete set null,
  read_at timestamptz not null,
  mileage integer not null,
  notes text,
  created_at timestamptz not null default now(),
  constraint vibilia_odometer_readings_mileage_range check (mileage between 0 and 999999),
  constraint vibilia_odometer_readings_notes_length check (length(notes) <= 500)
);

comment on table public.vibilia_odometer_readings is
  'A dated odometer value without a cost (e.g. from a service record). Plotted, never counted in statistics.';

create index if not exists vibilia_odometer_readings_car_read_at_idx
  on public.vibilia_odometer_readings (car_id, read_at desc);

alter table public.vibilia_odometer_readings enable row level security;

-- Shared like everything else of a car: owner and members see and edit all of
-- its readings. A new row is always the caller's own.
drop policy if exists "Members read odometer readings" on public.vibilia_odometer_readings;
create policy "Members read odometer readings"
  on public.vibilia_odometer_readings for select to authenticated
  using (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Members add odometer readings" on public.vibilia_odometer_readings;
create policy "Members add odometer readings"
  on public.vibilia_odometer_readings for insert to authenticated
  with check (user_id = auth.uid() and public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Members change odometer readings" on public.vibilia_odometer_readings;
create policy "Members change odometer readings"
  on public.vibilia_odometer_readings for update to authenticated
  using (public.user_can_access_car(car_id, auth.uid()))
  with check (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Members delete odometer readings" on public.vibilia_odometer_readings;
create policy "Members delete odometer readings"
  on public.vibilia_odometer_readings for delete to authenticated
  using (public.user_can_access_car(car_id, auth.uid()));

-- ---------------------------------------------------------------------------
-- The odometer chart: every refuel and every reading, oldest first
-- ---------------------------------------------------------------------------

-- Replaces get_car_refuel_plot_events' mileage column for the chart; that
-- function stays as it is for older APKs and the other charts.
create or replace function public.get_car_odometer_plot(
  p_car_id uuid,
  p_since timestamptz default '-infinity',
  p_until timestamptz default now()
)
returns table (
  read_at timestamptz,
  mileage integer,
  source text
)
language sql
stable
set search_path = public
as $$
  select p.read_at, p.mileage, p.source
  from (
    select r.fueled_at as read_at, r.mileage, 'refuel'::text as source
    from public.refuel_events r
    where r.car_id = p_car_id and r.mileage is not null
    union all
    select o.read_at, o.mileage, 'reading'::text
    from public.vibilia_odometer_readings o
    where o.car_id = p_car_id
  ) p
  where public.user_can_access_car(p_car_id, auth.uid())
    and p.read_at >= p_since
    and p.read_at <= p_until
  order by p.read_at asc, p.mileage asc;
$$;

revoke all on function public.get_car_odometer_plot(uuid, timestamptz, timestamptz) from public, anon;
grant execute on function public.get_car_odometer_plot(uuid, timestamptz, timestamptz) to authenticated, service_role;

-- The statistics range picker's years now reach back to the oldest reading,
-- so "All" and the year options include the readings. Same signature and
-- result as before (20260412195000), so older clients simply get more years.
-- The access check stays inline, not user_can_access_car: anon may still call
-- this function and must keep getting an empty answer, not an error.
create or replace function public.get_car_refuel_year_bounds(
  p_car_id uuid
)
returns table (
  min_year integer,
  max_year integer
)
language sql
stable
set search_path = public
as $$
  with base_events as (
    select r.fueled_at as at
    from public.refuel_events r
    where r.car_id = p_car_id
    union all
    select o.read_at
    from public.vibilia_odometer_readings o
    where o.car_id = p_car_id
  )
  select
    min(extract(year from at))::int as min_year,
    max(extract(year from at))::int as max_year
  from base_events
  where exists (
    select 1
    from public.cars c
    where c.id = p_car_id
      and (
        c.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access ca
          where ca.car_id = c.id and ca.user_id = auth.uid()
        )
      )
  );
$$;

-- ---------------------------------------------------------------------------
-- Restores: carry the new user reference across a profile merge
-- ---------------------------------------------------------------------------

-- Identical to 20261019120000 plus vibilia_odometer_readings in the merge
-- branch; the non-merge branch cascades through the foreign key.
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

    update public.vibilia_odometer_readings
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
revoke all on function public.reassign_profile_user(uuid, uuid, boolean) from anon, authenticated;
grant execute on function public.reassign_profile_user(uuid, uuid, boolean) to service_role;
