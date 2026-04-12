alter table public.cars
  add column if not exists vin text,
  add column if not exists plate text,
  add column if not exists make text,
  add column if not exists model text,
  add column if not exists year integer;
