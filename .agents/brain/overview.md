# Brain Overview

`.agents/brain/` is the project's persistent knowledge base — the reasoning
behind the codebase, kept in focused files so only the relevant part needs
loading at any time.

It is committed, so it survives sessions, machines and people. This is the
difference between it and an assistant's local memory.

---

## What belongs here — and what does not

The brain records **why**. The repository's other documents record **what** and
**how**, and the brain links to them rather than restating them.

| Question | Lives in |
| :--- | :--- |
| How do the pieces fit together? | `architecture.md` |
| What is this thing called, in code and in the UI? | `glossary.md` |
| Why was it decided this way? | `decisions/` |
| What is broken or constrained, and what do we do about it? | `issues/` |
| How do I run the project locally? | `README.md` |
| How is it hosted, deployed and backed up? | `DEPLOYMENT.md` |
| How should code in this repo be written? | `CLAUDE.md` |

Add a directory only when there is real content for it, not in advance.

**Never copy content from `README.md` or `DEPLOYMENT.md` into the brain.**
Duplicated documentation drifts, and drifted documentation is worse than none —
this repository has already been bitten by it: `README.md` described two apps
long after the third existed, and `supabase/README.md` still referenced npm
scripts that had been renamed away.

---

## Structure

```
.agents/brain/
├── overview.md      ← this file: index and conventions
├── architecture.md  ← how the three apps and the one backend fit together
├── glossary.md      ← the domain nouns, in code and in the UI
├── decisions/       ← architectural decision records (ADRs)
└── issues/          ← known constraints, blockers, technical debt
```

---

## When to read

Read this file first. Then load only what the task needs:

| Working on... | Read... |
| :--- | :--- |
| Anything structural, or your first task in an app | `architecture.md` |
| Anything user-facing, or naming a new table/column | `glossary.md` |
| A choice that seems arbitrary | `decisions/` — it probably is not |
| The database, RLS, or an Edge Function | `decisions/`, then the migrations themselves |
| Deployment, hosting, CI, backups | `decisions/`, `issues/`, then `DEPLOYMENT.md` |
| Something that will not work | `issues/` before assuming it is a bug |

---

## When to write

Add or update an entry when:

- a decision is made that a future reader would otherwise have to reverse-engineer
- a constraint is discovered that will shape later work
- a known issue is found, worked around, or resolved

Do **not** write an entry for routine changes. A brain that records everything
gets read by nobody. Keep entries short and delete what stops being true.

---

## ADR format (`decisions/`)

Filename: `YYYY-MM-DD-short-title.md`

```markdown
# ADR: <Title>

**Date:** YYYY-MM-DD
**Status:** Accepted | Superseded by <file> | Deprecated

## Context
The situation that forced a choice.

## Decision
What was chosen.

## Consequences
What this costs, what it rules out, and what to watch for.
```

Supersede rather than edit: when a decision is reversed, add a new ADR and mark
the old one `Superseded`. The reasoning that was later abandoned is often the
most useful part of the record.

## Issue format (`issues/`)

```markdown
# <Short Title>

**Status:** Open | Workaround in place | Resolved
**Discovered:** YYYY-MM-DD

## Problem
What does not work, and why.

## Impact
What this blocks or complicates.

## Workaround
Current mitigation, if any.

## Resolution
How it was resolved, once it is.
```
