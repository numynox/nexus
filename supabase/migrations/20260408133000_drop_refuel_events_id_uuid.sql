-- Drop transitional UUID shadow column after bigint migration is in place.
-- Safe to run on environments where the column/index may already be absent.

drop index if exists public.refuel_events_id_uuid_key;

alter table public.refuel_events
  drop column if exists id_uuid;
