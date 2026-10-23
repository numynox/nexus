# ADR: Home screen widgets with Glance, loading for themselves

**Date:** 2026-10-23
**Status:** Accepted

## Context
Vibilia (tank and cheapest fuel) and Annona (what to use up next) got home
screen widgets. A widget runs without the app: no ViewModel, often no
network, and Android redraws it at most every 30 minutes on its own.

## Decision
- Jetpack Glance (`glance-appwidget`, `glance-material3`), so widgets are
  written in Compose and themed from the app's own `NexusPalette` (the
  wallpaper's colours when the app's dynamic-colour setting is on).
- Each widget loads in `provideGlance` from the same repositories the app
  uses, and stores its last good load as JSON in the app's DataStore. Without
  network it shows that; Vibilia then marks a price older than an hour
  "as of 14:10". Annona stores dates, not labels, so "Tomorrow" becomes
  "Expires today" overnight without a load.
- Refresh triggers beside `updatePeriodMillis`: the activity's `onStop` (the
  home screen is about to show) and, in Vibilia, a successful drive upload.
- Screenshot tests render the real widget from each app's fake backend:
  `GlanceAppWidget.compose()` gives RemoteViews, which `shotWidget` in
  `core:testing` inflates and draws.
- No web counterpart: a browser has no home screen widget. The data they show
  is already on the web's own pages.

## Consequences
- Rounded corners come from `cornerRadius`, which only takes effect on
  Android 12+; older launchers draw square widgets. Robolectric draws them
  square too.
- Lists are plain Columns cut to the widget's height, not `LazyColumn`, so
  nothing scrolls inside a widget — and Robolectric can draw them.
