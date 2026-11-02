-- The `http` extension: synchronous HTTP from SQL. It was already enabled on
-- the hosted project (through the dashboard), so this brings the schema in
-- the repository in line with production; there it is a no-op.
--
-- It is what lets a migration be applied with one paste into the SQL editor:
-- the database downloads the file from GitHub and runs it in the same
-- statement (DEPLOYMENT.md, "Without the CLI"). pg_net, which the scheduled
-- jobs use, cannot do that: it answers asynchronously.
create extension if not exists http with schema extensions;
