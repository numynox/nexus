-- Abeona, #38 phase 5: keeping track of compensation claims.
--
-- A claim is one trip's passenger-rights form: whether it is still to be
-- sent, sent, paid or rejected, and what came back. One per trip. It carries
-- no household of its own: access follows the trip, like a BahnCard follows
-- its traveller, so a merge on invite carries claims along.
--
-- Only additions: older APKs keep reading what they read before.

create table if not exists public.abeona_claims (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null unique references public.abeona_trips(id) on delete cascade,
  status text not null default 'open' check (status in ('open', 'claimed', 'paid', 'rejected')),
  -- What was claimed, and once paid what came back.
  amount_cents integer check (amount_cents is null or amount_cents >= 0),
  claimed_at timestamptz,
  paid_at timestamptz,
  note text,
  created_by uuid references auth.users(id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.abeona_claims enable row level security;

drop policy if exists abeona_claims_all on public.abeona_claims;
create policy abeona_claims_all on public.abeona_claims
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

revoke all on table public.abeona_claims from anon;

-- What the claims list shows, for the caller's household, as one object:
--   to_claim   trips with something to claim and no claim sent yet (no row,
--              or status 'open') whose deadline has not passed, soonest
--              deadline first: { trip_id, title, origin_name,
--              destination_name, planned_departure, delay_minutes, verdict,
--              amount_cents, deadline, days_left, status }
--   waiting    claims sent and not answered yet, oldest first (same shape)
--   earned_cents  paid in p_year (by paid_at, local time)
--   paid       how many claims were paid in p_year
-- "Something to claim" is eligible, unknown_price, or below_minimum with a
-- flat rate that may be pooled. Runs as the caller, so RLS scopes it.
create or replace function public.abeona_claims_overview(p_year integer)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with trips as (
    select t.id, t.title, t.origin_name, t.destination_name, t.planned_departure,
      c.status, c.amount_cents as claim_amount,
      public.abeona_compensation(t.id) as comp
    from public.abeona_trips t
    left join public.abeona_claims c on c.trip_id = t.id
    where t.household_id = public.abeona_current_household()
      and t.status = 'planned'
      and t.planned_arrival < now()
      -- Older trips are past any deadline, unless a claim is still waiting.
      and (t.planned_arrival > now() - interval '4 months' or c.status = 'claimed')
  ),
  rows as (
    select tr.*, jsonb_build_object(
      'trip_id', tr.id,
      'title', tr.title,
      'origin_name', tr.origin_name,
      'destination_name', tr.destination_name,
      'planned_departure', tr.planned_departure,
      'delay_minutes', tr.comp->'delay_minutes',
      'verdict', tr.comp->>'verdict',
      'amount_cents', coalesce(to_jsonb(tr.claim_amount), tr.comp->'amount_cents'),
      'deadline', tr.comp->>'deadline',
      'days_left', ((tr.comp->>'deadline')::date - (now() at time zone public.nexus_time_zone())::date),
      'status', coalesce(tr.status, 'open')
    ) as row
    from trips tr
  )
  select jsonb_build_object(
    'to_claim', coalesce((
      select jsonb_agg(r.row order by r.comp->>'deadline', r.planned_departure)
      from rows r
      where coalesce(r.status, 'open') = 'open'
        and not (r.comp->>'deadline_passed')::boolean
        and (r.comp->>'verdict' in ('eligible', 'unknown_price')
             or (r.comp->>'verdict' = 'below_minimum' and (r.comp->>'poolable')::boolean))
    ), '[]'::jsonb),
    'waiting', coalesce((
      select jsonb_agg(r.row order by r.planned_departure)
      from rows r where r.status = 'claimed'
    ), '[]'::jsonb),
    'earned_cents', coalesce((
      select sum(c.amount_cents)
      from public.abeona_claims c
      join public.abeona_trips t on t.id = c.trip_id
      where t.household_id = public.abeona_current_household()
        and c.status = 'paid'
        and extract(year from c.paid_at at time zone public.nexus_time_zone()) = p_year
    ), 0),
    'paid', (
      select count(*)
      from public.abeona_claims c
      join public.abeona_trips t on t.id = c.trip_id
      where t.household_id = public.abeona_current_household()
        and c.status = 'paid'
        and extract(year from c.paid_at at time zone public.nexus_time_zone()) = p_year
    )
  );
$$;

revoke all on function public.abeona_claims_overview(integer) from public, anon;
grant execute on function public.abeona_claims_overview(integer) to authenticated;
