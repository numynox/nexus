-- Allow editing refuel events for cars the user can access.

drop policy if exists "Users can update refuel events for accessible cars" on public.refuel_events;

create policy "Users can update refuel events for accessible cars"
on public.refuel_events
for update
using (
  exists (
    select 1
    from public.cars
    where id = car_id
      and (
        owner_id = auth.uid()
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
    where id = car_id
      and (
        owner_id = auth.uid()
        or exists (
          select 1
          from public.car_access
          where car_access.car_id = cars.id
            and car_access.user_id = auth.uid()
        )
      )
  )
);
