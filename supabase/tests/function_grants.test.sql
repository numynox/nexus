-- Who may call what, outside Abeona (its grants are in abeona.test.sql).
-- Supabase grants EXECUTE to `anon` directly, so a `revoke … from public` in a
-- migration does not reach it; these tests pin that signed-out callers get
-- none of these functions, that the clients keep the RPCs they call, and that
-- the trigger and cron-only functions still work for the roles that run them.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(28);

-- ── anon: nothing ──────────────────────────────────────────────────────────
select ok(not has_function_privilege('anon', 'public.user_can_access_car(uuid, uuid)', 'execute'),
  'anon cannot call user_can_access_car');
select ok(not has_function_privilege('anon', 'public.share_car_with_email(uuid, text)', 'execute'),
  'anon cannot call share_car_with_email');
select ok(not has_function_privilege('anon', 'public.list_car_members(uuid)', 'execute'),
  'anon cannot call list_car_members');
select ok(not has_function_privilege('anon', 'public.annona_dashboard_summary()', 'execute'),
  'anon cannot call annona_dashboard_summary');
select ok(not has_function_privilege('anon', 'public.get_user_home_feeds(uuid)', 'execute'),
  'anon cannot call get_user_home_feeds');
select ok(not has_function_privilege('anon', 'public.reassign_profile_user(uuid, uuid, boolean)', 'execute'),
  'anon cannot call reassign_profile_user');
select ok(not has_function_privilege('anon', 'public.invoke_fetch_rss()', 'execute'),
  'anon cannot call invoke_fetch_rss');
select ok(not has_function_privilege('anon', 'public.invoke_refresh_fuel_prices()', 'execute'),
  'anon cannot call invoke_refresh_fuel_prices');
select ok(not has_function_privilege('anon', 'public.handle_new_user_profile()', 'execute'),
  'anon cannot call handle_new_user_profile');
select ok(not has_function_privilege('anon', 'public.rls_auto_enable()', 'execute'),
  'anon cannot call rls_auto_enable');

-- ── authenticated: what the clients call, and the RLS helper ───────────────
select ok(has_function_privilege('authenticated', 'public.user_can_access_car(uuid, uuid)', 'execute'),
  'authenticated keeps user_can_access_car (RLS on cars and car_access)');
select ok(has_function_privilege('authenticated', 'public.share_car_with_email(uuid, text)', 'execute'),
  'authenticated keeps share_car_with_email (Vibilia web and Android)');
select ok(has_function_privilege('authenticated', 'public.list_car_members(uuid)', 'execute'),
  'authenticated keeps list_car_members (Vibilia web and Android)');
select ok(has_function_privilege('authenticated', 'public.annona_dashboard_summary()', 'execute'),
  'authenticated keeps annona_dashboard_summary (Annona web and Android)');
select ok(has_function_privilege('authenticated', 'public.get_user_home_feeds(uuid)', 'execute'),
  'authenticated keeps get_user_home_feeds (an older APK may still call it)');

-- ── authenticated: nothing that only cron, triggers or maintenance call ────
select ok(not has_function_privilege('authenticated', 'public.reassign_profile_user(uuid, uuid, boolean)', 'execute'),
  'authenticated cannot call reassign_profile_user');
select ok(not has_function_privilege('authenticated', 'public.invoke_fetch_rss()', 'execute'),
  'authenticated cannot call invoke_fetch_rss');
select ok(not has_function_privilege('authenticated', 'public.invoke_refresh_fuel_prices()', 'execute'),
  'authenticated cannot call invoke_refresh_fuel_prices');
select ok(not has_function_privilege('authenticated', 'public.handle_new_user_profile()', 'execute'),
  'authenticated cannot call handle_new_user_profile');
select ok(not has_function_privilege('authenticated', 'public.rls_auto_enable()', 'execute'),
  'authenticated cannot call rls_auto_enable');

-- ── The roles that do run them still can ───────────────────────────────────
select ok(has_function_privilege('service_role', 'public.reassign_profile_user(uuid, uuid, boolean)', 'execute'),
  'service_role keeps reassign_profile_user');
select ok(has_function_privilege('postgres', 'public.invoke_fetch_rss()', 'execute')
      and has_function_privilege('postgres', 'public.invoke_refresh_fuel_prices()', 'execute'),
  'postgres (the pg_cron job owner) keeps the invoke_* functions');
select is(
  (select count(*) from cron.job
    where command ~ 'invoke_(fetch_rss|refresh_fuel_prices)' and username <> 'postgres'),
  0::bigint,
  'every cron job that calls an invoke_* function runs as postgres'
);

-- A signup still creates a profile, though the role Auth inserts as no longer
-- holds EXECUTE on the trigger function: a trigger does not check it.
select ok(not has_function_privilege('supabase_auth_admin', 'public.handle_new_user_profile()', 'execute'),
  'supabase_auth_admin holds no EXECUTE on handle_new_user_profile');

insert into auth.users (id, email)
values ('cccc0000-0000-4000-8000-000000000001', 'grants-signup@test.local');

select is(
  (select email from public.profiles where id = 'cccc0000-0000-4000-8000-000000000001'),
  'grants-signup@test.local',
  'the signup trigger still creates a profile'
);

-- This session cannot become supabase_auth_admin, so show the same with a role
-- that lacks EXECUTE too: authenticated fires the very function through a
-- stand-in for auth.users, and the profile is updated.
create temporary table grants_signup_stand_in (
  id uuid, email text, raw_user_meta_data jsonb
);
create trigger grants_signup_stand_in_profile
after insert on grants_signup_stand_in
for each row execute function public.handle_new_user_profile();
grant insert on grants_signup_stand_in to authenticated;

set local role authenticated;
insert into grants_signup_stand_in (id, email, raw_user_meta_data)
values ('cccc0000-0000-4000-8000-000000000001', 'grants-signup@test.local',
        '{"full_name": "Grants T Signup"}');
reset role;

select is(
  (select full_name from public.profiles where id = 'cccc0000-0000-4000-8000-000000000001'),
  'Grants T Signup',
  'a role without EXECUTE still fires the profile trigger'
);

-- ── anon reading cars gets nothing, not an error ───────────────────────────
-- The car policies call user_can_access_car, which anon may no longer call;
-- they are scoped to authenticated so anon is denied by RLS instead.
insert into public.cars (id, name, owner_id, tank_capacity)
values ('dddd0000-0000-4000-8000-0000000000aa', 'Grants T Car', 'cccc0000-0000-4000-8000-000000000001', 50);

set local role anon;
select lives_ok($$ select count(*) from public.cars $$, 'anon can query cars without a permission error');
select is((select count(*) from public.cars), 0::bigint, 'anon sees no cars');
reset role;

select * from finish();

rollback;
