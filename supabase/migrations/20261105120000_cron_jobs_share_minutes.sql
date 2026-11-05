-- Every scheduled job on the same minutes, to save Disk IO
-- (decisions/2026-11-05-scheduled-jobs-share-minutes.md).
--
-- Supabase archives WAL with archive_timeout = 120 s: any 2-minute window in
-- which anything writes to the database ends in a forced switch to a fresh
-- 16 MB WAL segment, written out and read back for archiving, however few
-- bytes were written. The jobs ran on 11 different minutes an hour (fuel on
-- 7, 17, …; Abeona on 4, 19, 34, 49; RSS on 0), each run writing at least its
-- own cron.job_run_details row — about 11 segments an hour from the schedule
-- alone, ~3 GB of writes and as much again of reads a day on a free-tier disk
-- whose baseline is 5 MB/s. On the same six minutes they share segments.
--
-- - fetch-rss-hourly:            0 * * * *          → 7 * * * *
-- - abeona-refresh-trips:        4,19,34,49 * * * * → 7,17,27,37,47,57 * * * *
--   (every 15 → every 10 minutes; renamed to …-every-10min)
-- - nexus-maintenance-daily:     20 3 * * *         → 17 3 * * *
-- - refresh-fuel-prices-every-10min keeps 7,17,27,37,47,57 * * * *.
--
-- Like every scheduling migration, each job is first unscheduled by name or
-- command, so re-running this is safe.

do $do$
declare
  v_job record;
begin
  if not exists (select 1 from pg_extension where extname = 'pg_cron') then
    raise exception 'pg_cron extension is required for scheduling';
  end if;

  for v_job in
    select jobid
    from cron.job
    where jobname in (
            'fetch-rss-hourly',
            'abeona-refresh-trips-every-15min',
            'abeona-refresh-trips-every-10min',
            'nexus-maintenance-daily'
          )
       or command ilike '%invoke_fetch_rss%'
       or command ilike '%invoke_abeona_refresh_trips%'
       or command ilike '%run_nexus_maintenance%'
  loop
    perform cron.unschedule(v_job.jobid);
  end loop;

  perform cron.schedule(
    'fetch-rss-hourly',
    '7 * * * *',
    $job$select public.invoke_fetch_rss();$job$
  );

  perform cron.schedule(
    'abeona-refresh-trips-every-10min',
    '7,17,27,37,47,57 * * * *',
    $job$select public.invoke_abeona_refresh_trips();$job$
  );

  -- Still well after the Berlin day it rolls up has ended (22:00/23:00 UTC).
  perform cron.schedule(
    'nexus-maintenance-daily',
    '17 3 * * *',
    $job$select public.run_nexus_maintenance();$job$
  );
end;
$do$;
