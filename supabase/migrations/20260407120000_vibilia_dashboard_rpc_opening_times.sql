drop function if exists public.get_fuel_stations_current_prices(text);

create or replace function public.get_fuel_stations_current_prices(
  p_fuel_type text
)
returns table (
  id uuid,
  name text,
  brand text,
  street text,
  house_number text,
  place text,
  opening_times jsonb,
  whole_day boolean,
  current_price numeric,
  checked_at timestamp with time zone
)
language sql
stable
set search_path = public
as $$
  select
    s.id,
    s.name,
    s.brand,
    s.street,
    s.house_number,
    s.place,
    s.opening_times,
    s.whole_day,
    latest.price as current_price,
    latest.checked_at
  from public.fuel_stations s
  left join lateral (
    select fp.price, fp.checked_at
    from public.fuel_prices fp
    where fp.station_id = s.id
      and fp.fuel_type = p_fuel_type
    order by fp.checked_at desc
    limit 1
  ) latest on true
  order by coalesce(latest.price, 999), s.name;
$$;

grant execute on function public.get_fuel_stations_current_prices(text)
  to anon, authenticated, service_role;