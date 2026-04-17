alter table public.fuel_stations
  add column if not exists discount numeric not null default 0;

-- Return discount and order by discounted price so station lists are price-sorted.
drop function if exists public.get_fuel_stations_current_prices(text);

create function public.get_fuel_stations_current_prices(
  p_fuel_type text
)
returns table (
  id uuid,
  name text,
  brand text,
  street text,
  house_number text,
  place text,
  whole_day boolean,
  discount numeric,
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
    s.whole_day,
    coalesce(s.discount, 0)::numeric as discount,
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
  order by coalesce(latest.price - coalesce(s.discount, 0), 999), s.name;
$$;

grant execute on function public.get_fuel_stations_current_prices(text)
  to anon, authenticated, service_role;
