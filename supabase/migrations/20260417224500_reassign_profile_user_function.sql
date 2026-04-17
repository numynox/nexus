-- Reassign one profile to a different auth user.
-- Useful after restoring public data into an environment with different auth.users IDs.

create or replace function public.reassign_profile_user(
  source_profile_id uuid,
  target_user_id uuid,
  merge_target_profile boolean default true
)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  source_exists boolean;
  target_user_exists boolean;
  target_profile_exists boolean;
begin
  if source_profile_id is null or target_user_id is null then
    raise exception 'source_profile_id and target_user_id must be non-null';
  end if;

  if source_profile_id = target_user_id then
    return;
  end if;

  select exists(select 1 from public.profiles p where p.id = source_profile_id)
    into source_exists;
  if not source_exists then
    raise exception 'source profile % does not exist', source_profile_id;
  end if;

  select exists(select 1 from auth.users u where u.id = target_user_id)
    into target_user_exists;
  if not target_user_exists then
    raise exception 'target auth user % does not exist', target_user_id;
  end if;

  select exists(select 1 from public.profiles p where p.id = target_user_id)
    into target_profile_exists;

  if target_profile_exists then
    if not merge_target_profile then
      raise exception 'target profile % already exists (set merge_target_profile=true)', target_user_id;
    end if;

    -- Move dependent ownership rows.
    update public.sections
    set user_id = target_user_id
    where user_id = source_profile_id;

    -- Merge reads and deduplicate on (user_id, article_id).
    insert into public.article_reads (user_id, article_id, read_at, created_at)
    select target_user_id, ar.article_id, ar.read_at, ar.created_at
    from public.article_reads ar
    where ar.user_id = source_profile_id
    on conflict (user_id, article_id)
    do update
      set read_at = greatest(public.article_reads.read_at, excluded.read_at),
          created_at = least(public.article_reads.created_at, excluded.created_at);

    delete from public.article_reads
    where user_id = source_profile_id;

    -- Keep existing target values and fill blanks from source.
    update public.profiles p
    set full_name = coalesce(p.full_name, src.full_name),
        email = coalesce(p.email, src.email)
    from public.profiles src
    where p.id = target_user_id
      and src.id = source_profile_id;

    delete from public.profiles
    where id = source_profile_id;
  else
    -- If target profile does not exist, this cascades to dependent tables.
    update public.profiles
    set id = target_user_id
    where id = source_profile_id;
  end if;
end;
$$;

revoke all on function public.reassign_profile_user(uuid, uuid, boolean) from public;
grant execute on function public.reassign_profile_user(uuid, uuid, boolean) to service_role;
