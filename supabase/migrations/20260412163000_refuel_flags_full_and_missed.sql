alter table public.refuel_events
  add column if not exists is_full_refuel boolean default true,
  add column if not exists missed_previous_refuel boolean default false;

update public.refuel_events
set
  is_full_refuel = coalesce(is_full_refuel, true),
  missed_previous_refuel = coalesce(missed_previous_refuel, false)
where is_full_refuel is null or missed_previous_refuel is null;

alter table public.refuel_events
  alter column is_full_refuel set default true,
  alter column is_full_refuel set not null,
  alter column missed_previous_refuel set default false,
  alter column missed_previous_refuel set not null;
