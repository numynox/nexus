-- Starred articles: "I want to come back to this".
--
-- Distinct from article_reads, which is history — what you have already looked
-- at. A star is intent, and the two answer different questions: /read tells you
-- where you have been, /starred tells you what you meant to return to.
--
-- Policies mirror article_reads exactly, including the insert check that the
-- article belongs to a feed in one of your own sections: articles themselves
-- are only visible under that condition, so a star must not be able to reach
-- further than a read.

create table if not exists public.article_stars (
  user_id uuid not null references public.profiles(id) on update cascade on delete cascade,
  article_id bigint not null references public.articles(id) on delete cascade,
  starred_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  primary key (user_id, article_id)
);

create index if not exists article_stars_user_starred_at_idx
  on public.article_stars (user_id, starred_at desc);

create index if not exists article_stars_article_id_idx
  on public.article_stars (article_id);

alter table public.article_stars enable row level security;

drop policy if exists "article_stars_select_own" on public.article_stars;
create policy "article_stars_select_own"
on public.article_stars
for select
to authenticated
using (user_id = auth.uid());

drop policy if exists "article_stars_insert_own_owned_articles" on public.article_stars;
create policy "article_stars_insert_own_owned_articles"
on public.article_stars
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
      and a.id = article_stars.article_id
  )
);

drop policy if exists "article_stars_delete_own" on public.article_stars;
create policy "article_stars_delete_own"
on public.article_stars
for delete
to authenticated
using (user_id = auth.uid());

grant select, insert, delete on table public.article_stars to authenticated;
grant all on table public.article_stars to service_role;
