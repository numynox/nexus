drop function if exists public.get_car_refuel_statistics(uuid, timestamp with time zone);

create or replace function public.get_car_refuel_statistics(
  p_car_id uuid,
  p_now timestamp with time zone default now()
)
returns table (
  latest_mileage integer,
  driven_total_km numeric,
  driven_this_year_km numeric,
  driven_last_year_km numeric,
  driven_avg_month_km numeric,
  driven_avg_day_km numeric,
  fuel_used_total_l numeric,
  fuel_used_this_year_l numeric,
  fuel_used_last_year_l numeric,
  fuel_used_avg_month_l numeric,
  fuel_used_avg_day_l numeric,
  fuel_cost_total_eur numeric,
  fuel_cost_this_year_eur numeric,
  fuel_cost_last_year_eur numeric,
  fuel_cost_avg_month_eur numeric,
  fuel_cost_avg_day_eur numeric,
  avg_consumption_l_per_100km numeric,
  min_consumption_l_per_100km numeric,
  max_consumption_l_per_100km numeric,
  avg_cost_per_km_eur numeric,
  min_cost_per_km_eur numeric,
  max_cost_per_km_eur numeric,
  expense_total_eur numeric,
  expense_this_year_eur numeric,
  expense_last_year_eur numeric,
  expense_avg_year_eur numeric
)
language sql
stable
set search_path = public
as $$
  with boundaries as (
    select
      date_trunc('year', p_now) as start_this_year,
      date_trunc('year', p_now) - interval '1 year' as start_last_year,
      date_trunc('year', p_now) + interval '1 year' as start_next_year
  ),
  base_events as (
    select
      r.id,
      r.fueled_at,
      r.mileage::numeric as mileage,
      r.liters::numeric as liters,
      r.total_price::numeric as total_price,
      r.fuel_level_after::numeric as fuel_level_after,
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
  base_expenses as (
    select
      e.id,
      e.expensed_at,
      e.amount::numeric as amount
    from public.car_expenses e
    join public.cars c on c.id = e.car_id
    where e.car_id = p_car_id
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
  intervals as (
    select
      o.*,
      case
        when o.prev_mileage is not null and o.mileage > o.prev_mileage
          then o.mileage - o.prev_mileage
        else null
      end as km_delta,
      case
        when o.missed_previous_refuel then null
        when o.prev_mileage is null or o.mileage <= o.prev_mileage then null
        when o.prev_fuel_level is not null and o.fuel_level_after is not null
          then o.liters + o.prev_fuel_level - o.fuel_level_after
        else o.liters
      end as fuel_used_raw
    from ordered_events o
  ),
  metrics as (
    select
      i.*,
      case when i.fuel_used_raw > 0 then i.fuel_used_raw else null end as fuel_used,
      case
        when i.km_delta is not null and i.km_delta > 0 and i.fuel_used_raw > 0
          then (i.fuel_used_raw / i.km_delta) * 100
        else null
      end as consumption,
      case
        when i.km_delta is not null and i.km_delta > 0
          then i.total_price / i.km_delta
        else null
      end as cost_per_km
    from intervals i
  ),
  span as (
    select
      min(fueled_at) as min_at,
      max(fueled_at) as max_at,
      greatest(
        1,
        (
          extract(year from age(max(fueled_at), min(fueled_at)))::int * 12
          + extract(month from age(max(fueled_at), min(fueled_at)))::int
          + 1
        )
      ) as active_months,
      greatest(1, (max(fueled_at)::date - min(fueled_at)::date + 1))::numeric as active_days
    from base_events
  ),
  expense_span as (
    select
      min(expensed_at) as min_at,
      max(expensed_at) as max_at,
      greatest(
        1,
        extract(year from max(expensed_at))::int
        - extract(year from min(expensed_at))::int
        + 1
      ) as active_years
    from base_expenses
  )
  select
    coalesce(max(b.mileage)::int, 0) as latest_mileage,
    coalesce(sum(m.km_delta), 0) as driven_total_km,
    coalesce(sum(m.km_delta) filter (
      where m.fueled_at >= br.start_this_year and m.fueled_at < br.start_next_year
    ), 0) as driven_this_year_km,
    coalesce(sum(m.km_delta) filter (
      where m.fueled_at >= br.start_last_year and m.fueled_at < br.start_this_year
    ), 0) as driven_last_year_km,
    case
      when max(s.min_at) is null then 0
      else coalesce(sum(m.km_delta), 0) / max(s.active_months)
    end as driven_avg_month_km,
    case
      when max(s.min_at) is null then 0
      else coalesce(sum(m.km_delta), 0) / max(s.active_days)
    end as driven_avg_day_km,

    coalesce(sum(m.fuel_used), 0) as fuel_used_total_l,
    coalesce(sum(m.fuel_used) filter (
      where m.fueled_at >= br.start_this_year and m.fueled_at < br.start_next_year
    ), 0) as fuel_used_this_year_l,
    coalesce(sum(m.fuel_used) filter (
      where m.fueled_at >= br.start_last_year and m.fueled_at < br.start_this_year
    ), 0) as fuel_used_last_year_l,
    case
      when max(s.min_at) is null then 0
      else coalesce(sum(m.fuel_used), 0) / max(s.active_months)
    end as fuel_used_avg_month_l,
    case
      when max(s.min_at) is null then 0
      else coalesce(sum(m.fuel_used), 0) / max(s.active_days)
    end as fuel_used_avg_day_l,

    coalesce(sum(b.total_price), 0) as fuel_cost_total_eur,
    coalesce(sum(b.total_price) filter (
      where b.fueled_at >= br.start_this_year and b.fueled_at < br.start_next_year
    ), 0) as fuel_cost_this_year_eur,
    coalesce(sum(b.total_price) filter (
      where b.fueled_at >= br.start_last_year and b.fueled_at < br.start_this_year
    ), 0) as fuel_cost_last_year_eur,
    case
      when max(s.min_at) is null then 0
      else coalesce(sum(b.total_price), 0) / max(s.active_months)
    end as fuel_cost_avg_month_eur,
    case
      when max(s.min_at) is null then 0
      else coalesce(sum(b.total_price), 0) / max(s.active_days)
    end as fuel_cost_avg_day_eur,

    coalesce(avg(m.consumption), 0) as avg_consumption_l_per_100km,
    coalesce(min(m.consumption), 0) as min_consumption_l_per_100km,
    coalesce(max(m.consumption), 0) as max_consumption_l_per_100km,

    coalesce(avg(m.cost_per_km), 0) as avg_cost_per_km_eur,
    coalesce(min(m.cost_per_km), 0) as min_cost_per_km_eur,
    coalesce(max(m.cost_per_km), 0) as max_cost_per_km_eur,

    coalesce(sum(e.amount), 0) as expense_total_eur,
    coalesce(sum(e.amount) filter (
      where e.expensed_at >= br.start_this_year and e.expensed_at < br.start_next_year
    ), 0) as expense_this_year_eur,
    coalesce(sum(e.amount) filter (
      where e.expensed_at >= br.start_last_year and e.expensed_at < br.start_this_year
    ), 0) as expense_last_year_eur,
    case
      when max(es.min_at) is null then 0
      else coalesce(sum(e.amount), 0) / max(es.active_years)
    end as expense_avg_year_eur
  from boundaries br
  left join base_events b on true
  left join metrics m on m.id = b.id
  left join span s on true
  left join base_expenses e on true
  left join expense_span es on true;
$$;

grant execute on function public.get_car_refuel_statistics(uuid, timestamp with time zone)
  to anon, authenticated, service_role;
