set check_function_bodies = off;

do $do$
declare
  v_job record;
begin
  if not exists (select 1 from pg_extension where extname = 'pg_cron') then
    raise exception 'pg_cron extension is required for refresh-fuel-prices scheduling';
  end if;

  for v_job in
    select jobid
    from cron.job
    where jobname in ('refresh-fuel-prices-hourly', 'refresh-fuel-prices-every-10min')
       or command ilike '%invoke_refresh_fuel_prices%'
  loop
    perform cron.unschedule(v_job.jobid);
  end loop;

  perform cron.schedule(
    'refresh-fuel-prices-every-10min',
    '7,17,27,37,47,57 * * * *',
    $job$select public.invoke_refresh_fuel_prices();$job$
  );
end;
$do$;
