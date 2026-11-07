# Maven Central rate-limits Android builds in cloud sandboxes

**Status:** Workaround in place
**Discovered:** 2026-11-07

## Problem
In a cloud agent session (outbound traffic through the session's proxy, one
shared egress address), `repo.maven.apache.org` answers `429 Too Many
Requests` for a good part of a cold Android build's ~2 GB of dependencies.
Gradle then reports "Could not download …", "Could not resolve …" or
"Repository MavenRepo is disabled due to earlier error". The tests fail a
second way: Robolectric downloads its Android runtime (`android-all-
instrumented`, ~190 MB) from Central itself at test time
(`MavenArtifactFetcher` … `IOException`). The 429 comes from Central, not
from the session's proxy, whose status showed no load.

## Impact
Without a warm `~/.gradle` cache, `recordRoborazziDebug` and
`assembleRelease` cannot run in such a session, so changes under `android/`
cannot be verified there. CI and laptops are not affected.

## Workaround
`android/gradle/init/maven-central-mirror.gradle`, an init script passed
with `-I` (README.md, "Android"). It puts Google's mirror of Central
(`maven-central.storage-download.googleapis.com/maven2`) first, drops Central
itself, and sets `robolectric.dependency.repo.url` on every test task.
Verified from an empty Gradle home and an empty Robolectric cache: the whole
Abeona build, all unit and screenshot tests and the release build, with no
request to Central.

What did not work, and why:
- Fewer parallel downloads (`--max-workers=2`) and retrying after 60–90 s:
  Central kept refusing the same large artifacts.
- Adding the mirror *after* Central: Gradle remembers which repository an
  artifact's metadata came from and keeps downloading the jar from there.
- Adding repositories per project (`allprojects { repositories … }`): the
  build prefers settings repositories and fails on project ones.
- `robolectric.dependency.dir`: that switches Robolectric to offline jars from
  a folder, it is not a cache location (`LocalDependencyResolver` fails).

## Resolution
None needed beyond the script while the sandboxes share an egress address.
