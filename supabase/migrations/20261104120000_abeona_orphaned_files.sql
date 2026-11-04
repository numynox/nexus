-- Abeona: find the files in abeona-tickets that no row points at any more.
--
-- Clients delete a trip's ticket and a card's file when they delete the row,
-- best effort. Deleting a traveller cascades to their BahnCards in the
-- database, but not to the card files in Storage; a failed delete, a
-- replaced file whose old copy could not be removed, or a whole household
-- going away leave files behind too. Nobody can open them (access follows
-- the row the path names), but they count against the free 1 GB.
--
-- Postgres may not delete from storage.objects (Supabase's protect_delete
-- trigger; the bytes live outside the database anyway), so the deletion goes
-- through the Storage API: abeona-refresh-trips, which already runs every
-- 15 minutes with the service role, asks this function for a handful of
-- names and removes them (decisions/2026-11-03-abeona-bahncard-files-follow-the-card.md).
--
-- An object counts as orphaned, once it is older than p_min_age (an upload
-- happens just before its row is updated), when its path is
--   <household>/<trip_id>/<file>              and no trip has it as ticket_path
--   <household>/bahncards/<card_id>/<file>    and no card has it as file_path
-- Any other path is left alone: Abeona did not write it.
create or replace function public.abeona_orphaned_files(
  p_limit integer default 50,
  p_min_age interval default interval '1 day'
)
returns setof text
language sql
stable
security invoker
set search_path = public
as $$
  select o.name
  from storage.objects o
  where o.bucket_id = 'abeona-tickets'
    and o.created_at < now() - p_min_age
    and (
      (
        split_part(o.name, '/', 2) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        and split_part(o.name, '/', 3) <> ''
        and split_part(o.name, '/', 4) = ''
        and not exists (select 1 from public.abeona_trips t where t.ticket_path = o.name)
      )
      or (
        split_part(o.name, '/', 2) = 'bahncards'
        and split_part(o.name, '/', 3) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        and split_part(o.name, '/', 4) <> ''
        and not exists (select 1 from public.abeona_bahncards c where c.file_path = o.name)
      )
    )
  order by o.created_at
  limit greatest(0, least(p_limit, 500));
$$;

revoke all on function public.abeona_orphaned_files(integer, interval) from public, anon, authenticated;
grant execute on function public.abeona_orphaned_files(integer, interval) to service_role;
