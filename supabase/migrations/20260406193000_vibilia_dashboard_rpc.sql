create or replace function public.get_fuel_price_plot_history(
  p_fuel_type text,
  p_since timestamp with time zone,
  p_bucket_minutes integer default 10,
  p_time_zone text default 'UTC'
)
returns table (
  day_key date,
  bucket_minute integer,
  min_price numeric
)
language sql
stable
set search_path = public
as $$
  select
    (fp.checked_at at time zone p_time_zone)::date as day_key,
    (
      (
        (
          extract(hour from fp.checked_at at time zone p_time_zone)::int * 60
          + extract(minute from fp.checked_at at time zone p_time_zone)::int
        ) / greatest(p_bucket_minutes, 1)
      ) * greatest(p_bucket_minutes, 1)
    )::int as bucket_minute,
    min(fp.price)::numeric as min_price
  from public.fuel_prices fp
  where fp.fuel_type = p_fuel_type
    and fp.checked_at > p_since
  group by 1, 2
  order by 1 asc, 2 asc;
$$;

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

grant execute on function public.get_fuel_price_plot_history(text, timestamp with time zone, integer, text)
  to anon, authenticated, service_role;

grant execute on function public.get_fuel_stations_current_prices(text)
  to anon, authenticated, service_role;
