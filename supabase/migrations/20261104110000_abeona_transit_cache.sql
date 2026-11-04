-- Abeona: a short-lived cache of Transitous answers, and two more prune steps
-- in the nightly maintenance.
--
-- abeona-transit (and abeona-public, abeona-refresh-trips through the same
-- module) keep each Transitous response here under a key derived from the
-- request, with the service role, and reuse it while it is fresh: a station
-- search for a day, a journey search for two minutes, a refresh for one.
-- Two people of a household watching the same trip, a status page reloaded
-- by whoever waits at the station, or a calendar refetching every few
-- minutes then cost Transitous one request instead of many
-- (decisions/2026-11-02-abeona-transit-providers.md).
--
-- Nobody but the service role reads or writes the table: RLS is on and there
-- are no policies.

create table if not exists public.abeona_transit_cache (
  -- sha-256 of the provider request (path and sorted parameters), hex.
  key text primary key,
  payload jsonb not null,
  fetched_at timestamptz not null default now()
);

alter table public.abeona_transit_cache enable row level security;
revoke all on public.abeona_transit_cache from anon, authenticated;

create index if not exists abeona_transit_cache_fetched_at_idx
  on public.abeona_transit_cache (fetched_at);

-- ── Prune steps ────────────────────────────────────────────────────────────

-- The longest time-to-live is a day (station search), so anything older than
-- p_keep_hours is dead weight.
create or replace function public.prune_abeona_transit_cache(p_keep_hours integer default 48)
returns integer
language plpgsql
set search_path = public
as $$
declare
  v_rows integer := 0;
begin
  delete from public.abeona_transit_cache
  where fetched_at < now() - make_interval(hours => p_keep_hours);
  get diagnostics v_rows = row_count;
  return v_rows;
end;
$$;

comment on function public.prune_abeona_transit_cache(integer) is
  'Deletes cached Transitous answers older than p_keep_hours; none is used after a day.';

-- An expired share link opens nothing already; this only tidies the table.
create or replace function public.prune_abeona_share_links(p_keep_days integer default 7)
returns integer
language plpgsql
set search_path = public
as $$
declare
  v_rows integer := 0;
begin
  delete from public.abeona_share_links
  where expires_at < now() - make_interval(days => p_keep_days);
  get diagnostics v_rows = row_count;
  return v_rows;
end;
$$;

comment on function public.prune_abeona_share_links(integer) is
  'Deletes Abeona share links that expired more than p_keep_days ago.';

-- ── The nightly entry point, two steps longer ──────────────────────────────

-- As in 20260905091000_maintenance_jobs, plus the two Abeona steps, each
-- isolated like the others.
create or replace function public.run_nexus_maintenance()
returns jsonb
language plpgsql
set search_path = public
as $$
declare
  v_result jsonb := '{}'::jsonb;
  v_count integer;
begin
  -- Order matters: aggregate before pruning what was aggregated.
  begin
    v_count := public.rollup_fuel_prices_daily();
    v_result := v_result || jsonb_build_object('fuel_prices_daily_rows', v_count);
  exception when others then
    v_result := v_result || jsonb_build_object('fuel_prices_daily_error', sqlerrm);
  end;

  begin
    v_count := public.prune_fuel_prices_raw();
    v_result := v_result || jsonb_build_object('fuel_prices_deleted', v_count);
  exception when others then
    v_result := v_result || jsonb_build_object('fuel_prices_error', sqlerrm);
  end;

  begin
    v_count := public.prune_articles_unread();
    v_result := v_result || jsonb_build_object('articles_deleted', v_count);
  exception when others then
    v_result := v_result || jsonb_build_object('articles_error', sqlerrm);
  end;

  begin
    v_count := public.prune_net_http_response();
    v_result := v_result || jsonb_build_object('net_http_response_deleted', v_count);
  exception when others then
    v_result := v_result || jsonb_build_object('net_http_response_error', sqlerrm);
  end;

  begin
    v_count := public.prune_cron_job_run_details();
    v_result := v_result || jsonb_build_object('cron_job_run_details_deleted', v_count);
  exception when others then
    v_result := v_result || jsonb_build_object('cron_job_run_details_error', sqlerrm);
  end;

  begin
    v_count := public.prune_abeona_transit_cache();
    v_result := v_result || jsonb_build_object('abeona_transit_cache_deleted', v_count);
  exception when others then
    v_result := v_result || jsonb_build_object('abeona_transit_cache_error', sqlerrm);
  end;

  begin
    v_count := public.prune_abeona_share_links();
    v_result := v_result || jsonb_build_object('abeona_share_links_deleted', v_count);
  exception when others then
    v_result := v_result || jsonb_build_object('abeona_share_links_error', sqlerrm);
  end;

  raise notice 'nexus maintenance: %', v_result;
  return v_result;
end;
$$;

comment on function public.run_nexus_maintenance() is
  'Daily maintenance: roll up fuel prices, then prune raw prices, unread articles, pg_net responses, pg_cron history, Abeona''s transit cache and expired share links. Each step is isolated; the result reports rows touched or the error per step.';

revoke all on function public.prune_abeona_transit_cache(integer) from public, anon, authenticated;
revoke all on function public.prune_abeona_share_links(integer) from public, anon, authenticated;
revoke all on function public.run_nexus_maintenance() from public, anon, authenticated;
grant execute on function public.prune_abeona_transit_cache(integer) to service_role;
grant execute on function public.prune_abeona_share_links(integer) to service_role;
grant execute on function public.run_nexus_maintenance() to service_role;
