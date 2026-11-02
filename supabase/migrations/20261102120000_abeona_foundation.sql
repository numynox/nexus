-- Abeona, the train ride companion: households, travellers, trips and legs.
--
-- Scoping is a shared household (decisions/2026-11-02-abeona-train-companion.md):
-- every member of a household reads and writes all of its trips and travellers.
-- One helper, abeona_user_in_household(), is what every policy and RPC defers
-- to — the pattern of Vibilia's user_can_access_car().
--
-- A user belongs to at most one household. It is created on first use by
-- abeona_ensure_household(). Inviting someone whose household has no other
-- members moves them, and their trips, into the inviter's household.

-- ── Tables ─────────────────────────────────────────────────────────────────

create table if not exists public.abeona_households (
  id uuid primary key default gen_random_uuid(),
  name text not null default 'Household',
  created_at timestamptz not null default now()
);

create table if not exists public.abeona_household_members (
  household_id uuid not null references public.abeona_households(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner', 'member')),
  joined_at timestamptz not null default now(),
  primary key (household_id, user_id),
  unique (user_id)
);

-- A person who travels: the members themselves and anyone they travel with
-- (children, a partner without an account). BahnCards will hang off this.
create table if not exists public.abeona_travellers (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.abeona_households(id) on delete cascade,
  name text not null check (btrim(name) <> ''),
  user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now()
);

create index if not exists abeona_travellers_household_idx
  on public.abeona_travellers (household_id);

create table if not exists public.abeona_trips (
  id uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.abeona_households(id) on delete cascade,
  created_by uuid references auth.users(id) on delete set null,
  title text,
  status text not null default 'planned' check (status in ('planned', 'cancelled')),
  source text not null default 'search' check (source in ('search', 'manual', 'share', 'pdf', 'import')),
  -- Where the connection came from, and what refreshes it.
  provider text not null default 'transitous',
  refresh_token text,
  origin_name text not null,
  destination_name text not null,
  planned_departure timestamptz not null,
  planned_arrival timestamptz not null,
  -- The ticket, as far as the app knows it. Set by hand for now; the PDF
  -- parser will fill it later.
  fare_type text not null default 'other'
    check (fare_type in ('flex', 'saver', 'regional', 'deutschlandticket', 'bahncard100', 'other')),
  train_binding boolean not null default false,
  travel_class smallint not null default 2 check (travel_class in (1, 2)),
  price_cents integer check (price_cents is null or price_cents >= 0),
  -- Storage object path in the abeona-tickets bucket: <household_id>/<trip_id>/<file>
  ticket_path text,
  refreshed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (planned_arrival >= planned_departure)
);

create index if not exists abeona_trips_household_departure_idx
  on public.abeona_trips (household_id, planned_departure);

create table if not exists public.abeona_trip_travellers (
  trip_id uuid not null references public.abeona_trips(id) on delete cascade,
  traveller_id uuid not null references public.abeona_travellers(id) on delete cascade,
  primary key (trip_id, traveller_id)
);

-- One leg of a trip: a train ride, a bus, or the walk between two of them.
-- Planned values never change after saving; the current ones are refreshed.
create table if not exists public.abeona_legs (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references public.abeona_trips(id) on delete cascade,
  idx smallint not null,
  mode text not null check (mode in ('train', 'bus', 'walk', 'other')),
  product text,            -- ICE, IC, EC, RE, RB, S, … (null for walks)
  line_name text,          -- "ICE 932"
  trip_ref text,           -- the provider's trip id, for refreshing this leg
  headsign text,
  operator text,
  from_id text,
  from_name text not null,
  from_lat double precision,
  from_lon double precision,
  to_id text,
  to_name text not null,
  to_lat double precision,
  to_lon double precision,
  planned_departure timestamptz not null,
  departure timestamptz,   -- current estimate; null = no realtime data
  planned_arrival timestamptz not null,
  arrival timestamptz,
  planned_departure_platform text,
  departure_platform text,
  planned_arrival_platform text,
  arrival_platform text,
  cancelled boolean not null default false,
  realtime boolean not null default false,
  stopovers jsonb not null default '[]'::jsonb,
  polyline jsonb,          -- [[lon, lat], …], simplified
  distance_km numeric(8, 1),
  unique (trip_id, idx)
);

-- ── Access helpers ─────────────────────────────────────────────────────────

create or replace function public.abeona_user_in_household(
  p_household_id uuid,
  p_user_id uuid default auth.uid()
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.abeona_household_members m
    where m.household_id = p_household_id
      and m.user_id = p_user_id
  );
$$;

revoke all on function public.abeona_user_in_household(uuid, uuid) from public;
grant execute on function public.abeona_user_in_household(uuid, uuid) to authenticated;

-- Storage paths are <household_id>/<trip_id>/<file> (or <household_id>/<…>
-- for files that belong to no trip). Access follows the trip when the second
-- segment is one, so a ticket stays readable after its trip moved household
-- on a merge; otherwise it follows the household in the first segment.
-- Anything that is not a uuid there is simply not accessible, rather than an
-- error in a policy.
create or replace function public.abeona_user_in_household_path(p_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uuid constant text := '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
  v_first text := split_part(coalesce(p_name, ''), '/', 1);
  v_second text := split_part(coalesce(p_name, ''), '/', 2);
  v_trip_household uuid;
begin
  if v_second ~* v_uuid then
    select household_id into v_trip_household
    from public.abeona_trips
    where id = v_second::uuid;
    if v_trip_household is not null then
      return public.abeona_user_in_household(v_trip_household, auth.uid());
    end if;
  end if;
  if v_first !~* v_uuid then
    return false;
  end if;
  return public.abeona_user_in_household(v_first::uuid, auth.uid());
end;
$$;

revoke all on function public.abeona_user_in_household_path(text) from public;
grant execute on function public.abeona_user_in_household_path(text) to authenticated;

create or replace function public.abeona_current_household()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select m.household_id
  from public.abeona_household_members m
  where m.user_id = auth.uid();
$$;

revoke all on function public.abeona_current_household() from public;
grant execute on function public.abeona_current_household() to authenticated;

-- ── Row level security ─────────────────────────────────────────────────────

alter table public.abeona_households enable row level security;
alter table public.abeona_household_members enable row level security;
alter table public.abeona_travellers enable row level security;
alter table public.abeona_trips enable row level security;
alter table public.abeona_trip_travellers enable row level security;
alter table public.abeona_legs enable row level security;

-- Households and memberships change only through the RPCs below.
drop policy if exists abeona_households_select on public.abeona_households;
create policy abeona_households_select on public.abeona_households
  for select to authenticated
  using (public.abeona_user_in_household(id, auth.uid()));

drop policy if exists abeona_households_update on public.abeona_households;
create policy abeona_households_update on public.abeona_households
  for update to authenticated
  using (public.abeona_user_in_household(id, auth.uid()))
  with check (public.abeona_user_in_household(id, auth.uid()));

drop policy if exists abeona_members_select on public.abeona_household_members;
create policy abeona_members_select on public.abeona_household_members
  for select to authenticated
  using (public.abeona_user_in_household(household_id, auth.uid()));

drop policy if exists abeona_travellers_all on public.abeona_travellers;
create policy abeona_travellers_all on public.abeona_travellers
  for all to authenticated
  using (public.abeona_user_in_household(household_id, auth.uid()))
  with check (public.abeona_user_in_household(household_id, auth.uid()));

drop policy if exists abeona_trips_all on public.abeona_trips;
create policy abeona_trips_all on public.abeona_trips
  for all to authenticated
  using (public.abeona_user_in_household(household_id, auth.uid()))
  with check (public.abeona_user_in_household(household_id, auth.uid()));

drop policy if exists abeona_trip_travellers_all on public.abeona_trip_travellers;
create policy abeona_trip_travellers_all on public.abeona_trip_travellers
  for all to authenticated
  using (
    exists (
      select 1 from public.abeona_trips t
      where t.id = trip_id
        and public.abeona_user_in_household(t.household_id, auth.uid())
    )
  )
  with check (
    exists (
      select 1 from public.abeona_trips t
      join public.abeona_travellers tr on tr.id = traveller_id
      where t.id = trip_id
        and tr.household_id = t.household_id
        and public.abeona_user_in_household(t.household_id, auth.uid())
    )
  );

drop policy if exists abeona_legs_all on public.abeona_legs;
create policy abeona_legs_all on public.abeona_legs
  for all to authenticated
  using (
    exists (
      select 1 from public.abeona_trips t
      where t.id = trip_id
        and public.abeona_user_in_household(t.household_id, auth.uid())
    )
  )
  with check (
    exists (
      select 1 from public.abeona_trips t
      where t.id = trip_id
        and public.abeona_user_in_household(t.household_id, auth.uid())
    )
  );

-- ── Household RPCs ─────────────────────────────────────────────────────────

-- Returns the caller's household, creating it (and a traveller for the caller)
-- on first use. Every client calls this once after sign-in.
create or replace function public.abeona_ensure_household()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_household uuid;
  v_name text;
begin
  if v_uid is null then
    raise exception 'Not signed in.';
  end if;

  select household_id into v_household
  from public.abeona_household_members
  where user_id = v_uid;

  if v_household is not null then
    return v_household;
  end if;

  insert into public.abeona_households default values
  returning id into v_household;

  insert into public.abeona_household_members (household_id, user_id, role)
  values (v_household, v_uid, 'owner');

  select coalesce(nullif(btrim(p.full_name), ''), split_part(p.email, '@', 1), 'Me')
  into v_name
  from public.profiles p
  where p.id = v_uid;

  insert into public.abeona_travellers (household_id, name, user_id)
  values (v_household, coalesce(v_name, 'Me'), v_uid);

  return v_household;
end;
$$;

revoke all on function public.abeona_ensure_household() from public;
grant execute on function public.abeona_ensure_household() to authenticated;

-- Adds another Nexus user to the caller's household. If that user already has
-- a household of their own with nobody else in it, its trips and travellers
-- move over and the empty household is removed. Someone who already shares a
-- household with others must leave it first.
create or replace function public.abeona_invite_to_household(p_email text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_household uuid;
  v_target uuid;
  v_target_household uuid;
  v_target_members integer;
begin
  if p_email is null or btrim(p_email) = '' then
    raise exception 'Email is required.';
  end if;

  v_household := public.abeona_ensure_household();

  select p.id into v_target
  from public.profiles p
  where lower(p.email) = lower(btrim(p_email))
  limit 1;

  if v_target is null then
    raise exception 'No user found for this email.';
  end if;

  if v_target = v_uid then
    raise exception 'You are already in this household.';
  end if;

  select household_id into v_target_household
  from public.abeona_household_members
  where user_id = v_target;

  if v_target_household = v_household then
    return v_target;
  end if;

  if v_target_household is not null then
    select count(*) into v_target_members
    from public.abeona_household_members
    where household_id = v_target_household;

    if v_target_members > 1 then
      raise exception 'This user already shares a household with others.';
    end if;

    update public.abeona_trips set household_id = v_household
    where household_id = v_target_household;

    update public.abeona_travellers set household_id = v_household
    where household_id = v_target_household;

    delete from public.abeona_households where id = v_target_household;
  end if;

  insert into public.abeona_household_members (household_id, user_id, role)
  values (v_household, v_target, 'member');

  -- Make sure the new member exists as a traveller.
  if not exists (
    select 1 from public.abeona_travellers
    where household_id = v_household and user_id = v_target
  ) then
    insert into public.abeona_travellers (household_id, name, user_id)
    select v_household,
           coalesce(nullif(btrim(p.full_name), ''), split_part(p.email, '@', 1)),
           p.id
    from public.profiles p
    where p.id = v_target;
  end if;

  return v_target;
end;
$$;

revoke all on function public.abeona_invite_to_household(text) from public;
grant execute on function public.abeona_invite_to_household(text) to authenticated;

-- Leaves the shared household. The trips stay with it; the caller starts over
-- in a new, empty household of their own.
create or replace function public.abeona_leave_household()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_household uuid;
  v_remaining integer;
begin
  select household_id into v_household
  from public.abeona_household_members
  where user_id = v_uid;

  if v_household is null then
    return public.abeona_ensure_household();
  end if;

  select count(*) into v_remaining
  from public.abeona_household_members
  where household_id = v_household and user_id <> v_uid;

  if v_remaining = 0 then
    raise exception 'You are the only member of this household.';
  end if;

  delete from public.abeona_household_members
  where household_id = v_household and user_id = v_uid;

  -- Someone has to own what is left behind.
  update public.abeona_household_members
  set role = 'owner'
  where household_id = v_household
    and user_id = (
      select user_id from public.abeona_household_members
      where household_id = v_household
      order by (role = 'owner') desc, joined_at
      limit 1
    );

  return public.abeona_ensure_household();
end;
$$;

revoke all on function public.abeona_leave_household() from public;
grant execute on function public.abeona_leave_household() to authenticated;

create or replace function public.abeona_list_household_members()
returns table (
  user_id uuid,
  email text,
  full_name text,
  role text,
  joined_at timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
  select m.user_id, p.email, p.full_name, m.role, m.joined_at
  from public.abeona_household_members m
  left join public.profiles p on p.id = m.user_id
  where m.household_id = public.abeona_current_household()
  order by m.joined_at;
$$;

revoke all on function public.abeona_list_household_members() from public;
grant execute on function public.abeona_list_household_members() to authenticated;

-- ── Distance ───────────────────────────────────────────────────────────────

create or replace function public.abeona_haversine_km(
  p_lat1 double precision, p_lon1 double precision,
  p_lat2 double precision, p_lon2 double precision
)
returns double precision
language sql
immutable
as $$
  select 2 * 6371.0 * asin(sqrt(
    power(sin(radians(p_lat2 - p_lat1) / 2), 2)
    + cos(radians(p_lat1)) * cos(radians(p_lat2))
      * power(sin(radians(p_lon2 - p_lon1) / 2), 2)
  ));
$$;

-- Length of a [[lon, lat], …] polyline in km; null when there is none.
create or replace function public.abeona_polyline_km(p_polyline jsonb)
returns double precision
language sql
immutable
as $$
  with pts as (
    select ord,
           (pt->>1)::double precision as lat,
           (pt->>0)::double precision as lon
    from jsonb_array_elements(
      case when jsonb_typeof(p_polyline) = 'array' then p_polyline else '[]'::jsonb end
    ) with ordinality as e(pt, ord)
  ),
  segs as (
    select public.abeona_haversine_km(
             lag(lat) over (order by ord), lag(lon) over (order by ord), lat, lon
           ) as km
    from pts
  )
  select case when count(km) = 0 then null else sum(km) end from segs;
$$;

-- ── Saving a trip ──────────────────────────────────────────────────────────

-- Inserts a trip with its legs, or replaces an existing trip's legs, in one
-- transaction. Runs as the caller, so RLS decides what may be written.
--
-- p_trip: { id?, title?, source?, provider?, refresh_token?, fare_type?,
--           train_binding?, travel_class?, price_cents? }
-- p_legs: [ { mode, product, line_name, trip_ref, headsign, operator,
--             from: {id,name,lat,lon,planned,current,planned_platform,platform},
--             to:   {…same…}, cancelled, realtime, stopovers, polyline } ]
create or replace function public.abeona_save_trip(p_trip jsonb, p_legs jsonb)
returns uuid
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_household uuid := public.abeona_ensure_household();
  v_trip_id uuid := nullif(p_trip->>'id', '')::uuid;
  v_leg jsonb;
  v_idx integer := 0;
  v_first jsonb;
  v_last jsonb;
  v_km double precision;
begin
  if jsonb_typeof(p_legs) <> 'array' or jsonb_array_length(p_legs) = 0 then
    raise exception 'A trip needs at least one leg.';
  end if;

  v_first := p_legs->0;
  v_last := p_legs->(jsonb_array_length(p_legs) - 1);

  if v_trip_id is null then
    insert into public.abeona_trips (
      household_id, created_by, title, source, provider, refresh_token,
      origin_name, destination_name, planned_departure, planned_arrival,
      fare_type, train_binding, travel_class, price_cents, refreshed_at
    ) values (
      v_household, auth.uid(),
      nullif(btrim(p_trip->>'title'), ''),
      coalesce(p_trip->>'source', 'search'),
      coalesce(p_trip->>'provider', 'transitous'),
      p_trip->>'refresh_token',
      v_first->'from'->>'name',
      v_last->'to'->>'name',
      (v_first->'from'->>'planned')::timestamptz,
      (v_last->'to'->>'planned')::timestamptz,
      coalesce(p_trip->>'fare_type', 'other'),
      coalesce((p_trip->>'train_binding')::boolean, false),
      coalesce((p_trip->>'travel_class')::smallint, 2),
      (p_trip->>'price_cents')::integer,
      now()
    )
    returning id into v_trip_id;
  else
    update public.abeona_trips set
      refresh_token = coalesce(p_trip->>'refresh_token', refresh_token),
      refreshed_at = now(),
      updated_at = now()
    where id = v_trip_id;

    if not found then
      raise exception 'Trip not found.';
    end if;

    delete from public.abeona_legs where trip_id = v_trip_id;
  end if;

  for v_leg in select * from jsonb_array_elements(p_legs) loop
    v_km := public.abeona_polyline_km(v_leg->'polyline');
    if v_km is null and coalesce(v_leg->>'mode', 'train') <> 'walk' then
      v_km := 1.1 * public.abeona_haversine_km(
        (v_leg->'from'->>'lat')::double precision, (v_leg->'from'->>'lon')::double precision,
        (v_leg->'to'->>'lat')::double precision, (v_leg->'to'->>'lon')::double precision
      );
    end if;

    insert into public.abeona_legs (
      trip_id, idx, mode, product, line_name, trip_ref, headsign, operator,
      from_id, from_name, from_lat, from_lon,
      to_id, to_name, to_lat, to_lon,
      planned_departure, departure, planned_arrival, arrival,
      planned_departure_platform, departure_platform,
      planned_arrival_platform, arrival_platform,
      cancelled, realtime, stopovers, polyline, distance_km
    ) values (
      v_trip_id, v_idx,
      coalesce(v_leg->>'mode', 'train'),
      v_leg->>'product', v_leg->>'line_name', v_leg->>'trip_ref',
      v_leg->>'headsign', v_leg->>'operator',
      v_leg->'from'->>'id', v_leg->'from'->>'name',
      (v_leg->'from'->>'lat')::double precision, (v_leg->'from'->>'lon')::double precision,
      v_leg->'to'->>'id', v_leg->'to'->>'name',
      (v_leg->'to'->>'lat')::double precision, (v_leg->'to'->>'lon')::double precision,
      (v_leg->'from'->>'planned')::timestamptz, (v_leg->'from'->>'current')::timestamptz,
      (v_leg->'to'->>'planned')::timestamptz, (v_leg->'to'->>'current')::timestamptz,
      v_leg->'from'->>'planned_platform', v_leg->'from'->>'platform',
      v_leg->'to'->>'planned_platform', v_leg->'to'->>'platform',
      coalesce((v_leg->>'cancelled')::boolean, false),
      coalesce((v_leg->>'realtime')::boolean, false),
      coalesce(v_leg->'stopovers', '[]'::jsonb),
      v_leg->'polyline',
      round(v_km::numeric, 1)
    );
    v_idx := v_idx + 1;
  end loop;

  return v_trip_id;
end;
$$;

revoke all on function public.abeona_save_trip(jsonb, jsonb) from public;
grant execute on function public.abeona_save_trip(jsonb, jsonb) to authenticated;

-- Writes refreshed live values onto the existing legs, matched by position.
-- Planned values and the route stay as saved.
-- p_legs: [ { idx, departure, arrival, departure_platform, arrival_platform,
--             cancelled, realtime, stopovers } ]
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
    and l.idx = (x->>'idx')::smallint;

  update public.abeona_trips set refreshed_at = now(), updated_at = now()
  where id = p_trip_id;
end;
$$;

revoke all on function public.abeona_update_trip_live(uuid, jsonb) from public;
grant execute on function public.abeona_update_trip_live(uuid, jsonb) to authenticated;

-- ── Rules ──────────────────────────────────────────────────────────────────

-- DB lifts the train binding of a bound long-distance ticket when the expected
-- delay at the destination is at least 20 minutes. Never for regional tickets
-- or the Deutschlandticket: they have no binding to lift, and the Deutschland-
-- ticket does not open IC/ICE. Both clients mirror this for live values.
create or replace function public.abeona_train_binding_lifted(
  p_fare_type text,
  p_train_binding boolean,
  p_arrival_delay_minutes integer
)
returns boolean
language sql
immutable
as $$
  select coalesce(p_train_binding, false)
     and coalesce(p_fare_type, 'other') not in ('regional', 'deutschlandticket', 'bahncard100')
     and coalesce(p_arrival_delay_minutes, 0) >= 20;
$$;

grant execute on function public.abeona_train_binding_lifted(text, boolean, integer) to authenticated;

-- ── Statistics ─────────────────────────────────────────────────────────────

-- Everything the history card and the year in review show, for the caller's
-- household and trips that have arrived inside [p_from, p_to). Delay is the
-- final arrival delay of a trip, from the last refreshed value.
create or replace function public.abeona_statistics(p_from timestamptz, p_to timestamptz)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with trips as (
    select t.*
    from public.abeona_trips t
    where t.household_id = public.abeona_current_household()
      and t.status = 'planned'
      and t.planned_arrival >= p_from
      and t.planned_arrival < least(p_to, now())
  ),
  rides as (
    select l.*
    from public.abeona_legs l
    join trips t on t.id = l.trip_id
    where l.mode <> 'walk' and not l.cancelled
  ),
  per_trip as (
    select t.id, t.origin_name, t.destination_name, t.planned_arrival,
      (select greatest(0, extract(epoch from (coalesce(l.arrival, l.planned_arrival) - l.planned_arrival)) / 60)
       from public.abeona_legs l
       where l.trip_id = t.id and l.mode <> 'walk'
       order by l.idx desc limit 1) as delay_min,
      (select coalesce(sum(r.distance_km), 0) from rides r where r.trip_id = t.id) as km
    from trips t
  ),
  stations as (
    select from_name as name from rides
    union all
    select to_name from rides
  )
  select jsonb_build_object(
    'trips', (select count(*) from per_trip),
    'km', (select coalesce(round(sum(km)::numeric, 0), 0) from per_trip),
    'minutes_on_train', (select coalesce(round(sum(extract(epoch from (coalesce(arrival, planned_arrival) - coalesce(departure, planned_departure))) / 60)), 0) from rides),
    'rides', (select count(*) from rides),
    'delay_minutes', (select coalesce(round(sum(delay_min)), 0) from per_trip),
    'punctual_share', (select case when count(*) = 0 then null else round(avg(case when delay_min < 6 then 1.0 else 0.0 end), 3) end from per_trip),
    'longest_km', (select coalesce(round(max(km)::numeric, 0), 0) from per_trip),
    'max_delay_minutes', (select coalesce(round(max(delay_min)), 0) from per_trip),
    'stations', (select count(distinct name) from stations),
    'top_route', (
      select jsonb_build_object('from', origin_name, 'to', destination_name, 'count', n)
      from (
        select least(origin_name, destination_name) as origin_name,
               greatest(origin_name, destination_name) as destination_name,
               count(*) as n
        from per_trip group by 1, 2 order by n desc, 1 limit 1
      ) r
    ),
    'top_station', (
      select jsonb_build_object('name', name, 'count', n)
      from (select name, count(*) as n from stations group by name order by n desc, name limit 1) s
    ),
    'products', coalesce((
      select jsonb_object_agg(product, n)
      from (select coalesce(product, 'other') as product, count(*) as n from rides group by 1) p
    ), '{}'::jsonb),
    'by_month', coalesce((
      select jsonb_agg(jsonb_build_object('month', month, 'trips', trips, 'km', km) order by month)
      from (
        select to_char(planned_arrival at time zone public.nexus_time_zone(), 'YYYY-MM') as month,
               count(*) as trips,
               round(sum(km)::numeric, 0) as km
        from per_trip group by 1
      ) m
    ), '[]'::jsonb)
  );
$$;

revoke all on function public.abeona_statistics(timestamptz, timestamptz) from public;
grant execute on function public.abeona_statistics(timestamptz, timestamptz) to authenticated;

-- ── Ticket storage ─────────────────────────────────────────────────────────

-- Private bucket; objects live under <household_id>/<trip_id>/<file> and are
-- readable and writable by the members of that household only
-- (decisions/2026-11-02-abeona-ticket-storage.md).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('abeona-tickets', 'abeona-tickets', false, 10485760,
        array['application/pdf', 'image/png', 'image/jpeg'])
on conflict (id) do nothing;

drop policy if exists abeona_tickets_select on storage.objects;
create policy abeona_tickets_select on storage.objects
  for select to authenticated
  using (bucket_id = 'abeona-tickets' and public.abeona_user_in_household_path(name));

drop policy if exists abeona_tickets_insert on storage.objects;
create policy abeona_tickets_insert on storage.objects
  for insert to authenticated
  with check (bucket_id = 'abeona-tickets' and public.abeona_user_in_household_path(name));

drop policy if exists abeona_tickets_update on storage.objects;
create policy abeona_tickets_update on storage.objects
  for update to authenticated
  using (bucket_id = 'abeona-tickets' and public.abeona_user_in_household_path(name))
  with check (bucket_id = 'abeona-tickets' and public.abeona_user_in_household_path(name));

drop policy if exists abeona_tickets_delete on storage.objects;
create policy abeona_tickets_delete on storage.objects
  for delete to authenticated
  using (bucket_id = 'abeona-tickets' and public.abeona_user_in_household_path(name));
