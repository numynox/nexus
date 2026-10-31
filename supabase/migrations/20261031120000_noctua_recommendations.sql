-- Noctua recommendations: "everyone should see this".
--
-- A reader swipes an article to recommend it to every other Noctua user. The
-- article is then marked prominently for everybody, and for everyone but the
-- recommender it is pinned to the top of the feed on the next load — once, as
-- unseen again, even if they had already scrolled past it. "Seen" is device
-- local, so that reset happens in the clients; the database only records who
-- recommended what, and when.
--
-- Unlike reads and stars, a recommendation is shared on purpose: every
-- authenticated user may read every row, so each client can say who
-- recommended an article. Writing stays one's own, and only for an article the
-- recommender can see — the same check article_stars makes.
--
-- Recipients may not follow the recommended article's feed, and articles are
-- visible only through one's own sections. noctua_recommended_articles is
-- therefore SECURITY DEFINER: it returns the recommended articles themselves,
-- whatever the caller's sections. That widens article visibility exactly by
-- what someone chose to share; the feed catalogue is readable by everyone
-- anyway. It re-checks that the caller is signed in.

create table if not exists public.noctua_recommendations (
  article_id bigint not null references public.articles(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on update cascade on delete cascade,
  recommended_at timestamptz not null default now(),
  primary key (article_id, user_id)
);

create index if not exists noctua_recommendations_recommended_at_idx
  on public.noctua_recommendations (recommended_at desc);

create index if not exists noctua_recommendations_user_id_idx
  on public.noctua_recommendations (user_id);

alter table public.noctua_recommendations enable row level security;

drop policy if exists "noctua_recommendations_select_authenticated" on public.noctua_recommendations;
create policy "noctua_recommendations_select_authenticated"
on public.noctua_recommendations
for select
to authenticated
using (true);

drop policy if exists "noctua_recommendations_insert_own_visible_articles" on public.noctua_recommendations;
create policy "noctua_recommendations_insert_own_visible_articles"
on public.noctua_recommendations
for insert
to authenticated
with check (
  user_id = auth.uid()
  and exists (
    select 1
    from public.sections s
    join public.sections_feeds sf on sf.section_id = s.id
    join public.articles a on a.feed_id = sf.feed_id
    where s.user_id = auth.uid()
      and a.id = noctua_recommendations.article_id
  )
);

drop policy if exists "noctua_recommendations_delete_own" on public.noctua_recommendations;
create policy "noctua_recommendations_delete_own"
on public.noctua_recommendations
for delete
to authenticated
using (user_id = auth.uid());

grant select, insert, delete on table public.noctua_recommendations to authenticated;
grant all on table public.noctua_recommendations to service_role;

-- Recommended articles of the last p_days days, newest recommendation first,
-- with who recommended them. `mine` says whether the caller is one of them;
-- `from_others` whether anyone else is — which is what pins an article.
create or replace function public.noctua_recommended_articles(p_days integer default 30)
returns table (
  article_id bigint,
  feed_id bigint,
  title text,
  url text,
  published_at timestamptz,
  updated_at timestamptz,
  author text,
  summary text,
  image_url text,
  tags text[],
  feed_name text,
  feed_icon text,
  recommended_at timestamptz,
  recommenders jsonb,
  mine boolean,
  from_others boolean
)
language sql
stable
security definer
set search_path = public
as $$
  with recent as (
    select r.article_id, r.user_id, r.recommended_at,
      coalesce(
        nullif(btrim(p.full_name), ''),
        nullif(split_part(p.email, '@', 1), ''),
        'Someone'
      ) as name
    from public.noctua_recommendations r
    left join public.profiles p on p.id = r.user_id
    where auth.uid() is not null
      and r.recommended_at >= now() - make_interval(days => greatest(1, least(coalesce(p_days, 30), 365)))
  ),
  grouped as (
    select
      article_id,
      max(recommended_at) as recommended_at,
      jsonb_agg(
        jsonb_build_object('user_id', user_id, 'name', name, 'at', recommended_at)
        order by recommended_at
      ) as recommenders,
      bool_or(user_id = auth.uid()) as mine,
      bool_or(user_id <> auth.uid()) as from_others
    from recent
    group by article_id
  )
  select
    a.id, a.feed_id, a.title, a.url, a.published_at, a.updated_at, a.author,
    a.summary, a.image_url, a.tags, f.name, f.icon,
    g.recommended_at, g.recommenders, g.mine, g.from_others
  from grouped g
  join public.articles a on a.id = g.article_id
  left join public.feeds f on f.id = a.feed_id
  order by g.recommended_at desc, a.id desc
  limit 200;
$$;

comment on function public.noctua_recommended_articles(integer) is
  'Noctua: articles recommended in the last p_days days, with their recommenders. SECURITY DEFINER so recipients see articles outside their own sections; returns nothing to an anonymous caller.';

revoke all on function public.noctua_recommended_articles(integer) from public, anon;
grant execute on function public.noctua_recommended_articles(integer) to authenticated, service_role;
