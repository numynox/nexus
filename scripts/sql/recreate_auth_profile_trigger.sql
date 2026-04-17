-- Recreate auth.users -> public.profiles trigger after public-only restores.
-- Public schema drops with CASCADE can remove this trigger dependency.

do $$
begin
  if exists (
    select 1
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'handle_new_user_profile'
  ) then
    drop trigger if exists on_auth_user_created on auth.users;

    create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user_profile();
  else
    raise notice 'Skipped trigger recreation: public.handle_new_user_profile() not found.';
  end if;
end
$$;
