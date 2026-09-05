# Supabase

Setup, local development and deployment for this project's Supabase backend are
documented once, at the repository root:

- `README.md` — local stack, migrations, seeding
- `DEPLOYMENT.md` — linking, pushing migrations, deploying functions, Vault
  secrets, backups
- `.agents/brain/architecture.md` — schema layout, RLS models, RPCs, scheduling

What lives here:

```
supabase/
├── migrations/    the source of truth for the schema (timestamp-ordered)
├── functions/     Deno Edge Functions
├── config.toml    per-function CLI settings
├── seed.sql       feeds and fuel stations for local development
└── db-info.json   STALE — documents only Noctua's original five tables
```

## Notes that are specific to this directory

- **`db-info.json` is out of date.** It predates Vibilia and Annona. Read the
  migrations instead; do not extend it.
- **Edge Functions cannot be pulled.** The CLI has no command that fetches
  remote function source. Everything under `functions/` must stay the
  authoritative copy — if a function only exists in the dashboard, paste it in
  here before touching it.
- **Syncing a schema changed outside migrations:** `supabase db remote commit`
  is deprecated; use `npx supabase db pull` to generate a migration from the
  remote schema, then inspect and commit it. This should be rare — schema
  changes belong in hand-written migrations.
