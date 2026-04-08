-- Add fueled_at and fuel_station relation, and migrate refuel_events.id from UUID to BIGINT.
-- This migration is written to be safe on existing databases and re-runnable.

-- 1) fueled_at: explicit event datetime with sane backfill/default
alter table public.refuel_events
  add column if not exists fueled_at timestamptz;

update public.refuel_events
set fueled_at = coalesce(fueled_at, "timestamp", created_at, now())
where fueled_at is null;

alter table public.refuel_events
  alter column fueled_at set default now();

alter table public.refuel_events
  alter column fueled_at set not null;

-- 2) optional fuel_station relation
alter table public.refuel_events
  add column if not exists fuel_station_id uuid;

alter table public.refuel_events
  drop constraint if exists refuel_events_fuel_station_id_fkey;

alter table public.refuel_events
  add constraint refuel_events_fuel_station_id_fkey
  foreign key (fuel_station_id)
  references public.fuel_stations(id)
  on update cascade
  on delete set null;

create index if not exists refuel_events_fuel_station_id_idx
  on public.refuel_events (fuel_station_id);

-- 3) replace UUID id with BIGINT autoincrement while preserving old UUID values
DO $$
DECLARE
  v_id_data_type text;
  v_pk_name text;
BEGIN
  SELECT c.data_type
    INTO v_id_data_type
  FROM information_schema.columns c
  WHERE c.table_schema = 'public'
    AND c.table_name = 'refuel_events'
    AND c.column_name = 'id';

  -- Only migrate if id is still UUID.
  IF v_id_data_type = 'uuid' THEN
    IF NOT EXISTS (
      SELECT 1
      FROM information_schema.columns c
      WHERE c.table_schema = 'public'
        AND c.table_name = 'refuel_events'
        AND c.column_name = 'id_v2'
    ) THEN
      alter table public.refuel_events add column id_v2 bigint;
    END IF;

    create sequence if not exists public.refuel_events_id_v2_seq;

    alter table public.refuel_events
      alter column id_v2 set default nextval('public.refuel_events_id_v2_seq');

    update public.refuel_events
    set id_v2 = nextval('public.refuel_events_id_v2_seq')
    where id_v2 is null;

    alter table public.refuel_events
      alter column id_v2 set not null;

    SELECT con.conname
      INTO v_pk_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE nsp.nspname = 'public'
      AND rel.relname = 'refuel_events'
      AND con.contype = 'p'
    LIMIT 1;

    IF v_pk_name IS NOT NULL THEN
      EXECUTE format('alter table public.refuel_events drop constraint %I', v_pk_name);
    END IF;

    IF EXISTS (
      SELECT 1
      FROM information_schema.columns c
      WHERE c.table_schema = 'public'
        AND c.table_name = 'refuel_events'
        AND c.column_name = 'id'
    ) AND NOT EXISTS (
      SELECT 1
      FROM information_schema.columns c
      WHERE c.table_schema = 'public'
        AND c.table_name = 'refuel_events'
        AND c.column_name = 'id_uuid'
    ) THEN
      alter table public.refuel_events rename column id to id_uuid;
    END IF;

    IF EXISTS (
      SELECT 1
      FROM information_schema.columns c
      WHERE c.table_schema = 'public'
        AND c.table_name = 'refuel_events'
        AND c.column_name = 'id_v2'
    ) THEN
      alter table public.refuel_events rename column id_v2 to id;
    END IF;

    alter table public.refuel_events
      add constraint refuel_events_pkey primary key (id);

    create unique index if not exists refuel_events_id_uuid_key
      on public.refuel_events (id_uuid);

    alter sequence public.refuel_events_id_v2_seq owned by public.refuel_events.id;

    PERFORM setval(
      'public.refuel_events_id_v2_seq',
      coalesce((select max(id) from public.refuel_events), 0) + 1,
      false
    );
  END IF;
END $$;

-- Ensure autoincrement default is present in already-migrated environments too.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.table_name = 'refuel_events'
      AND c.column_name = 'id'
      AND c.data_type = 'bigint'
  ) THEN
    create sequence if not exists public.refuel_events_id_v2_seq;

    alter table public.refuel_events
      alter column id set default nextval('public.refuel_events_id_v2_seq');

    alter sequence public.refuel_events_id_v2_seq owned by public.refuel_events.id;

    PERFORM setval(
      'public.refuel_events_id_v2_seq',
      coalesce((select max(id) from public.refuel_events), 0) + 1,
      false
    );
  END IF;
END $$;
