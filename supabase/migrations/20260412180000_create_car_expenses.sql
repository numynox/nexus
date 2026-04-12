create table if not exists public.car_expenses (
  id bigserial primary key,
  car_id uuid not null references public.cars(id) on update cascade on delete cascade,
  user_id uuid references auth.users(id) on update cascade on delete set null,
  expensed_at timestamptz not null default now(),
  title text not null,
  amount numeric(10,2) not null,
  mileage integer,
  category text not null default 'Other',
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists car_expenses_car_id_idx
  on public.car_expenses (car_id);

create index if not exists car_expenses_expensed_at_idx
  on public.car_expenses (expensed_at desc);

alter table public.car_expenses enable row level security;

create policy "Users can view car expenses for accessible cars"
on public.car_expenses
for select
using (
  exists (
    select 1
    from public.cars
    where cars.id = car_expenses.car_id
      and (
        cars.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access
          where car_access.car_id = cars.id
            and car_access.user_id = auth.uid()
        )
      )
  )
);

create policy "Users can add car expenses for accessible cars"
on public.car_expenses
for insert
with check (
  exists (
    select 1
    from public.cars
    where cars.id = car_expenses.car_id
      and (
        cars.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access
          where car_access.car_id = cars.id
            and car_access.user_id = auth.uid()
        )
      )
  )
);

create policy "Users can update car expenses for accessible cars"
on public.car_expenses
for update
using (
  exists (
    select 1
    from public.cars
    where cars.id = car_expenses.car_id
      and (
        cars.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access
          where car_access.car_id = cars.id
            and car_access.user_id = auth.uid()
        )
      )
  )
)
with check (
  exists (
    select 1
    from public.cars
    where cars.id = car_expenses.car_id
      and (
        cars.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access
          where car_access.car_id = cars.id
            and car_access.user_id = auth.uid()
        )
      )
  )
);

create policy "Users can delete car expenses for accessible cars"
on public.car_expenses
for delete
using (
  exists (
    select 1
    from public.cars
    where cars.id = car_expenses.car_id
      and (
        cars.owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access
          where car_access.car_id = cars.id
            and car_access.user_id = auth.uid()
        )
      )
  )
);
