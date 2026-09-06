# ADR: Sections have an icon, not a colour

**Date:** 2026-09-06
**Status:** Accepted

## Context

Sections gained both an icon and a colour on 2026-09-05, modelled on Annona's
categories, which have both. The colour was applied to one thing: the section's
icon in the sidebar.

That put a mid-lightness hue on the selected row's primary fill, where it went
unreadable — worst on the one row you are looking at. Fixing the contrast meant
choosing between the section's colour and the row's, and the row has to win:
the selected state is what the sidebar is for.

Which left the colour applying to an icon in exactly one unselected state, next
to a label in a different colour. An icon coloured differently from its own
label reads as a second piece of information when it is only ever a restatement
of the first.

## Decision

Sections have an icon and no colour. `SECTION_COLORS`, `getSectionColor`, the
swatch row in the settings dialog, `Section.color` and the `sections.color`
column all go (`supabase/migrations/20260906120000_sections_drop_color.sql`).

Icons stay. They distinguish one section from another, which is the job the
colour was doing worse.

## Consequences

- Annona's categories keep their colour, so the two apps no longer match. That
  is fine: Annona's colours carry meaning across a grid of cards, Noctua's
  carried none down a single-column list.
- Dropping the column is the rare case where the **frontend deploys first**.
  `fetchSectionsForUser` names `color` in its select, and PostgREST rejects a
  select naming a column that does not exist — so running the migration before
  the merge takes the sidebar down. This inverts the usual order in
  `DEPLOYMENT.md`; there is no version of the frontend that wants the column
  after this PR.
- If sections ever need a colour again, give it to the row, not to the icon.
