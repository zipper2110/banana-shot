# B-78 OS crash reports

Added on 2026-11-21. This file has the plan, the decisions, and the status of
B-78. It continues B-67 (`b-67-crash-evidence.md`).

## Problem

- On 2026-11-21, the analytics showed 2 crashes of 1.5.0: one on macOS and
  one on Windows. Both were `crash_unknown` with the activity `idle`.
- `crash_unknown` means that the next start found no JVM crash file
  (`hs_err_pid<pid>.log`) and no freeze. It can be a kill, a force quit, an OS
  shutdown, or a power loss. It can also be a native failure in the app.
- Some native failures stop the process at once. The JVM writes no crash file,
  and the shutdown hook does not run:
  - `abort()` or a failed assertion in native code. On macOS, the JVM does not
    handle `SIGABRT`. libmpv and FFmpeg run in the app process, and both call
    `abort()` when an internal check fails.
  - The fail-fast stop of Windows: `0xc0000409` (`__fastfail`, stack buffer
    overrun) and `0xc0000374` (heap corruption). GPU drivers often cause
    these.
  - A native library that calls the C function `exit()`.
- The OS writes a crash report for these failures. The app does not read it.
- Example: on 2026-11-14, a development run of the render API spike
  (`src/test/native/macos/mpv_gl_layer.m`) stopped with `SIGABRT`. libmpv
  failed an assertion in `mp_dispatch_queue_process`. The main thread called
  `mpv_render_context_update` while the thread `mpv-gl-layer` called
  `mpv_render_context_free`. The JVM wrote no crash file. macOS wrote
  `~/Library/Logs/DiagnosticReports/java-2026-11-14-135328.ips` with the full
  stack. The product does not use the spike code.

## Goal

At the next start, the app finds the OS crash report of the old process. It
keeps the safe part and gets the library of the failure. Then such a failure
gives `crash_native` and `crash_lib_<lib>`, not `crash_unknown`, and the
crash popup shows.

## Decisions

Proposed on 2026-11-21. The author must approve them.

1. No new analytics keys. A crash from an OS crash report has the type
   `native`. The library is a value of the closed list of B-67. Thus the
   notice version stays 3, and the schema stays 4. The Worker and the
   cockpit do not change.
2. The text of the privacy page and `docs/analytics/legitimate-interest.md`
   change: the log and the crash folder can also have the safe part of the
   crash report of the OS. The type of data does not change. The author
   reviews the text, as in B-67.
3. The JVM crash file wins. After a JVM crash, the JVM calls `abort()`, so
   macOS also writes a crash report with `SIGABRT` (for example
   `BananaShot-2026-11-16-004211.ips` after the `kill -SEGV` check of B-67).
   That report shows the JVM, not the cause. The app reads the OS crash report
   only when it finds no JVM crash file.
4. An OS crash report wins over the freeze flag. A force quit of a frozen app
   gives no crash report on macOS (`SIGKILL`). Thus a report means that the
   process failed, also after a freeze.
5. The library is the first frame of the crashed thread that is not in the
   abort chain. The abort chain is `libsystem_kernel`, `libsystem_pthread`,
   `libsystem_c`, and `libc++abi` on macOS. In the 2026-11-14 example, this
   gives `libmpv.2.dylib`, so the library is `mpv`.

## Design

### macOS

- Folder: `~/Library/Logs/DiagnosticReports/`, and its subfolder `Retired`.
- Files: `BananaShot-*.ips` (the packaged app) and `java-*.ips` (a development
  run). Only files that changed after the old start time.
- Format: line 1 is a JSON header (`app_name`, `timestamp`, `bug_type`).
  `bug_type` 309 is a crash. The rest of the file is one JSON object. Read it
  with Jackson.
- Match: the field `pid` is the old PID, and `procLaunch` is near the old
  start time. A PID can come again.
- Fields to read: `exception.type`, `exception.signal`,
  `termination.indicator`, `faultingThread`, `threads[faultingThread]` (the
  name, the queue, and the frames with `imageIndex` and `symbol`), and
  `usedImages[].name`.
- The safe part: the app version, the OS version, the exception, the
  termination, the name of the crashed thread, and its frames as
  `<image name> <symbol>` (40 frames maximum). Only the image names, not the
  paths: a path can contain the user name.
- The app removes all other fields. These include `procPath`, `parentProc`,
  `responsibleProc`, `crashReporterKey`, `sleepWakeUUID`, `bootSessionUUID`,
  the `codeSigning*` fields, `usedImages` paths and UUIDs, the other threads,
  `threadState` (the registers), `instructionByteStream`, `vmSummary`, and
  `extMods`.
- Delay: macOS writes the report some seconds after the process stops. A
  quick restart can come before the file exists. Thus, when the marker is
  `open` and the app finds no report, the app looks one more time after 30
  seconds.

### Windows

- Source: the event log Application, provider `Application Error`, event ID
  1000. A standard user can read this log.
- Read: `wevtutil qe Application /f:xml /rd:true /c:20` with a query for the
  provider, the event ID, and the time after the old start. Run it on a
  background thread with a time limit of 5 seconds.
- Fields of `EventData` (in this order): the app name, the app version, the
  app time stamp, the module name, the module version, the module time stamp,
  the exception code, the fault offset, the process ID (hex), the app start
  time, the app path, the module path, and the report ID.
- Match: the process ID is the old PID, and the app name is `BananaShot.exe`,
  `java.exe`, or `javaw.exe`. The jpackage launcher loads the JVM into its own
  process, so the PID is the PID of the marker.
- Fallback when the event log has nothing: the WER folders
  `%LOCALAPPDATA%\Microsoft\Windows\WER\ReportArchive` and `ReportQueue`,
  file `AppCrash_BananaShot.exe_*\Report.wer` (UTF-16 text). `Sig[3]` is the
  module, and `Sig[6]` is the exception code. The file has no PID, so the app
  matches the time: after the old start, before this start.
- The safe part: the app version, the module file name, the module version,
  the exception code with its name, and the fault offset. The app removes the
  paths and the report ID.
- Check on Windows: the field order, the read without admin rights, and the
  time of `wevtutil`.

### Library and summary

- Move the name rules of `JvmCrashFile.libraryOf` to one function for an
  image name. The JVM crash file and the OS crash reports use it.
- The crash summary gets the same lines as for a JVM crash file. Example:
  `problem: SIGABRT (Abort trap: 6)`, `frame: libmpv.2.dylib
  mp_dispatch_queue_process`, `thread: main`.
- The safe part goes to the log and to `logs/crashes/`, with the same name
  and the same limit of 5 files as in B-67.

## Tasks

| Task | Content | Status |
|---|---|---|
| T1 | One function for the library of an image name. `JvmCrashFile` uses it. No change of the result: the tests of `JvmCrashFileTest` stay green. | Open |
| T2 | macOS reader: find, match, safe part, library. Tests with a fixture made from the 2026-11-14 report, without the private fields. A test that the safe part has no path, UUID, or register. | Open |
| T3 | Windows reader: event log and WER fallback. Tests with fixtures of the event XML and of `Report.wer`. | Open |
| T4 | `CrashMonitor`: the order of decision 3 and 4, and the second look after 30 seconds. Check that the analytics session can get the crash keys after its first summary. | Open |
| T5 | Texts: the privacy page (the log part of the feedback report) and `legitimate-interest.md`. Author review. | Open |
| T6 | Check on a packaged build. macOS: a development option calls `abort()` through JNA. The next start shows the popup, `crash_native`, and the library. Windows: the same with `abort()` and with `RaiseFailFastException`. | Open |

Release order: T5 (site) before the app. The Worker and the cockpit do not
change.

## Status

| Step | Status |
|---|---|
| Plan | Written on 2026-11-21. The decisions wait for the author. |

## Open points

- On Windows, an `abort()` stops in `ucrtbase.dll`, so the library is
  `system`, not the library that called it. A minidump (WER `LocalDumps`)
  would show the caller, but it is large and has memory content. Not now.
- macOS writes `JetsamEvent-*.ips` when it stops a process for memory. This
  could give `crash_memory`. Later.
- A heartbeat in the marker and a check of the boot time could separate a
  kill from an OS shutdown or a power loss. This needs a new analytics key and
  a new notice text. Separate item.
