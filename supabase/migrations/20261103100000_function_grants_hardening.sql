-- The same tightening 20261102140000_abeona_function_grants did for Abeona,
-- applied to the older functions of the other apps after Supabase's security
-- advisor flagged them.
--
-- 1. `revoke … from public` does not reach Supabase's `anon` role, which gets
--    EXECUTE on new functions directly. No app calls any of these signed out,
--    so take it away explicitly.
-- 2. The ones only pg_cron, a trigger or maintenance by hand call lose
--    `authenticated` (and `public`) too. pg_cron runs its jobs as `postgres`,
--    the owner; a trigger or event trigger does not check EXECUTE of the role
--    that fires it; `service_role` keeps what it had. None of them is called
--    by the web apps or the Android apps.
--    The client RPCs (`share_car_with_email`, `list_car_members`,
--    `annona_dashboard_summary`), the RLS helper `user_can_access_car` and the
--    unused-but-kept `get_user_home_feeds` (an older APK may still call it)
--    keep `authenticated`.
-- 3. Six policies on `cars` and `car_access` call `user_can_access_car` and
--    were written without `to …`, so they also apply to `anon`. Postgres
--    checks EXECUTE on a policy's functions before reading a row, so without
--    the function an anon read would fail with "permission denied" instead of
--    returning nothing. Scope them to `authenticated`, which they only ever
--    matched anyway (`auth.uid()` is null for anon). Anon still sees nothing:
--    with no policy for its role, RLS denies.
-- 4. Pin search_path on `nexus_time_zone()`, which had none.

-- Several of these still have the default grant to `public` too, which every
-- role inherits, anon included; take both away and grant back what is kept.
revoke execute on function
  public.user_can_access_car(uuid, uuid),
  public.share_car_with_email(uuid, text),
  public.list_car_members(uuid),
  public.annona_dashboard_summary(),
  public.get_user_home_feeds(uuid),
  public.reassign_profile_user(uuid, uuid, boolean),
  public.invoke_fetch_rss(),
  public.invoke_refresh_fuel_prices(),
  public.handle_new_user_profile(),
  public.rls_auto_enable()
from public, anon;

revoke execute on function
  public.reassign_profile_user(uuid, uuid, boolean),
  public.invoke_fetch_rss(),
  public.invoke_refresh_fuel_prices(),
  public.handle_new_user_profile(),
  public.rls_auto_enable()
from authenticated;

grant execute on function
  public.user_can_access_car(uuid, uuid),
  public.share_car_with_email(uuid, text),
  public.list_car_members(uuid),
  public.annona_dashboard_summary(),
  public.get_user_home_feeds(uuid)
to authenticated;

alter policy "Users can read accessible cars" on public.cars to authenticated;
alter policy "Users can update accessible cars" on public.cars to authenticated;
alter policy "Users can delete accessible cars" on public.cars to authenticated;
alter policy "Users can view access rows for accessible cars" on public.car_access to authenticated;
alter policy "Users can grant access for accessible cars" on public.car_access to authenticated;
alter policy "Users can revoke access for accessible cars" on public.car_access to authenticated;

alter function public.nexus_time_zone() set search_path = public;
