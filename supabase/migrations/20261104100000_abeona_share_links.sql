-- Abeona, #38 phase 7: share a trip with someone outside the household.
--
-- A share link is an unguessable token for one trip, in one of two kinds:
--   ics     a calendar file, one event per ride, live times when known
--   status  a small read-only page: the current ride, the expected arrival
--           and the delay — for whoever picks the traveller up
-- The token is the whole credential. It is made here (never by a client),
-- expires on its own, can be revoked by deleting the row, and opens only
-- what abeona_shared_trip() returns. The abeona-public Edge Function looks
-- tokens up with the service role; nothing else reads them without a session
-- (decisions/2026-11-04-abeona-share-links.md).
--
-- Only additions: older APKs never see the table.

create table if not exists public.abeona_share_links (
  -- 144 random bits, hex. Overwritten by the trigger whatever the insert says.
  token text primary key default encode(extensions.gen_random_bytes(18), 'hex'),
  trip_id uuid not null references public.abeona_trips(id) on delete cascade,
  kind text not null check (kind in ('ics', 'status')),
  expires_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  -- One live link per trip and kind: revoking and sharing again gives a new token.
  unique (trip_id, kind)
);

alter table public.abeona_share_links enable row level security;

-- Members of the trip's household see, make and revoke its links. There is
-- no update: a link is replaced, never edited.
drop policy if exists abeona_share_links_select on public.abeona_share_links;
create policy abeona_share_links_select on public.abeona_share_links
  for select to authenticated
  using (exists (
    select 1 from public.abeona_trips t
    where t.id = trip_id and public.abeona_user_in_household(t.household_id, auth.uid())
  ));

drop policy if exists abeona_share_links_insert on public.abeona_share_links;
create policy abeona_share_links_insert on public.abeona_share_links
  for insert to authenticated
  with check (exists (
    select 1 from public.abeona_trips t
    where t.id = trip_id and public.abeona_user_in_household(t.household_id, auth.uid())
  ));

drop policy if exists abeona_share_links_delete on public.abeona_share_links;
create policy abeona_share_links_delete on public.abeona_share_links
  for delete to authenticated
  using (exists (
    select 1 from public.abeona_trips t
    where t.id = trip_id and public.abeona_user_in_household(t.household_id, auth.uid())
  ));

revoke all on public.abeona_share_links from anon;
revoke update on public.abeona_share_links from authenticated;

-- The token, the creator and the expiry are the database's to set:
--   status  six hours after the trip's expected arrival (the last ride's
--           live arrival, else its planned one) — it is for the pickup
--   ics     30 days after the planned arrival — calendars refetch for a while
create or replace function public.abeona_share_links_defaults()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  v_arrival timestamptz;
begin
  new.token := encode(extensions.gen_random_bytes(18), 'hex');
  new.created_by := coalesce(auth.uid(), new.created_by);
  new.created_at := now();

  select greatest(t.planned_arrival, coalesce((
    select coalesce(l.arrival, l.planned_arrival)
    from public.abeona_legs l
    where l.trip_id = t.id and l.mode <> 'walk'
    order by l.idx desc limit 1
  ), t.planned_arrival))
  into v_arrival
  from public.abeona_trips t
  where t.id = new.trip_id;

  new.expires_at := case new.kind
    when 'status' then coalesce(v_arrival, now()) + interval '6 hours'
    else coalesce(v_arrival, now()) + interval '30 days'
  end;
  return new;
end;
$$;

drop trigger if exists abeona_share_links_defaults on public.abeona_share_links;
create trigger abeona_share_links_defaults
  before insert on public.abeona_share_links
  for each row execute function public.abeona_share_links_defaults();

-- What a token opens, for the abeona-public Edge Function only (service
-- role): null for an unknown or expired token. Deliberately little — the
-- route, the times, platforms and whether a ride is cancelled. No title,
-- travellers, ticket, price, household or account. `refresh` is what a live
-- refresh needs; the function uses it and never passes it on.
create or replace function public.abeona_shared_trip(p_token text)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  select jsonb_build_object(
    'kind', s.kind,
    'expires_at', s.expires_at,
    'trip', jsonb_build_object(
      'uid', md5(t.id::text),
      'origin', t.origin_name,
      'destination', t.destination_name,
      'planned_departure', t.planned_departure,
      'planned_arrival', t.planned_arrival,
      'cancelled', t.status = 'cancelled',
      'final', t.finalized_at is not null,
      'refreshed_at', t.refreshed_at
    ),
    'legs', coalesce((
      select jsonb_agg(jsonb_build_object(
        'uid', md5(t.id::text || ':' || l.idx),
        'mode', l.mode,
        'product', l.product,
        'line_name', l.line_name,
        'headsign', l.headsign,
        'from', jsonb_build_object(
          'name', l.from_name, 'planned', l.planned_departure, 'current', l.departure,
          'planned_platform', l.planned_departure_platform, 'platform', l.departure_platform),
        'to', jsonb_build_object(
          'name', l.to_name, 'planned', l.planned_arrival, 'current', l.arrival,
          'planned_platform', l.planned_arrival_platform, 'platform', l.arrival_platform),
        'cancelled', l.cancelled,
        'realtime', l.realtime,
        'final', l.final
      ) order by l.idx)
      from public.abeona_legs l where l.trip_id = t.id
    ), '[]'::jsonb),
    'refresh', jsonb_build_object(
      'refresh_token', t.refresh_token,
      'source', t.source,
      'legs', coalesce((
        select jsonb_agg(jsonb_build_object(
          'trip_ref', l.trip_ref, 'from_id', l.from_id, 'to_id', l.to_id,
          'from_name', l.from_name, 'to_name', l.to_name
        ) order by l.idx)
        from public.abeona_legs l where l.trip_id = t.id
      ), '[]'::jsonb)
    )
  )
  from public.abeona_share_links s
  join public.abeona_trips t on t.id = s.trip_id
  where s.token = p_token
    and p_token ~ '^[0-9a-f]{36}$'
    and s.expires_at > now();
$$;

revoke all on function public.abeona_shared_trip(text) from public, anon, authenticated;
grant execute on function public.abeona_shared_trip(text) to service_role;
revoke all on function public.abeona_share_links_defaults() from public, anon, authenticated;
