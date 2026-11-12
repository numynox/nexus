-- Does nothing. The first migration applied by CI rather than by hand: it
-- proves the `migrate` job in deploy-pages.yml can reach the live database and
-- record a version in supabase_migrations.schema_migrations
-- (decisions/2026-11-11-migrations-deployed-by-ci.md).

select 1;
