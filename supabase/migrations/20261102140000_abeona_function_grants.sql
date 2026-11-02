-- Tighten what 20261102120000_abeona_foundation granted, after Supabase's
-- security advisor flagged it.
--
-- 1. `revoke … from public` does not reach Supabase's `anon` role, which gets
--    EXECUTE on new functions directly. Abeona has nothing for a signed-out
--    caller, so take it away explicitly. `authenticated` keeps it.
-- 2. Pin search_path on the three pure helpers that had none.

revoke execute on function
  public.abeona_user_in_household(uuid, uuid),
  public.abeona_user_in_household_path(text),
  public.abeona_current_household(),
  public.abeona_ensure_household(),
  public.abeona_invite_to_household(text),
  public.abeona_leave_household(),
  public.abeona_list_household_members(),
  public.abeona_save_trip(jsonb, jsonb),
  public.abeona_update_trip_live(uuid, jsonb),
  public.abeona_statistics(timestamptz, timestamptz),
  public.abeona_train_binding_lifted(text, boolean, integer),
  public.abeona_haversine_km(double precision, double precision, double precision, double precision),
  public.abeona_polyline_km(jsonb)
from anon;

alter function public.abeona_haversine_km(double precision, double precision, double precision, double precision)
  set search_path = public;
alter function public.abeona_polyline_km(jsonb) set search_path = public;
alter function public.abeona_train_binding_lifted(text, boolean, integer) set search_path = public;
