-- Vibilia sharing: full access to cars a user owns or that are shared with them.

create or replace function public.user_can_access_car(
  p_car_id uuid,
  p_user_id uuid default auth.uid()
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.cars c
    where c.id = p_car_id
      and (
        c.owner_id = p_user_id
        or exists (
          select 1
          from public.car_access ca
          where ca.car_id = c.id
            and ca.user_id = p_user_id
        )
      )
  );
$$;

revoke all on function public.user_can_access_car(uuid, uuid) from public;
grant execute on function public.user_can_access_car(uuid, uuid) to authenticated;
grant execute on function public.user_can_access_car(uuid, uuid) to service_role;

-- Cars: keep own insert, but allow update/delete/select on any accessible car.
drop policy if exists "Users can manage their own cars" on public.cars;
drop policy if exists "Users can access shared cars" on public.cars;

drop policy if exists "Users can read accessible cars" on public.cars;
create policy "Users can read accessible cars"
on public.cars
for select
using (public.user_can_access_car(id, auth.uid()));

drop policy if exists "Users can insert own cars" on public.cars;
create policy "Users can insert own cars"
on public.cars
for insert
with check (owner_id = auth.uid());

drop policy if exists "Users can update accessible cars" on public.cars;
create policy "Users can update accessible cars"
on public.cars
for update
using (public.user_can_access_car(id, auth.uid()))
with check (public.user_can_access_car(id, auth.uid()));

drop policy if exists "Users can delete accessible cars" on public.cars;
create policy "Users can delete accessible cars"
on public.cars
for delete
using (public.user_can_access_car(id, auth.uid()));

-- car_access: full share management for users with access to the car.
drop policy if exists "Users can view access rows for accessible cars" on public.car_access;
create policy "Users can view access rows for accessible cars"
on public.car_access
for select
using (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Users can grant access for accessible cars" on public.car_access;
create policy "Users can grant access for accessible cars"
on public.car_access
for insert
with check (public.user_can_access_car(car_id, auth.uid()));

drop policy if exists "Users can revoke access for accessible cars" on public.car_access;
create policy "Users can revoke access for accessible cars"
on public.car_access
for delete
using (public.user_can_access_car(car_id, auth.uid()));
