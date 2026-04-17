-- Allow profile ID reassignment (e.g. after restore + new auth users)
-- by cascading user_id updates from public.profiles to dependent article_reads rows.

alter table public.article_reads
  drop constraint if exists article_reads_user_id_fkey;

alter table public.article_reads
  add constraint article_reads_user_id_fkey
  foreign key (user_id)
  references public.profiles(id)
  on update cascade
  on delete cascade
  not valid;

alter table public.article_reads
  validate constraint article_reads_user_id_fkey;
