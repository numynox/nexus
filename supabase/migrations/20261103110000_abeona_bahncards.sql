-- Abeona, phase 3: BahnCards, their cancellation deadlines, the BahnCard
-- discount on a trip, and the warnings both clients show for it.
--
-- Only additions: older APKs keep reading what they read before.
--
-- A BahnCard belongs to a traveller, not to an account, and carries no
-- household of its own: it is in whichever household its traveller is in. The
-- merge on invite moves travellers, so their cards follow without a change to
-- abeona_invite_to_household(). Card files live in the abeona-tickets bucket
-- under <household_id>/bahncards/<card_id>/<file>, and access to them follows
-- the card (decisions/2026-11-03-abeona-bahncard-files-follow-the-card.md).

-- ── Tables ─────────────────────────────────────────────────────────────────

create table if not exists public.abeona_bahncards (
  id uuid primary key default gen_random_uuid(),
  traveller_id uuid not null references public.abeona_travellers(id) on delete cascade,
  kind smallint not null check (kind in (25, 50, 100)),
  travel_class smallint not null default 2 check (travel_class in (1, 2)),
  valid_from date not null,
  valid_to date not null,
  card_number text,
  -- A subscription renews unless it is cancelled notice_weeks before valid_to.
  -- DB shortened the notice period from six weeks to four; it stays editable.
  subscription boolean not null default true,
  notice_weeks smallint not null default 4 check (notice_weeks between 0 and 26),
  -- Storage object path in the abeona-tickets bucket:
  -- <household_id>/bahncards/<card_id>/<file>
  file_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (valid_to >= valid_from)
);

create index if not exists abeona_bahncards_traveller_idx
  on public.abeona_bahncards (traveller_id);

-- The BahnCard discount the trip's fare was bought with. A BahnCard 100 is a
-- fare type of its own ('bahncard100') and needs nothing here.
alter table public.abeona_trips
  add column if not exists bahncard_discount smallint
    check (bahncard_discount is null or bahncard_discount in (25, 50, 100));

-- ── Row level security ─────────────────────────────────────────────────────

alter table public.abeona_bahncards enable row level security;

drop policy if exists abeona_bahncards_all on public.abeona_bahncards;
create policy abeona_bahncards_all on public.abeona_bahncards
  for all to authenticated
  using (
    exists (
      select 1 from public.abeona_travellers t
      where t.id = traveller_id
        and public.abeona_user_in_household(t.household_id, auth.uid())
    )
  )
  with check (
    exists (
      select 1 from public.abeona_travellers t
      where t.id = traveller_id
        and public.abeona_user_in_household(t.household_id, auth.uid())
    )
  );

-- ── Storage paths ──────────────────────────────────────────────────────────

-- Paths are <household_id>/<trip_id>/<file> for tickets and
-- <household_id>/bahncards/<card_id>/<file> for BahnCards. Access follows the
-- trip or the card when the path names one that exists, so a file stays
-- readable after a merge on invite moved its trip or its card's traveller to
-- another household. Otherwise (a card's file uploaded before its row, or no
-- trip at all) it follows the household in the first segment. Anything that
-- is not a uuid there is simply not accessible.
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
    select t.household_id into v_household
    from public.abeona_bahncards c
    join public.abeona_travellers t on t.id = c.traveller_id
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

-- ── Saving a trip ──────────────────────────────────────────────────────────

-- As in 20261102120000_abeona_foundation, plus:
-- - p_trip.bahncard_discount (25, 50, 100 or null) on a new trip;
-- - p_trip.traveller_ids (an array of traveller ids) on a new trip. Without
--   it the trip gets the caller's own traveller, so a trip always has someone
--   on it; an older APK that does not send it gets the same default.
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
      fare_type, train_binding, travel_class, price_cents, bahncard_discount,
      refreshed_at
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
      (p_trip->>'bahncard_discount')::smallint,
      now()
    )
    returning id into v_trip_id;

    if jsonb_typeof(p_trip->'traveller_ids') = 'array' then
      insert into public.abeona_trip_travellers (trip_id, traveller_id)
      select v_trip_id, t.id
      from public.abeona_travellers t
      where t.household_id = v_household
        and t.id in (select x::uuid from jsonb_array_elements_text(p_trip->'traveller_ids') as x)
      on conflict do nothing;
    else
      insert into public.abeona_trip_travellers (trip_id, traveller_id)
      select v_trip_id, t.id
      from public.abeona_travellers t
      where t.household_id = v_household
        and t.user_id = auth.uid()
      on conflict do nothing;
    end if;
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

-- Replaces who travels on a trip in one statement. Runs as the caller, so RLS
-- decides: only the household's trips, only the household's travellers.
create or replace function public.abeona_set_trip_travellers(p_trip_id uuid, p_traveller_ids uuid[])
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if not exists (select 1 from public.abeona_trips where id = p_trip_id) then
    raise exception 'Trip not found.';
  end if;

  delete from public.abeona_trip_travellers
  where trip_id = p_trip_id
    and not (traveller_id = any (coalesce(p_traveller_ids, '{}')));

  insert into public.abeona_trip_travellers (trip_id, traveller_id)
  select p_trip_id, x
  from unnest(coalesce(p_traveller_ids, '{}')) as x
  on conflict do nothing;
end;
$$;

-- ── Cancellation deadlines ─────────────────────────────────────────────────

-- The caller's household's subscription BahnCards whose cancellation deadline
-- (valid_to minus the notice period) is at most p_within_days away, or has
-- passed while the card still runs. Soonest first. days_left is negative once
-- the deadline has passed. "Today" is the date in nexus_time_zone().
create or replace function public.abeona_bahncard_deadlines(p_within_days integer default 14)
returns table (
  card_id uuid,
  traveller_id uuid,
  traveller_name text,
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
  select c.id, t.id, t.name, c.kind, c.travel_class, c.valid_to, c.notice_weeks,
         c.valid_to - c.notice_weeks * 7 as deadline,
         (c.valid_to - c.notice_weeks * 7) - today.d as days_left
  from public.abeona_bahncards c
  join public.abeona_travellers t on t.id = c.traveller_id
  cross join today
  where t.household_id = public.abeona_current_household()
    and c.subscription
    and today.d <= c.valid_to
    and (c.valid_to - c.notice_weeks * 7) - today.d <= greatest(coalesce(p_within_days, 14), 0)
  order by deadline, t.name;
$$;

-- ── Trip warnings ──────────────────────────────────────────────────────────

-- What is wrong with the BahnCards for a trip whose fare was bought with a
-- BahnCard discount (bahncard_discount) or is a BahnCard 100 (fare_type).
-- Every traveller on the trip needs a card of at least that kind, for the
-- trip's class or better, valid from the departure date through the arrival
-- date (both in nexus_time_zone()). A BahnCard 50 covers a BahnCard 25 fare:
-- DB sells Sparpreise to its holders at 25 %.
--
-- One row per problem, in the order of the travellers' names, with an English
-- message both clients show as is:
--   no_travellers  nobody is assigned to the trip
--   no_card        a traveller has no card of the kind
--   expired        a traveller's card of the kind ends before the trip does
--   not_yet_valid  a traveller's card of the kind starts after the trip does
--   class          a traveller's card is valid but for 2nd class on a 1st class ticket
-- Runs as the caller: a trip outside the household yields nothing.
create or replace function public.abeona_trip_warnings(p_trip_id uuid)
returns table (
  kind text,
  traveller_id uuid,
  traveller_name text,
  card_id uuid,
  message text
)
language sql
stable
security invoker
set search_path = public
as $$
  with trip as (
    select tr.id,
           tr.travel_class,
           case when tr.fare_type = 'bahncard100' then 100 else tr.bahncard_discount end as required,
           (tr.planned_departure at time zone public.nexus_time_zone())::date as dep,
           (tr.planned_arrival at time zone public.nexus_time_zone())::date as arr
    from public.abeona_trips tr
    where tr.id = p_trip_id
      and tr.status = 'planned'
  ),
  needed as (
    select * from trip where required is not null
  ),
  riders as (
    select n.*, t.id as traveller_id, t.name
    from needed n
    join public.abeona_trip_travellers tt on tt.trip_id = n.id
    join public.abeona_travellers t on t.id = tt.traveller_id
  ),
  -- For each rider, their best card of a sufficient kind: one valid for the
  -- whole trip in the right class first, then valid in another class, then
  -- the one closest to the trip.
  best as (
    select distinct on (r.traveller_id)
           r.traveller_id, r.name, r.required, r.travel_class as trip_class, r.dep, r.arr,
           c.id as card_id, c.kind as card_kind, c.travel_class as card_class,
           c.valid_from, c.valid_to
    from riders r
    left join public.abeona_bahncards c
      on c.traveller_id = r.traveller_id and c.kind >= r.required
    order by r.traveller_id,
             (c.valid_from <= r.dep and c.valid_to >= r.arr and c.travel_class <= r.travel_class) desc nulls last,
             (c.valid_from <= r.dep and c.valid_to >= r.arr) desc nulls last,
             least(abs(c.valid_to - r.arr), abs(c.valid_from - r.dep)) nulls last
  ),
  problems as (
    select 'no_travellers'::text as kind, null::uuid as traveller_id, null::text as traveller_name,
           null::uuid as card_id,
           format('This fare needs a BahnCard %s, but nobody is assigned to the trip.', n.required) as message,
           0 as rank
    from needed n
    where not exists (select 1 from riders)
    union all
    select case
             when b.card_id is null then 'no_card'
             when b.valid_to < b.arr then 'expired'
             when b.valid_from > b.dep then 'not_yet_valid'
             else 'class'
           end,
           b.traveller_id, b.name, b.card_id,
           case
             when b.card_id is null then
               format('%s has no BahnCard %s.', b.name, b.required)
             when b.valid_to < b.arr then
               format('%s''s BahnCard %s expires on %s, before this trip.',
                      b.name, b.card_kind, to_char(b.valid_to, 'FMDD Mon YYYY'))
             when b.valid_from > b.dep then
               format('%s''s BahnCard %s is only valid from %s, after this trip starts.',
                      b.name, b.card_kind, to_char(b.valid_from, 'FMDD Mon YYYY'))
             else
               format('%s''s BahnCard %s is for 2nd class, but this ticket is 1st class.',
                      b.name, b.card_kind)
           end,
           1
    from best b
    where b.card_id is null
       or b.valid_to < b.arr
       or b.valid_from > b.dep
       or b.card_class > b.trip_class
  )
  select p.kind, p.traveller_id, p.traveller_name, p.card_id, p.message
  from problems p
  order by p.rank, p.traveller_name;
$$;

-- ── Grants ─────────────────────────────────────────────────────────────────

-- Supabase grants anon EXECUTE on new functions directly, which a revoke from
-- public does not reach (20261102140000_abeona_function_grants).
revoke all on function
  public.abeona_set_trip_travellers(uuid, uuid[]),
  public.abeona_bahncard_deadlines(integer),
  public.abeona_trip_warnings(uuid)
from public, anon;

grant execute on function
  public.abeona_set_trip_travellers(uuid, uuid[]),
  public.abeona_bahncard_deadlines(integer),
  public.abeona_trip_warnings(uuid)
to authenticated;

-- create or replace keeps the grants of the two replaced functions; restate
-- them so this file reads correctly on its own.
revoke all on function
  public.abeona_user_in_household_path(text),
  public.abeona_save_trip(jsonb, jsonb)
from public, anon;

grant execute on function
  public.abeona_user_in_household_path(text),
  public.abeona_save_trip(jsonb, jsonb)
to authenticated;
