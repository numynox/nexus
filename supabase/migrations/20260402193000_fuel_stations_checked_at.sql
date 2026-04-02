alter table public.fuel_stations
  add column if not exists checked_at timestamp with time zone;
