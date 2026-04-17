-- Sharing helpers for Vibilia settings.
-- 1) List car owner and shared users with display names.
-- 2) Share a car by recipient email.

create or replace function public.list_car_members(
  p_car_id uuid
)
returns table (
  user_id uuid,
  role text,
  full_name text,
  email text
)
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.user_can_access_car(p_car_id, auth.uid()) then
    raise exception 'Not allowed to view members for this car.';
  end if;

  return query
  with owner_row as (
    select c.owner_id as user_id, 'owner'::text as role
    from public.cars c
    where c.id = p_car_id
  ),
  shared_rows as (
    select ca.user_id, 'shared'::text as role
    from public.car_access ca
    where ca.car_id = p_car_id
  ),
  all_rows as (
    select * from owner_row
    union all
    select * from shared_rows
  )
  select
    r.user_id,
    r.role,
    p.full_name,
    p.email
  from all_rows r
  left join public.profiles p on p.id = r.user_id
  order by case when r.role = 'owner' then 0 else 1 end, coalesce(p.full_name, p.email, r.user_id::text);
end;
$$;

revoke all on function public.list_car_members(uuid) from public;
grant execute on function public.list_car_members(uuid) to authenticated;
grant execute on function public.list_car_members(uuid) to service_role;

create or replace function public.share_car_with_email(
  p_car_id uuid,
  p_email text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_target_user_id uuid;
begin
  if p_email is null or btrim(p_email) = '' then
    raise exception 'Email is required.';
  end if;

  if not public.user_can_access_car(p_car_id, auth.uid()) then
    raise exception 'Not allowed to share this car.';
  end if;

  select p.id
  into v_target_user_id
  from public.profiles p
  where lower(p.email) = lower(btrim(p_email))
  limit 1;

  if v_target_user_id is null then
    raise exception 'No user found for this email.';
  end if;

  insert into public.car_access (car_id, user_id)
  values (p_car_id, v_target_user_id)
  on conflict (car_id, user_id) do nothing;

  return v_target_user_id;
end;
$$;

revoke all on function public.share_car_with_email(uuid, text) from public;
grant execute on function public.share_car_with_email(uuid, text) to authenticated;
grant execute on function public.share_car_with_email(uuid, text) to service_role;
