-- Sections carried a colour for a day (20260905160000). It reached the screen
-- in exactly two places: the sidebar icon, where it sat on the selected row's
-- primary fill and went unreadable, and a swatch row in the settings dialog
-- that existed to feed it. An icon coloured differently from the label beside
-- it reads as a second piece of information when it is only a restatement of
-- the same one, so both are gone and the column has nothing left to serve.
--
-- `icon` stays: it distinguishes sections from one another, which is the job
-- the colour was doing badly.
--
-- ORDER MATTERS. `fetchSectionsForUser` names `color` in its select until the
-- frontend that drops it is deployed, and PostgREST rejects a select naming a
-- column that is not there. Merge first, then run this.
--
-- See .agents/brain/decisions/2026-09-06-sections-have-no-colour.md

alter table public.sections
  drop column if exists color;
