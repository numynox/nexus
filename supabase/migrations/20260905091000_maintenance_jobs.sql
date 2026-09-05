-- Scheduled maintenance: roll up fuel prices, then prune what is no longer read.
--
-- Six months of production produced 170 MB of a 500 MB free tier, of which
-- 43% was raw fuel prices and 22% was pg_net's log of the cron jobs' own HTTP
-- responses — which nothing reads, because both invoke wrappers are
-- fire-and-forget.
--
-- Every prune here is guarded so that it can only remove data that is either
-- already aggregated or provably unreferenced. Each step is also wrapped
-- independently in run_nexus_maintenance(), so a permissions problem on one
-- table cannot stop the others from running.

-- ---------------------------------------------------------------------------
-- Raw fuel prices: keep 21 days, and only what the rollup already covers
-- ---------------------------------------------------------------------------
create or replace function public.prune_fuel_prices_raw(p_keep_days integer default 21)
returns integer
language plpgsql
set search_path = public
as $$
declare
  v_tz text := public.nexus_time_zone();
  v_rows integer := 0;
begin
  -- A raw row is deletable exactly when its aggregate exists: same station,
  -- same fuel type, same Berlin day. Checking "older than the last rolled-up
  -- day" would be weaker — it would delete a backdated reading for a day the
  -- rollup never actually covered.
  delete from public.fuel_prices fp
  where fp.checked_at < (now() - make_interval(days => p_keep_days))
    and exists (
      select 1
      from public.fuel_prices_daily d
      where d.station_id = fp.station_id
        and d.fuel_type = fp.fuel_type
        and d.day = (fp.checked_at at time zone v_tz)::date
    );

  get diagnostics v_rows = row_count;
  return v_rows;
end;
$$;

comment on function public.prune_fuel_prices_raw(integer) is
  'Deletes raw fuel_prices older than p_keep_days, but only rows whose (station, fuel type, Berlin day) already exists in fuel_prices_daily.';

-- ---------------------------------------------------------------------------
-- Articles: delete old ones nobody read
-- ---------------------------------------------------------------------------
-- article_reads.article_id cascades on delete, so anything ever marked read is
-- excluded: the read history and the statistics built on it stay intact.
create or replace function public.prune_articles_unread(p_keep_days integer default 90)
returns integer
language plpgsql
set search_path = public
as $$
declare
  v_rows integer := 0;
begin
  delete from public.articles a
  where coalesce(a.published_at, a.created_at) < (now() - make_interval(days => p_keep_days))
    and not exists (
      select 1 from public.article_reads r where r.article_id = a.id
    );

  get diagnostics v_rows = row_count;
  return v_rows;
end;
$$;

comment on function public.prune_articles_unread(integer) is
  'Deletes articles older than p_keep_days that were never read. Read articles are kept so article_reads and the statistics survive.';

-- ---------------------------------------------------------------------------
-- pg_net response log
-- ---------------------------------------------------------------------------
create or replace function public.prune_net_http_response(p_keep_hours integer default 24)
returns integer
language plpgsql
set search_path = public
as $$
declare
  v_rows integer := 0;
begin
  delete from net._http_response r
  where r.created < (now() - make_interval(hours => p_keep_hours));

  get diagnostics v_rows = row_count;
  return v_rows;
end;
$$;

comment on function public.prune_net_http_response(integer) is
  'Deletes pg_net HTTP response rows older than p_keep_hours. Nothing reads them: the invoke wrappers are fire-and-forget.';

-- ---------------------------------------------------------------------------
-- pg_cron run log
-- ---------------------------------------------------------------------------
create or replace function public.prune_cron_job_run_details(p_keep_days integer default 7)
returns integer
language plpgsql
set search_path = public
as $$
declare
  v_rows integer := 0;
begin
  delete from cron.job_run_details d
  where coalesce(d.end_time, d.start_time) < (now() - make_interval(days => p_keep_days));

  get diagnostics v_rows = row_count;
  return v_rows;
end;
$$;

comment on function public.prune_cron_job_run_details(integer) is
  'Trims pg_cron run history. Keep enough to debug a failing job, not six months of it.';

-- ---------------------------------------------------------------------------
-- One entry point, so the schedule has a single command
-- ---------------------------------------------------------------------------
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

  raise notice 'nexus maintenance: %', v_result;
  return v_result;
end;
$$;

comment on function public.run_nexus_maintenance() is
  'Daily maintenance: roll up fuel prices, then prune raw prices, unread articles, pg_net responses and pg_cron history. Each step is isolated; the result reports rows touched or the error per step.';

revoke all on function public.prune_fuel_prices_raw(integer) from public;
revoke all on function public.prune_articles_unread(integer) from public;
revoke all on function public.prune_net_http_response(integer) from public;
revoke all on function public.prune_cron_job_run_details(integer) from public;
revoke all on function public.run_nexus_maintenance() from public;

grant execute on function public.prune_fuel_prices_raw(integer) to service_role;
grant execute on function public.prune_articles_unread(integer) to service_role;
grant execute on function public.prune_net_http_response(integer) to service_role;
grant execute on function public.prune_cron_job_run_details(integer) to service_role;
grant execute on function public.run_nexus_maintenance() to service_role;

-- ---------------------------------------------------------------------------
-- Schedule: 03:20 UTC, comfortably after the Berlin day it rolls up has ended
-- ---------------------------------------------------------------------------
do $do$
declare
  v_job record;
begin
  if not exists (select 1 from pg_extension where extname = 'pg_cron') then
    raise exception 'pg_cron extension is required for maintenance scheduling';
  end if;

  for v_job in
    select jobid
    from cron.job
    where jobname = 'nexus-maintenance-daily'
       or command ilike '%run_nexus_maintenance%'
  loop
    perform cron.unschedule(v_job.jobid);
  end loop;

  perform cron.schedule(
    'nexus-maintenance-daily',
    '20 3 * * *',
    $job$select public.run_nexus_maintenance();$job$
  );
end;
$do$;
