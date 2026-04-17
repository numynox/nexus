-- Ensure Vibilia references public.profiles (shared profile model), not auth.users.
-- Also upgrade profile reassignment helper to move both Noctua and Vibilia user-owned data.

-- Backfill missing profile rows for any referenced user IDs so FK validation can succeed
-- even if signup trigger was previously missing.
with referenced_user_ids as (
  select owner_id as id from public.cars
  union
  select user_id as id from public.car_access
  union
  select user_id as id from public.refuel_events where user_id is not null
  union
  select user_id as id from public.car_expenses where user_id is not null
)
insert into public.profiles (id, email, full_name)
select
  r.id,
  u.email,
  coalesce(u.raw_user_meta_data ->> 'full_name', u.raw_user_meta_data ->> 'name')
from referenced_user_ids r
left join auth.users u on u.id = r.id
left join public.profiles p on p.id = r.id
where r.id is not null
  and p.id is null;

-- Decouple profiles from auth.users so restored snapshots can be reassigned safely
-- even when auth IDs differ between environments.
alter table public.profiles
  drop constraint if exists profiles_id_fkey;

-- cars.owner_id -> public.profiles
alter table public.cars
  drop constraint if exists cars_owner_id_fkey;

alter table public.cars
  add constraint cars_owner_id_fkey
  foreign key (owner_id)
  references public.profiles(id)
  on update cascade
  on delete cascade
  not valid;

alter table public.cars
  validate constraint cars_owner_id_fkey;

-- car_access.user_id -> public.profiles
alter table public.car_access
  drop constraint if exists car_access_user_id_fkey;

alter table public.car_access
  add constraint car_access_user_id_fkey
  foreign key (user_id)
  references public.profiles(id)
  on update cascade
  on delete cascade
  not valid;

alter table public.car_access
  validate constraint car_access_user_id_fkey;

-- refuel_events.user_id -> public.profiles
alter table public.refuel_events
  drop constraint if exists refuel_events_user_id_fkey;

alter table public.refuel_events
  add constraint refuel_events_user_id_fkey
  foreign key (user_id)
  references public.profiles(id)
  on update cascade
  on delete set null
  not valid;

alter table public.refuel_events
  validate constraint refuel_events_user_id_fkey;

-- car_expenses.user_id -> public.profiles
alter table public.car_expenses
  drop constraint if exists car_expenses_user_id_fkey;

alter table public.car_expenses
  add constraint car_expenses_user_id_fkey
  foreign key (user_id)
  references public.profiles(id)
  on update cascade
  on delete set null
  not valid;

alter table public.car_expenses
  validate constraint car_expenses_user_id_fkey;

-- Keep Noctua article_reads profile FK update-cascade semantics as part of restore hardening.
alter table public.article_reads
  drop constraint if exists article_reads_user_id_fkey;

alter table public.article_reads
  add constraint article_reads_user_id_fkey
  foreign key (user_id)
  references public.profiles(id)
  on update cascade
  on delete cascade
  not valid;

alter table public.article_reads
  validate constraint article_reads_user_id_fkey;

create or replace function public.reassign_profile_user(
  source_profile_id uuid,
  target_user_id uuid,
  merge_target_profile boolean default true
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
