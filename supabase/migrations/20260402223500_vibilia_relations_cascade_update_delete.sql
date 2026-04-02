-- Align Vibilia-related relations with cascade semantics where appropriate.
-- Goal:
-- 1) Keep child rows consistent when parent IDs are updated.
-- 2) Remove dependent rows automatically when parent rows are deleted where ownership implies dependency.

-- fuel_prices -> fuel_stations
alter table public.fuel_prices
  drop constraint if exists fuel_prices_station_id_fkey;

alter table public.fuel_prices
  add constraint fuel_prices_station_id_fkey
  foreign key (station_id)
  references public.fuel_stations(id)
  on update cascade
  on delete cascade;

-- cars -> auth.users
alter table public.cars
  drop constraint if exists cars_owner_id_fkey;

alter table public.cars
  add constraint cars_owner_id_fkey
  foreign key (owner_id)
  references auth.users(id)
  on update cascade
  on delete cascade;

-- car_access -> cars
alter table public.car_access
  drop constraint if exists car_access_car_id_fkey;

alter table public.car_access
  add constraint car_access_car_id_fkey
  foreign key (car_id)
  references public.cars(id)
  on update cascade
  on delete cascade;

-- car_access -> auth.users
alter table public.car_access
  drop constraint if exists car_access_user_id_fkey;

alter table public.car_access
  add constraint car_access_user_id_fkey
  foreign key (user_id)
  references auth.users(id)
  on update cascade
  on delete cascade;

-- refuel_events -> cars
alter table public.refuel_events
  drop constraint if exists refuel_events_car_id_fkey;

alter table public.refuel_events
  add constraint refuel_events_car_id_fkey
  foreign key (car_id)
  references public.cars(id)
  on update cascade
  on delete cascade;

-- refuel_events -> auth.users
-- Keep events when user gets deleted (set null), but ensure key updates cascade.
alter table public.refuel_events
  drop constraint if exists refuel_events_user_id_fkey;

alter table public.refuel_events
  add constraint refuel_events_user_id_fkey
  foreign key (user_id)
  references auth.users(id)
  on update cascade
  on delete set null;

-- profiles -> auth.users
-- This keeps profile lifecycle aligned with the backing auth user.
alter table public.profiles
  drop constraint if exists profiles_id_fkey;

alter table public.profiles
  add constraint profiles_id_fkey
  foreign key (id)
  references auth.users(id)
  on update cascade
  on delete cascade
  not valid;

alter table public.profiles
  validate constraint profiles_id_fkey;
