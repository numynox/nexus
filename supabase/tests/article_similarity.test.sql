-- Duplicate story collapsing: Heise, Tagesschau and ZDF cover the same story
-- within minutes of each other, and the reader wants to see it once.
--
-- The two things worth pinning are that near-identical titles group, and that
-- the time window stops a recurring headline from folding a month of articles
-- into a single row.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(6);

insert into public.feeds (id, name, url, enabled) values
  (910001, 'Similarity Test Feed A', 'https://example.invalid/sim-a', true),
  (910002, 'Similarity Test Feed B', 'https://example.invalid/sim-b', true),
  (910003, 'Similarity Test Feed C', 'https://example.invalid/sim-c', true);

insert into public.articles (id, feed_id, title, url, published_at) values
  (910001, 910001, 'Bundestag beschließt neues Klimagesetz', 'https://example.invalid/s1', now()),
  (910002, 910002, 'Bundestag beschließt neues Klimagesetz mit großer Mehrheit', 'https://example.invalid/s2', now() - interval '20 minutes'),
  (910003, 910003, 'Neues Klimagesetz vom Bundestag beschlossen', 'https://example.invalid/s3', now() - interval '40 minutes'),
  (910004, 910001, 'Völlig anderes Thema: Fußball-Bundesliga', 'https://example.invalid/s4', now()),
  (910005, 910001, 'Bundestag beschließt neues Klimagesetz', 'https://example.invalid/s5', now() - interval '30 days');

create temporary table sim_groups as
select * from public.get_similar_article_groups(
  array[910001, 910002, 910003, 910004, 910005]::bigint[]
);

select is(
  (select count(distinct group_key) from sim_groups where article_id in (910001, 910002, 910003)),
  1::bigint,
  'three tellings of the same story share one group'
);

select is(
  (select group_key from sim_groups where article_id = 910002),
  910001::bigint,
  'the group key is the lowest id in the cluster, so it is stable'
);

-- 910003 matches 910001 only through 910002 in the strictest reading, which is
-- what makes connected components the right grouping rather than pairwise.
select is(
  (select group_key from sim_groups where article_id = 910003),
  910001::bigint,
  'a story reached only through another member still joins the group'
);

select isnt(
  (select group_key from sim_groups where article_id = 910004),
  910001::bigint,
  'an unrelated headline keeps its own group'
);

select isnt(
  (select group_key from sim_groups where article_id = 910005),
  910001::bigint,
  'an identical headline from a month ago is a different story, not a duplicate'
);

select is(
  (select count(*) from sim_groups),
  5::bigint,
  'every article gets exactly one group, including the ungrouped ones'
);

select * from finish();

rollback;
