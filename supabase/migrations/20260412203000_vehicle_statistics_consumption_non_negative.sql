create or replace function public.get_car_refuel_plot_events(
  p_car_id uuid,
  p_since timestamp with time zone,
  p_until timestamp with time zone default now()
)
returns table (
  fueled_at timestamp with time zone,
  mileage integer,
  price_per_liter numeric,
  consumption_l_per_100km numeric
)
language sql
stable
set search_path = public
as $$
  with base_events as (
    select
      r.id,
      r.fueled_at,
      r.mileage::numeric as mileage,
      r.liters::numeric as liters,
      r.total_price::numeric as total_price,
      r.fuel_level_after::numeric as fuel_level_after,
      r.price_per_liter_calculated::numeric as price_per_liter_calculated,
      r.missed_previous_refuel
    from public.refuel_events r
    join public.cars c on c.id = r.car_id
    where r.car_id = p_car_id
      and (
        c.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access ca
          where ca.car_id = c.id and ca.user_id = auth.uid()
        )
      )
  ),
  ordered_events as (
    select
      b.*,
      lead(b.mileage) over (order by b.fueled_at desc, b.id desc) as prev_mileage,
      lead(b.fuel_level_after) over (order by b.fueled_at desc, b.id desc) as prev_fuel_level
    from base_events b
  ),
  interval_values as (
    select
      o.*,
      (o.mileage - o.prev_mileage) as km_delta,
      case
        when o.prev_fuel_level is not null and o.fuel_level_after is not null
          then o.liters + o.prev_fuel_level - o.fuel_level_after
        else o.liters
      end as fuel_used
    from ordered_events o
  )
  select
    i.fueled_at,
    i.mileage::int as mileage,
    coalesce(i.price_per_liter_calculated, (i.total_price / nullif(i.liters, 0))) as price_per_liter,
    case
      when i.missed_previous_refuel then null
      when i.prev_mileage is null or i.km_delta <= 0 then null
      when i.fuel_used is null or i.fuel_used <= 0 then null
      else (i.fuel_used / nullif(i.km_delta, 0)) * 100
    end as consumption_l_per_100km
  from interval_values i
  where i.fueled_at >= p_since
    and i.fueled_at <= p_until
  order by i.fueled_at asc, i.id asc;
$$;

grant execute on function public.get_car_refuel_plot_events(uuid, timestamp with time zone, timestamp with time zone)
  to anon, authenticated, service_role;
