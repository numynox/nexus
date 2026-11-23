# Capturing a composable into an android.graphics.Picture crashes on phones

**Status:** Resolved
**Discovered:** 2026-11-23

## Problem
The first arrival postcard (#81) prepared its share image as soon as the card
showed. It recorded the postcard composable into an `android.graphics.Picture`
and drew that into a bitmap. The scene in it uses `clipToBounds()`, which
gives it its own graphics layer. On a phone that layer is a `RenderNode`, and
a `Picture`'s canvas is a software canvas that refuses render nodes. The
exception was thrown on the main thread while drawing, so the app crashed on
every arrived or past trip.

The screenshot tests did not catch it. Robolectric draws everything in
software, where no render nodes exist.

## Impact
Opening any past trip on Android crashed the app until the fix.

## Workaround
None needed.

## Resolution
`ui/trip/Postcard.kt` records the image into a `rememberGraphicsLayer()` and
reads it back with `toImageBitmap()`, Compose's supported capture. It does so
only when "Share" is pressed. A hardware bitmap is copied to `ARGB_8888`
before it is written out.

The rule: never draw Compose content into a `Picture` or any other software
canvas. Robolectric cannot read a hardware layer back, so the share mechanics
are not covered by screenshot tests; `PostcardImage` itself is.
