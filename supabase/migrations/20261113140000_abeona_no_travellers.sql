-- Abeona: no more travellers (issue #62,
-- decisions/2026-11-13-abeona-no-travellers.md).
--
-- A trip records what its ticket needs: how many travellers, and which
-- BahnCard discounts it was booked with (one entry per card). BahnCards
-- belong to the household, with a name, instead of to a traveller. The
-- traveller tables, the per-traveller discounts and the trip warnings go.
--
-- Not backwards compatible, on purpose (decided for #62): installed APKs
-- older than this change lose their traveller features until updated.

-- ── Trips: what the ticket needs ───────────────────────────────────────────

alter table public.abeona_trips
  add column if not exists traveller_count smallint
    check (traveller_count is null or traveller_count between 1 and 99),
  add column if not exists bahncard_discounts smallint[] not null default '{}'
    check (bahncard_discounts <@ array[25, 50, 100]::smallint[]);

comment on column public.abeona_trips.traveller_count is
  'How many people the ticket is for; null when unknown.';
comment on column public.abeona_trips.bahncard_discounts is
  'The BahnCard discounts the ticket was booked with, one entry per card, ascending (e.g. {25,50}). A BahnCard 100 fare is a fare_type of its own.';

-- From the travellers each trip had, else from the discount the trip shared.
update public.abeona_trips t set
  traveller_count = least(c.riders, 99)::smallint,
  bahncard_discounts = c.discounts
from (
  select tt.trip_id,
         count(*) as riders,
         coalesce(array_agg(tt.bahncard_discount order by tt.bahncard_discount)
                    filter (where tt.bahncard_discount is not null), '{}') as discounts
  from public.abeona_trip_travellers tt
  group by tt.trip_id
) c
where c.trip_id = t.id;

update public.abeona_trips t set bahncard_discounts = array[t.bahncard_discount]
where t.bahncard_discount is not null
  and t.bahncard_discounts = '{}'
  and not exists (select 1 from public.abeona_trip_travellers tt where tt.trip_id = t.id);

-- ── BahnCards: the household's, with a name ────────────────────────────────

alter table public.abeona_bahncards
  add column if not exists household_id uuid references public.abeona_households(id) on delete cascade,
  add column if not exists name text check (name is null or btrim(name) <> '');

comment on column public.abeona_bahncards.name is
  'What the household calls the card ("Jannis", "Work card"); null shows the kind.';

update public.abeona_bahncards c set
  household_id = t.household_id,
  name = coalesce(c.name, nullif(btrim(t.name), ''))
from public.abeona_travellers t
where t.id = c.traveller_id;

alter table public.abeona_bahncards alter column household_id set not null;

create index if not exists abeona_bahncards_household_idx
  on public.abeona_bahncards (household_id);

drop policy if exists abeona_bahncards_all on public.abeona_bahncards;
create policy abeona_bahncards_all on public.abeona_bahncards
  for all to authenticated
  using (public.abeona_user_in_household(household_id, auth.uid()))
  with check (public.abeona_user_in_household(household_id, auth.uid()));

-- ── What goes ──────────────────────────────────────────────────────────────

drop trigger if exists abeona_trips_spread_discount on public.abeona_trips;
drop function if exists public.abeona_trips_spread_discount();
drop function if exists public.abeona_uniform_discount(jsonb);
drop function if exists public.abeona_save_trip_travellers(uuid, jsonb);
drop function if exists public.abeona_set_trip_travellers(uuid, uuid[]);
drop function if exists public.abeona_trip_warnings(uuid);
-- Its rows named a traveller; it comes back below with the card's name.
drop function if exists public.abeona_bahncard_deadlines(integer);

drop index if exists public.abeona_bahncards_traveller_idx;
alter table public.abeona_bahncards drop column if exists traveller_id;
alter table public.abeona_trips drop column if exists bahncard_discount;
drop table if exists public.abeona_trip_travellers;
drop table if exists public.abeona_travellers;

-- ── Functions without travellers ───────────────────────────────────────────

-- As in 20261102120000_abeona_foundation, without creating a traveller.
create or replace function public.abeona_ensure_household()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_household uuid;
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

  return v_household;
end;
$$;

-- As in 20261102120000_abeona_foundation; a merge moves the joining
-- household's BahnCards along with its trips. No traveller to create.
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

    update public.abeona_bahncards set household_id = v_household
    where household_id = v_target_household;

    delete from public.abeona_households where id = v_target_household;
  end if;

  insert into public.abeona_household_members (household_id, user_id, role)
  values (v_household, v_target, 'member');

  return v_target;
end;
$$;

-- As in 20261103110000_abeona_bahncards; a card names its household itself.
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
  v_third text := split_part(coalesce(p_name, ''), '/', 3);
  v_household uuid;
begin
  if v_second ~* v_uuid then
    select household_id into v_household
    from public.abeona_trips
    where id = v_second::uuid;
    if v_household is not null then
      return public.abeona_user_in_household(v_household, auth.uid());
    end if;
  elsif v_second = 'bahncards' and v_third ~* v_uuid then
    select c.household_id into v_household
    from public.abeona_bahncards c
    where c.id = v_third::uuid;
    if v_household is not null then
      return public.abeona_user_in_household(v_household, auth.uid());
    end if;
  end if;
  if v_first !~* v_uuid then
    return false;
  end if;
  return public.abeona_user_in_household(v_first::uuid, auth.uid());
end;
$$;

-- Subscriptions whose cancellation deadline is within p_within_days: the
-- caller's household's cards, named by the card.
create or replace function public.abeona_bahncard_deadlines(p_within_days integer default 14)
returns table (
  card_id uuid,
  name text,
  kind smallint,
  travel_class smallint,
  valid_to date,
  notice_weeks smallint,
  deadline date,
  days_left integer
)
language sql
stable
security invoker
set search_path = public
as $$
  with today as (
    select (now() at time zone public.nexus_time_zone())::date as d
  )
  select c.id, c.name, c.kind, c.travel_class, c.valid_to, c.notice_weeks,
         c.valid_to - c.notice_weeks * 7 as deadline,
         (c.valid_to - c.notice_weeks * 7) - today.d as days_left
  from public.abeona_bahncards c
  cross join today
  where c.household_id = public.abeona_current_household()
    and c.subscription
    and today.d <= c.valid_to
    and (c.valid_to - c.notice_weeks * 7) - today.d <= greatest(coalesce(p_within_days, 14), 0)
  order by deadline, c.name nulls last;
$$;

-- As in 20261104130000_abeona_traveller_discounts, without travellers:
-- p_trip.traveller_count (people on the ticket, or null) and
-- p_trip.bahncard_discounts (an array, one discount per card) describe the
-- ticket instead.
create or replace function public.abeona_save_trip(p_trip jsonb, p_legs jsonb)
returns uuid
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_household uuid := public.abeona_ensure_household();
  v_trip_id uuid := nullif(p_trip->>'id', '')::uuid;
  v_source text := coalesce(nullif(p_trip->>'source', ''), 'search');
  v_import boolean;
  v_delay integer := nullif(p_trip->>'arrival_delay_minutes', '')::integer;
  v_leg jsonb;
  v_idx integer := 0;
  v_count integer;
  v_last_ride integer;
  v_first jsonb;
  v_last jsonb;
  v_km double precision;
  v_arrival timestamptz;
  v_travellers smallint := nullif(p_trip->>'traveller_count', '')::smallint;
  v_discounts smallint[] := case
    when jsonb_typeof(p_trip->'bahncard_discounts') = 'array' then
      coalesce((select array_agg(x::smallint order by x::smallint)
                from jsonb_array_elements_text(p_trip->'bahncard_discounts') as x), '{}')
    else '{}'
  end;
begin

  if jsonb_typeof(p_legs) <> 'array' or jsonb_array_length(p_legs) = 0 then
    raise exception 'A trip needs at least one leg.';
  end if;
  if v_delay is not null and (v_delay < 0 or v_delay > 1440) then
    raise exception 'The arrival delay must be between 0 and 1440 minutes.';
  end if;

  v_import := v_source = 'import';
  v_count := jsonb_array_length(p_legs);
  v_first := p_legs->0;
  v_last := p_legs->(v_count - 1);

  -- The last leg that is not a walk carries the real arrival.
  select max(n - 1) into v_last_ride
  from jsonb_array_elements(p_legs) with ordinality as e(leg, n)
  where coalesce(e.leg->>'mode', 'train') <> 'walk';

  if v_trip_id is null then
    insert into public.abeona_trips (
      household_id, created_by, title, source, provider, refresh_token,
      origin_name, destination_name, planned_departure, planned_arrival,
      fare_type, train_binding, travel_class, price_cents,
      traveller_count, bahncard_discounts,
      refreshed_at, finalized_at
    ) values (
      v_household, auth.uid(),
      nullif(btrim(p_trip->>'title'), ''),
      v_source,
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
      v_travellers,
      v_discounts,
      now(),
      case when v_import then now() end
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
    -- Without a route, a distance the source knows (Träwelling's along the
    -- track, a CSV's column) beats the great-circle estimate.
    if v_km is null and nullif(v_leg->>'distance_km', '') is not null then
      v_km := greatest(0, (v_leg->>'distance_km')::double precision);
    end if;
    if v_km is null and coalesce(v_leg->>'mode', 'train') <> 'walk' then
      v_km := 1.1 * public.abeona_haversine_km(
        (v_leg->'from'->>'lat')::double precision, (v_leg->'from'->>'lon')::double precision,
        (v_leg->'to'->>'lat')::double precision, (v_leg->'to'->>'lon')::double precision
      );
    end if;

    v_arrival := (v_leg->'to'->>'current')::timestamptz;
    if v_import and v_delay is not null and v_idx = v_last_ride and v_arrival is null then
      v_arrival := (v_leg->'to'->>'planned')::timestamptz + make_interval(mins => v_delay);
    end if;

    insert into public.abeona_legs (
      trip_id, idx, mode, product, line_name, trip_ref, headsign, operator,
      from_id, from_name, from_lat, from_lon, from_country,
      to_id, to_name, to_lat, to_lon, to_country,
      planned_departure, departure, planned_arrival, arrival,
      planned_departure_platform, departure_platform,
      planned_arrival_platform, arrival_platform,
      cancelled, realtime, stopovers, polyline, distance_km, final
    ) values (
      v_trip_id, v_idx,
      coalesce(v_leg->>'mode', 'train'),
      v_leg->>'product', v_leg->>'line_name', v_leg->>'trip_ref',
      v_leg->>'headsign', v_leg->>'operator',
      v_leg->'from'->>'id', v_leg->'from'->>'name',
      (v_leg->'from'->>'lat')::double precision, (v_leg->'from'->>'lon')::double precision,
      public.abeona_stop_country(v_leg->'from'->>'country', null),
      v_leg->'to'->>'id', v_leg->'to'->>'name',
      (v_leg->'to'->>'lat')::double precision, (v_leg->'to'->>'lon')::double precision,
      public.abeona_stop_country(v_leg->'to'->>'country', null),
      (v_leg->'from'->>'planned')::timestamptz, (v_leg->'from'->>'current')::timestamptz,
      (v_leg->'to'->>'planned')::timestamptz, v_arrival,
      v_leg->'from'->>'planned_platform', v_leg->'from'->>'platform',
      v_leg->'to'->>'planned_platform', v_leg->'to'->>'platform',
      coalesce((v_leg->>'cancelled')::boolean, false),
      coalesce((v_leg->>'realtime')::boolean, false),
      coalesce(v_leg->'stopovers', '[]'::jsonb),
      v_leg->'polyline',
      round(v_km::numeric, 1),
      v_import
    );
    v_idx := v_idx + 1;
  end loop;

  return v_trip_id;
end;
$$;

-- ── Grants ─────────────────────────────────────────────────────────────────

revoke all on function
  public.abeona_ensure_household(),
  public.abeona_invite_to_household(text),
  public.abeona_user_in_household_path(text),
  public.abeona_bahncard_deadlines(integer),
  public.abeona_save_trip(jsonb, jsonb)
from public, anon;
grant execute on function
  public.abeona_ensure_household(),
  public.abeona_invite_to_household(text),
  public.abeona_user_in_household_path(text),
  public.abeona_bahncard_deadlines(integer),
  public.abeona_save_trip(jsonb, jsonb)
to authenticated;
