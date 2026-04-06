-- Indexes for vibilia dashboard RPC functions
-- 1) get_fuel_price_plot_history: filters by fuel_type + checked_at range
create index if not exists fuel_prices_fuel_type_checked_at_idx
  on public.fuel_prices (fuel_type, checked_at);

-- 2) get_fuel_stations_current_prices: latest row per station for a fuel type
create index if not exists fuel_prices_station_id_fuel_type_checked_at_desc_idx
  on public.fuel_prices (station_id, fuel_type, checked_at desc);
