-- Abeona, #38 phase 5: run abeona-refresh-trips every 15 minutes.
--
-- The pattern of invoke_fetch_rss / invoke_refresh_fuel_prices
-- (decisions/2026-03-27-schedule-edge-functions-with-pg-cron-and-vault.md):
-- pg_cron calls a wrapper that reads the function's URL and its invoke
-- secret from Vault and POSTs with pg_net. Both secrets must exist in Vault
-- (ABEONA_REFRESH_INVOKE_SECRET, ABEONA_REFRESH_FUNCTION_URL) or every tick
-- raises; see DEPLOYMENT.md.

set check_function_bodies = off;

create or replace function public.invoke_abeona_refresh_trips()
returns void
language plpgsql
security definer
set search_path = pg_catalog
as $function$
declare
  v_invoke_secret text;
  v_function_url text;
begin
  select decrypted_secret
    into v_invoke_secret
  from vault.decrypted_secrets
  where name = 'ABEONA_REFRESH_INVOKE_SECRET'
  limit 1;

  if v_invoke_secret is null then
    raise exception 'ABEONA_REFRESH_INVOKE_SECRET not found in vault';
  end if;

  select decrypted_secret
    into v_function_url
  from vault.decrypted_secrets
  where name = 'ABEONA_REFRESH_FUNCTION_URL'
  limit 1;

  if v_function_url is null then
    raise exception 'ABEONA_REFRESH_FUNCTION_URL not found in vault';
  end if;

  perform net.http_post(
    url := v_function_url,
    headers := jsonb_build_object(
      'Authorization', 'Bearer ' || v_invoke_secret,
      'Content-Type', 'application/json'
    ),
    body := '{}'::jsonb
  );
end;
$function$;

-- Reads Vault: nobody but the scheduler (postgres) may call it.
revoke all on function public.invoke_abeona_refresh_trips() from public, anon, authenticated;

do $do$
declare
  v_job record;
begin
  if not exists (select 1 from pg_extension where extname = 'pg_cron') then
    raise exception 'pg_cron extension is required for abeona-refresh-trips scheduling';
  end if;

  for v_job in
    select jobid
    from cron.job
    where jobname = 'abeona-refresh-trips-every-15min'
       or command ilike '%invoke_abeona_refresh_trips%'
  loop
    perform cron.unschedule(v_job.jobid);
  end loop;

  -- Off the minutes the other jobs use (0 and 7, 17, …).
  perform cron.schedule(
    'abeona-refresh-trips-every-15min',
    '4,19,34,49 * * * *',
    $job$select public.invoke_abeona_refresh_trips();$job$
  );
end;
$do$;
