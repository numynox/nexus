create or replace function public.get_fuel_price_weekly_minima(
  p_fuel_type text,
  p_since timestamp with time zone,
  p_until timestamp with time zone default now(),
  p_time_zone text default 'UTC'
)
returns table (
  week_start date,
  min_price numeric
)
language sql
stable
set search_path = public
as $$
  select
    date_trunc('week', fp.checked_at at time zone p_time_zone)::date as week_start,
    min(fp.price)::numeric as min_price
  from public.fuel_prices fp
  where fp.fuel_type = p_fuel_type
    and fp.checked_at >= p_since
    and fp.checked_at <= p_until
  group by 1
  order by 1 asc;
$$;

grant execute on function public.get_fuel_price_weekly_minima(text, timestamp with time zone, timestamp with time zone, text)
  to anon, authenticated, service_role;
