# B-67 Crash evidence

Added on 2026-11-11. This file has the plan, the decisions, and the status of
B-67.

## Problem

- The cockpit shows a crash count (`unclean_exit`), but no cause.
- `unclean_exit` mixes a native crash, a force quit after a freeze, a kill,
  and a power loss.
- The app detected a crash only when analytics was on. The flag was in
  `AnalyticsPreferences`.
- A native crash (CoreVideo, Metal, mpv) stops the process at once. No Java
  code runs, so the error popup and its "Report this problem" button do not
  show. At the next start, the app showed nothing.
- The JVM writes an `hs_err_pid<pid>.log` file. It goes to the start folder
  of the process, or to the temp folder when the start folder is read-only.
  Nothing read this file.

## Decisions

1. The crash marker is always on. It does not depend on analytics. Analytics
   reads the result of the marker (decided on 2026-11-11).
2. The crash type, the crash library, and the crash activity are new
   essential counters. They are crash counts, the same type of data as
   `unclean_exit`. Thus the notice version stays 3, and the schema stays 4.
   The site text and `docs/analytics/legitimate-interest.md` change
   (decided on 2026-11-11).
3. The crash popup opens the feedback form. The user presses Send. There is
   no automatic send now (decided on 2026-11-11). An "Always send crash
   reports" choice can come later.
4. A crash fingerprint (a hash of the top stack frames) comes later. It is not
   a value of a closed list, so it needs a new notice version.
5. The popup shows only when the app has evidence: a crash file or a freeze.
   A crash with no evidence (a kill, a power loss) goes only to the log and to
   the analytics. Thus a power loss does not show a popup.

## Design

### Layer 1: evidence on the user's computer

- Package `org.litvin.crash`. It is a leaf service, as `feedback`. `app`
  wires it.
- The marker file `last-session.properties` in the app data folder. At the
  start, the app reads the old marker and then writes a new one: the state
  `open`, the PID, the start time, the start folder, and the app version.
  The shutdown hook writes the state `closed`. A JVM crash, a kill, and a
  power loss do not run the hook.
- The marker also keeps the current activities: `export`, `playback`, and
  `sleep_wake`, and the `freeze` flag. Each change writes the file at once,
  so that the file is correct at the crash.
- At the next start, an old marker with the state `open` means a crash. The
  app looks for `hs_err_pid<old PID>.log` in the old start folder, the temp
  folder, and the home folder. The temp folder of the JVM on macOS is the
  same as `java.io.tmpdir`.
- The app reads the crash file and keeps only the safe part: the header, the
  summary, the crashed thread, and its stack. It removes the command line,
  the registers, the memory, the process data, and the environment variables.
  It writes the safe part to the log and to `logs/crashes/`. The folder keeps
  the last 5 files.
- The freeze watchdog checks the UI thread each second. When the UI thread
  does not answer for 10 seconds, it writes a dump of all threads to the log
  and sets `freeze` in the marker. When the UI thread answers again, it
  clears `freeze`.

### Layer 2: the crash popup

- After the main window shows, the app shows the error popup "BananaShot
  closed unexpectedly" with "Report this problem".
- "Report this problem" opens the feedback form with the topic Problem, a
  short crash summary as the error text, and the log attached. The log has
  the end of the old session and the safe part of the crash file.
- The feedback contract does not change.

### Layer 3: crash counts in analytics

New essential counter keys. A session counts them one time, in the first
analytics session after the crash.

| Key | Meaning |
|---|---|
| `crash_native` | The crash file shows a native crash. |
| `crash_memory` | The crash file shows that the JVM had no memory. |
| `crash_freeze` | The UI thread was frozen at the end of the session. |
| `crash_unknown` | No evidence: a kill, a force quit, or a power loss. |
| `crash_lib_<lib>` | The library of the crashed frame. |
| `crash_at_<activity>` | What the app did at the crash. |

- `<lib>`: `corevideo`, `graphics`, `mpv`, `ffmpeg`, `jvm`, `system`, `other`.
- `<activity>`: `export`, `playback`, `sleep_wake`, `idle`.
- `unclean_exit` keeps its meaning. It now comes from the crash marker, so it
  also counts a crash after a session without analytics.
- The cockpit shows the crash counts by type, library, and activity.

## Release order

1. Deploy the analytics Worker with the new keys.
2. Deploy the site with the new text of the privacy page.
3. Release the app.

## Status

| Step | Status |
|---|---|
| Layer 1: crash marker, crash file, freeze watchdog | Done (2026-11-11). Checked with a real JVM crash (JNA, JDK 27, macOS) from a development run: the next start found the file and wrote the safe part. |
| Layer 2: crash popup | Done (2026-11-11). Checked in a development run with a separate data folder: the popup opened after a frozen session. The click on "Report this problem" is not checked. |
| Layer 3: app counters, contract, Worker, cockpit | Done (2026-11-11). Not deployed. |
| Privacy texts: site text | Done (2026-11-11). Not deployed. |
| Legitimate interest update | Draft (2026-11-11). The author must review it. |
| Check on a packaged build: where the JVM writes the crash file, on Windows and macOS | Not started |

### Activities

- `export`: the render queue sets it from the start to the end of a run.
- `playback`: the mpv player sets it while its status is "playing".
- `sleep_wake`: on macOS, from a sleep event until 60 seconds after the wake.
  Java gives no sleep events on Windows.

### Open points

- A crash in the JNA library itself gives the library `other`. Change this
  if such crashes are frequent.
- An "Always send crash reports" choice (decision 3) and a fingerprint
  (decision 4) are for later.
