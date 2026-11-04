-- Abeona: the BahnCard discount per traveller on a trip, not per trip. One
-- ticket can carry several travellers with different cards ("3 Personen,
-- 1 BC25, 1 BC50"), and each needs the card their own share was bought with.
--
-- Only additions, and older APKs keep working: they read and write
-- abeona_trips.bahncard_discount, which stays. Writing it the old way (the
-- trip page's direct update, abeona_save_trip without per-traveller values,
-- abeona_set_trip_travellers adding someone) gives every traveller that
-- discount. New clients write per traveller, and the trip's own column then
-- holds the discount when all of them share it, else null.

-- ── The column ─────────────────────────────────────────────────────────────

alter table public.abeona_trip_travellers
  add column if not exists bahncard_discount smallint
    check (bahncard_discount is null or bahncard_discount in (25, 50, 100));

comment on column public.abeona_trip_travellers.bahncard_discount is
  'The BahnCard discount this traveller''s share of the fare was bought with (25, 50, 100) or null for none. A BahnCard 100 fare (abeona_trips.fare_type) needs nothing here.';

comment on column public.abeona_trips.bahncard_discount is
  'Kept for older clients: the discount every traveller on the trip shares, else null. Per traveller in abeona_trip_travellers.bahncard_discount.';

-- Trips saved so far had one discount for everybody on them.
update public.abeona_trip_travellers tt
set bahncard_discount = tr.bahncard_discount
from public.abeona_trips tr
where tr.id = tt.trip_id
  and tr.bahncard_discount is not null
  and tt.bahncard_discount is null;

-- ── Older clients' writes ──────────────────────────────────────────────────

-- The trip page of an older APK updates abeona_trips.bahncard_discount
-- directly: that means everybody on the trip. New clients never write the
-- column directly; abeona_save_trip_travellers sets it first and the
-- per-traveller values after, so this trigger never undoes them.
create or replace function public.abeona_trips_spread_discount()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  update public.abeona_trip_travellers
  set bahncard_discount = new.bahncard_discount
  where trip_id = new.id;
  return new;
end;
$$;

drop trigger if exists abeona_trips_spread_discount on public.abeona_trips;
create trigger abeona_trips_spread_discount
  after update of bahncard_discount on public.abeona_trips
  for each row
  when (old.bahncard_discount is distinct from new.bahncard_discount)
  execute function public.abeona_trips_spread_discount();

-- The one discount every rider shares, else null: what abeona_trips keeps for
-- older clients. p_riders: [{"id": …, "bahncard_discount": 25 | 50 | 100 | null}].
create or replace function public.abeona_uniform_discount(p_riders jsonb)
returns smallint
language sql
immutable
set search_path = public
as $$
  select case when count(distinct coalesce(d, 0)) = 1 then max(d) end
  from (
    select nullif(r->>'bahncard_discount', '')::smallint as d
    from jsonb_array_elements(coalesce(p_riders, '[]'::jsonb)) as r
  ) x;
$$;

-- ── Saving a trip ──────────────────────────────────────────────────────────

-- As in 20261104090000_abeona_history, plus p_trip.travellers: who travels,
-- each with their own discount ([{"id": …, "bahncard_discount": …}]). It wins
-- over p_trip.traveller_ids; without it, everybody gets p_trip.bahncard_discount
-- as before. The trip's own bahncard_discount is the shared one (or the
-- given one, if p_trip names it).
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
  v_riders jsonb := case when jsonb_typeof(p_trip->'travellers') = 'array' then p_trip->'travellers' end;
  v_discount smallint := nullif(p_trip->>'bahncard_discount', '')::smallint;
begin
  if v_riders is not null and not (p_trip ? 'bahncard_discount') then
    v_discount := public.abeona_uniform_discount(v_riders);
  end if;

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
      fare_type, train_binding, travel_class, price_cents, bahncard_discount,
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
      v_discount,
      now(),
      case when v_import then now() end
    )
    returning id into v_trip_id;

    if v_riders is not null then
      insert into public.abeona_trip_travellers (trip_id, traveller_id, bahncard_discount)
      select v_trip_id, t.id, nullif(r->>'bahncard_discount', '')::smallint
      from jsonb_array_elements(v_riders) as r
      join public.abeona_travellers t on t.id = (r->>'id')::uuid
      where t.household_id = v_household
      on conflict do nothing;
    elsif jsonb_typeof(p_trip->'traveller_ids') = 'array' then
      insert into public.abeona_trip_travellers (trip_id, traveller_id, bahncard_discount)
      select v_trip_id, t.id, v_discount
      from public.abeona_travellers t
      where t.household_id = v_household
        and t.id in (select x::uuid from jsonb_array_elements_text(p_trip->'traveller_ids') as x)
      on conflict do nothing;
    else
      insert into public.abeona_trip_travellers (trip_id, traveller_id, bahncard_discount)
      select v_trip_id, t.id, v_discount
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

-- ── Who travels, with their discounts ──────────────────────────────────────

-- Replaces who travels on a trip and with which discount, in one statement.
-- p_travellers: [{"id": …, "bahncard_discount": 25 | 50 | 100 | null}]. Runs as
-- the caller, so RLS decides: only the household's trips and travellers.
create or replace function public.abeona_save_trip_travellers(p_trip_id uuid, p_travellers jsonb)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if jsonb_typeof(p_travellers) <> 'array' then
    raise exception 'p_travellers must be an array.';
  end if;
  -- First the trip's shared value (its trigger spreads it), then each
  -- traveller's own, so the trigger never overrides them.
  update public.abeona_trips
  set bahncard_discount = public.abeona_uniform_discount(p_travellers),
      updated_at = now()
  where id = p_trip_id;
  if not found then
    raise exception 'Trip not found.';
  end if;

  delete from public.abeona_trip_travellers
  where trip_id = p_trip_id
    and traveller_id not in (select (r->>'id')::uuid from jsonb_array_elements(p_travellers) as r);

  insert into public.abeona_trip_travellers (trip_id, traveller_id, bahncard_discount)
  select p_trip_id, (r->>'id')::uuid, nullif(r->>'bahncard_discount', '')::smallint
  from jsonb_array_elements(p_travellers) as r
  on conflict (trip_id, traveller_id) do update set bahncard_discount = excluded.bahncard_discount;
end;
$$;

-- As in 20261103110000_abeona_bahncards, for older clients: who is added
-- gets the trip's shared discount.
create or replace function public.abeona_set_trip_travellers(p_trip_id uuid, p_traveller_ids uuid[])
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_discount smallint;
begin
  select bahncard_discount into v_discount from public.abeona_trips where id = p_trip_id;
  if not found then
    raise exception 'Trip not found.';
  end if;

  delete from public.abeona_trip_travellers
  where trip_id = p_trip_id
    and not (traveller_id = any (coalesce(p_traveller_ids, '{}')));

  insert into public.abeona_trip_travellers (trip_id, traveller_id, bahncard_discount)
  select p_trip_id, x, v_discount
  from unnest(coalesce(p_traveller_ids, '{}')) as x
  on conflict do nothing;
end;
$$;

-- ── Trip warnings ──────────────────────────────────────────────────────────

-- As in 20261103110000_abeona_bahncards, but each traveller is checked
-- against their own discount: a traveller without one needs no card. A
-- BahnCard 100 fare still needs one for everybody, and "nobody is assigned"
-- still uses the trip's discount.
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
           tr.fare_type = 'bahncard100' as all_need_100,
           case when tr.fare_type = 'bahncard100' then 100 else tr.bahncard_discount end as required,
           (tr.planned_departure at time zone public.nexus_time_zone())::date as dep,
           (tr.planned_arrival at time zone public.nexus_time_zone())::date as arr
    from public.abeona_trips tr
    where tr.id = p_trip_id
      and tr.status = 'planned'
  ),
  -- Each traveller needs the card their own share of the fare was bought
  -- with; a BahnCard 100 fare needs one for everybody.
  riders as (
    select tr.id, tr.travel_class, tr.dep, tr.arr,
           case when tr.all_need_100 then 100 else tt.bahncard_discount end as required,
           t.id as traveller_id, t.name
    from trip tr
    join public.abeona_trip_travellers tt on tt.trip_id = tr.id
    join public.abeona_travellers t on t.id = tt.traveller_id
  ),
  needed as (
    select * from trip
    where required is not null
      and not exists (select 1 from public.abeona_trip_travellers tt where tt.trip_id = trip.id)
  ),
  -- For each rider, their best card of a sufficient kind: one valid for the
  -- whole trip in the right class first, then valid in another class, then
  -- the one closest to the trip.
  best as (
    select distinct on (r.traveller_id)
           r.traveller_id, r.name, r.required, r.travel_class as trip_class, r.dep, r.arr,
           c.id as card_id, c.kind as card_kind, c.travel_class as card_class,
           c.valid_from, c.valid_to
    from (select * from riders where required is not null) r
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

revoke all on function public.abeona_trips_spread_discount() from public, anon, authenticated;
revoke all on function
  public.abeona_uniform_discount(jsonb),
  public.abeona_save_trip(jsonb, jsonb),
  public.abeona_save_trip_travellers(uuid, jsonb),
  public.abeona_set_trip_travellers(uuid, uuid[]),
  public.abeona_trip_warnings(uuid)
from public, anon;
grant execute on function
  public.abeona_uniform_discount(jsonb),
  public.abeona_save_trip(jsonb, jsonb),
  public.abeona_save_trip_travellers(uuid, jsonb),
  public.abeona_set_trip_travellers(uuid, uuid[]),
  public.abeona_trip_warnings(uuid)
to authenticated;
