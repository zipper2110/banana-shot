# Build Expiry: User Scenarios

Status: draft, 2026-10-02.

This file lists the user scenarios that the build expiry must support. Each
scenario is an acceptance criterion for `build-expiry-spec.md`. The spec
gives the rules. The scenarios show the result of the rules for a user. If a
scenario and a rule do not agree, one of them is wrong. Decide which, and
correct it.

Use these scenarios for the manual QA checklist (`qa/windows/`) and for the
names of the UI-flow tests.

## Format

- **User:** honest or cheater (see `threat-model.md`). For an honest user,
  the goal is no harm and an easy recovery. For a cheater, the goal is a
  bypass that is stopped or limited.
- **Starting state:** the build state, the network, the clock, the flag
  "expired mode in the last session", and the export queue.
- **Steps:** what the user or the environment does.
- **App behavior:** what the user sees. Internal state only where it
  explains the result.
- **Next steps for the user:** what the user can do, and the result of each
  action.
- **Expected result:** a result that a test or a person can measure.
- **References:** the sections of `build-expiry-spec.md`.

Terms:

- "Expiry" is the effective expiry: the earliest of the build expiry and the
  `stopsOn` of each rule that matches the version.
- "Dialog" is the dialog of expired mode. "Banner" is the banner of expired
  mode.
- "Path A" and "path B" are the two start paths in "Start of a build that
  looks expired".

## Summary

| ID | Scenario | User |
|---|---|---|
| S-01 | Warning before the expiry, then an update | Honest |
| S-02 | Update notice for a new release | Honest |
| S-03 | Expiry during a session, with exports in the queue | Honest |
| S-04 | Expiry while the app is closed | Honest |
| S-05 | Update from expired mode | Honest |
| S-06 | Clock too far forward, offline | Honest |
| S-07 | Clock too far forward, online | Honest |
| S-08 | Clock behind, offline (flat clock battery) | Honest |
| S-09 | New device with a clock earlier than the build date | Honest |
| S-10 | Always offline, with a correct clock | Honest |
| S-11 | Firewall or blocked region | Honest |
| S-12 | Office with TLS inspection | Honest |
| S-13 | Rule with a future stop date | Honest |
| S-14 | Rule that stops the version at once | Honest |
| S-15 | Wrong rule, then the fix of the author | Honest |
| S-16 | Sleep with the app open | Honest |
| S-17 | Second start while the app runs | Honest |
| S-18 | Reinstall, or install an old installer | Honest |
| S-19 | App closed with End task, or a crash | Honest |
| C-01 | Clock back, offline | Cheater |
| C-02 | Clock back at each start, with End task | Cheater |
| C-03 | Time tool for one program | Cheater |
| C-04 | New device with an old installer, offline | Cheater |
| C-05 | New Windows user account with an old installer | Cheater |

## Normal life

### S-01 Warning before the expiry, then an update

- **User:** honest.
- **Starting state:** normal mode, online. The expiry is in 30 days.
- **Steps:**
  1. The user starts the app.
- **App behavior:**
  - The warning shows: "This version works until 29 March 2027, 16:00."
    (local time, with the hour), with a "Download update" button.
  - While the app runs, the warning shows again one time on each calendar
    day (local time), in the first hour after midnight.
- **Next steps for the user:**
  - Click "Download update": the browser opens the download page. The user
    installs the new build. The new build has a new expiry, and the warning
    does not show.
  - Close the warning: it stays hidden until the next calendar day.
  - Do nothing: the build expires on the date in the warning (S-03 or S-04).
- **Expected result:** the warning starts exactly 30 × 24 hours before the
  expiry moment. The text shows the local time with the hour.
- **References:** "Expiry warning", "Expiry moment".

### S-02 Update notice for a new release

- **User:** honest.
- **Starting state:** normal mode, online. `latest.version` in the rules file
  is newer than the app version.
- **Steps:**
  1. The user starts the app, or the 24-hour online check runs.
- **App behavior:** a notice that is not modal shows the new version, the
  `notes`, a "Download" button, and a "Later" button.
- **Next steps for the user:**
  - "Download": the browser opens `latest.downloadUrl`.
  - "Later": the notice does not show again for this version. It shows again
    for the next version.
- **Expected result:** the notice shows only for a newer version. A
  development build `1.4.0-SNAPSHOT` does not show it for `1.4.0`.
- **References:** "Update notice", "Rules for the app".

### S-03 Expiry during a session, with exports in the queue

- **User:** honest.
- **Starting state:** normal mode, online, with a server time in the session.
  A project is open. One export runs, and two exports wait in the queue.
- **Steps:**
  1. The expiry moment passes while the user works.
- **App behavior:**
  1. Within 1 minute, the minute tick finds the expiry.
  2. The session has a server time, so the app goes to expired mode at once.
  3. The app saves the open project and stops the player. It hides all tabs
     except Export, and shows the banner and the dialog.
  4. The running export and the two queued exports continue.
  5. The start button of the Export tab has a different color. A click on it
     shows the dialog again and adds no export.
- **Next steps for the user:**
  - Wait: the exports finish. The output files and the completed exports show
    as usual.
  - Close the dialog: the banner stays. The Export tab works.
  - Close the app: the queue stays (B-18). At the next start, the app opens
    in expired mode (path B), and the queue continues.
  - "Download update": see S-05.
- **Expected result:** no lost work. The project is saved. All exports that
  the user started before the expiry finish. The user cannot edit a project
  or start a new export.
- **References:** "Expiry check (local, no network)", "Expired mode".

### S-04 Expiry while the app is closed

- **User:** honest.
- **Starting state:** the app is closed. The expiry moment passes. The flag
  is not set.
- **Steps:**
  1. The user starts the app.
- **App behavior:**
  1. The build looks expired, and the flag is not set, so path A runs.
  2. "Checking the date…" shows before the main window, for less than 1
     second when online.
  3. The check gives "Still expired". The app opens in expired mode and sets
     the flag. The dialog shows the expired text and the date that the app
     used.
- **Next steps for the user:**
  - "Download update": see S-05.
  - "Close": the banner stays. The completed exports show.
  - Close and start the app again: path B. Expired mode shows at once, with
    the dialog in "Checking…".
- **Expected result:** expired mode shows after one online check. No project
  opens.
- **References:** "Start of a build that looks expired", "Expired mode".

### S-05 Update from expired mode

- **User:** honest.
- **Starting state:** expired mode. Exports can wait in the queue.
- **Steps:**
  1. The user clicks "Download update" in the dialog or in the banner.
  2. The user downloads the installer and installs the new build.
- **App behavior:**
  1. The browser opens `latest.downloadUrl`, or the GitHub releases page if
     there is no saved rules file. The app does not quit.
  2. When the user closes the app for the installation, the queue stays
     (B-18).
  3. The new build starts in normal mode, because its expiry is in the
     future. It clears the flag. The queue continues.
- **Next steps for the user:** work as usual.
- **Expected result:** after the update, the app opens in normal mode with
  all tabs. The exports from the queue continue.
- **References:** "Expired mode", B-18 in `backlog.md`.

## Clock errors

### S-06 Clock too far forward, offline

- **User:** honest.
- **Starting state:** normal mode, no network. The clock is set to 2028 by
  mistake. The real expiry is in the future. The flag is not set.
- **Steps:**
  1. The user starts the app.
- **App behavior:**
  1. With no server time, the saved time becomes the system time (2028). The
     build looks expired.
  2. Path A: "Checking the date…". The 3 attempts fail at once.
  3. The app opens in expired mode. The dialog shows "Date used: 3 March
     2028" and "<app name> cannot connect to the update server. It checks
     again every minute. If the date is wrong, correct the clock and connect to the
     internet."
  4. The app makes an online check every minute.
- **Next steps for the user:**
  - Correct the clock and connect: within 1 minute, or at once with "Check
    now", a server time arrives. The saved time becomes the server time, and
    the registry gets the corrected value. The app leaves expired mode, and
    the tabs show again.
  - Connect, but do not correct the clock: the app leaves expired mode in
    the same way. The wrong clock notice shows: "The clock of this computer
    is wrong by N days…". If the user does not correct the clock, the next
    start with no network goes to expired mode again.
  - Stay offline: expired mode stays. This is an accepted risk.
- **Expected result:** recovery within 1 minute after the network is
  available, or at once with "Check now". No lost work. Later starts with no
  network use the corrected saved time.
- **References:** "Time and the clock", "Start of a build that looks
  expired", "Online check", "Wrong clock notice", "Accepted risks".

### S-07 Clock too far forward, online

- **User:** honest.
- **Starting state:** normal mode, online. The clock is set to 2028 by
  mistake. The flag is not set.
- **Steps:**
  1. The user starts the app.
- **App behavior:**
  1. The build looks expired. Path A: "Checking the date…" for less than 1
     second.
  2. The server time shows that the build is not expired. The normal main
     window opens. Expired mode does not show.
  3. The wrong clock notice shows: "The clock of this computer is wrong by
     N days. Correct the clock. If you do not, <app name> can stop while the
     computer is offline."
- **Next steps for the user:**
  - Correct the clock: the next start is normal, with no check window.
  - Do not correct the clock: each start shows the check window for a short
    time and the notice. A start with no network goes to S-06.
- **Expected result:** no flash of expired mode. The notice shows the
  number of days. The session works normally.
- **References:** "Start of a build that looks expired", "Wrong clock
  notice".

### S-08 Clock behind, offline (flat clock battery)

- **User:** honest.
- **Starting state:** normal mode, no network. The clock battery is flat,
  and the clock shows a date in 2020.
- **Steps:**
  1. The user starts the app.
- **App behavior:**
  1. The system time is more than 24 hours earlier than the saved time. The
     app adds 12 hours to the saved time.
  2. The "Clock behind" notice shows, with a "Check now" button.
  3. The app makes an online check every minute.
- **Next steps for the user:**
  - Connect: within 1 minute, or at once with "Check now", a server time
    arrives. The notice closes. The wrong clock notice shows. Windows usually
    corrects the clock itself when it is online.
  - Correct the clock by hand: the next start is normal.
  - Stay offline: each start uses 12 hours of the time that is left. If the
    user starts the app 2 times each day or less, the user loses no time.
- **Expected result:** the user can work. The notice tells the cause and the
  fix. Recovery within 1 minute after the network is available.
- **References:** "Clock behind", "Online check", "Wrong clock notice".

### S-09 New device with a clock earlier than the build date

- **User:** honest.
- **Starting state:** a new device with no saved time and no network. The
  clock shows a date that is more than 24 hours earlier than the build date.
- **Steps:**
  1. The user installs the app and starts it.
- **App behavior:**
  1. The saved time becomes the build moment (the build-date floor).
  2. The system time is more than 24 hours earlier, so "Clock behind"
     applies, as in S-08.
- **Next steps for the user:** as in S-08.
- **Expected result:** as in S-08. A clock earlier than the build date is
  always wrong, so the notice is correct.
- **References:** "Build-date floor", "Clock behind".

## Network

### S-10 Always offline, with a correct clock

- **User:** honest.
- **Starting state:** the computer is never online. The clock is correct.
- **Steps:**
  1. The user uses the app until the expiry.
- **App behavior:**
  - Before the expiry: normal mode. The warning shows from 30 days before
    the expiry (S-01). The user gets no rules and no update notice.
  - At the expiry: path A or the session check finds no connection. The app
    goes to expired mode. The dialog shows "<app name> cannot connect to the
    update server…".
- **Next steps for the user:**
  - Download the new installer on another device, copy it, and install it
    (S-05).
- **Expected result:** no maximum offline period. The build works until its
  expiry.
- **References:** "Results", "Known limits".

### S-11 Firewall or blocked region

- **User:** honest.
- **Starting state:** the computer is online, but a firewall or the region
  blocks `raw.githubusercontent.com`.
- **Steps:**
  1. The user uses the app as usual.
- **App behavior:**
  - The online checks fail. For the app, the computer is offline (S-10).
  - If the firewall drops the connection silently, each attempt waits up to
    10 seconds. Path A waits a maximum of 15 seconds.
- **Next steps for the user:** as in S-10.
- **Expected result:** as in S-10. The start waits a maximum of 15 seconds,
  and only for a build that looks expired. The text says that the app cannot
  connect to the update server, not that the internet does not work.
- **Accepted risk:** the download page is also on GitHub. If GitHub is
  blocked, the user cannot get the update from the app.
- **References:** "Version rules file", "Accepted risks".

### S-12 Office with TLS inspection

- **User:** honest.
- **Starting state:** the office network inspects TLS traffic with its own
  certificate. The certificate is in the Windows store.
- **Steps:**
  1. The user uses the app as usual.
- **App behavior:** the online check trusts the certificate through the
  Windows store. The app behaves as on a normal network.
- **Next steps for the user:** none.
- **Expected result:** the user gets the rules, the update notice, and the
  correction of the clock.
- **References:** "Trust for the HTTPS connection".

## Rules

### S-13 Rule with a future stop date

- **User:** honest.
- **Starting state:** normal mode, online. The author adds a rule for the
  version of the user, with a `stopsOn` in 20 days and a `message`.
- **Steps:**
  1. The app reads the rule (at start, or at the 24-hour check).
- **App behavior:**
  1. The expiry becomes the `stopsOn` of the rule. It is in less than 30
     days, so the warning starts.
  2. The rule `id` is new in this session, so the warning shows at once,
     also if the user closed it before. It shows the `message`.
  3. The app writes the rule `id` and the date to the log.
- **Next steps for the user:** as in S-01.
- **Expected result:** the warning shows at once with the `message`. If the
  `stopsOn` is more than 30 days away, the warning starts 30 days before it.
- **References:** "Rules for the app", "Expiry warning".

### S-14 Rule that stops the version at once

- **User:** honest.
- **Starting state:** normal mode, online, with a project open. The author
  adds a rule for the version with a `stopsOn` in the past, for example
  because the version can damage project files.
- **Steps:**
  1. The app reads the rule (at start, or at the 24-hour check).
- **App behavior:**
  - During a session: the session has a server time, so the app goes to
    expired mode at once, as in S-03. The dialog shows the `message`.
  - At start: path A gives "Still expired", as in S-04.
- **Next steps for the user:** as in S-03 and S-05.
- **Expected result:** the version stops at the next expiry check after the
  read, with no warning. The project is saved. The exports continue.
- **References:** "Fields", "Accepted risks".

### S-15 Wrong rule, then the fix of the author

- **User:** honest.
- **Starting state:** the author pushes a rule by mistake. Its range
  includes the newest version.
- **Steps:**
  1. Apps read the wrong rule and go to expired mode.
  2. The author pushes the fix. GitHub caches the file for about 5 minutes.
- **App behavior:**
  - Open apps in expired mode: the server answers "Still expired" until the
    fix arrives, so they check every 15 minutes. After the fix, the next
    check gives "Not expired", and the app leaves expired mode. The tabs and
    the open project show again.
  - Apps that read the wrong rule and closed: the flag is set, so path B
    runs at the next start. The check gives "Not expired" within seconds.
  - Apps that were offline and saved the wrong file: path A gives "No
    connection", and expired mode shows. They recover within 1 minute after
    they connect.
- **Next steps for the user:** wait, or click "Check now" after the fix.
- **Expected result:** an open app leaves expired mode within about 20
  minutes after the fix (5 minutes of cache and 15 minutes of interval), or
  at once with "Check now" after the cache. No lost work.
- **References:** "Online check", "Start of a build that looks expired".

## Environment

### S-16 Sleep with the app open

- **User:** honest.
- **Starting state:** normal mode. The app is open. The expiry is in 2 days.
- **Steps:**
  1. The user puts the laptop to sleep for 3 days.
  2. The user wakes the laptop.
- **App behavior:**
  1. The run time includes the sleep (`GetTickCount64`).
  2. The first minute tick after the wake finds the expiry.
  3. With a server time in the session: expired mode at once (S-03). With
     no server time: one online check first, then expired mode.
- **Next steps for the user:** as in S-03.
- **Expected result:** expired mode within 1 minute after the wake. The
  manual check before the first release confirms that the run time includes
  the sleep.
- **References:** "Run time", "Expiry check (local, no network)".

### S-17 Second start while the app runs

- **User:** honest.
- **Starting state:** the app runs in normal mode or in expired mode. Its
  window can be minimized or on another desktop.
- **Steps:**
  1. The user starts the app again in the same Windows account.
- **App behavior:**
  1. The second process cannot get the instance lock (B-19).
  2. It shows "<app name> is already running. Use the open window. If you
     cannot see it, look in the taskbar." and quits.
  3. The second process does not write the saved time. It does not add the
     12 hours of "Clock behind", also when the clock is behind.
  4. The first instance does not change.
- **Next steps for the user:**
  - Use the open window.
  - If the first instance does not respond, stop it with End task. Windows
    releases the lock, and the next start works (S-19).
- **Expected result:** only one instance runs for each Windows account.
  Another Windows account on the same device can run its own instance. If
  the app cannot lock the file, it starts anyway (fail open), and the write
  rules for more than one instance protect the saved time.
- **References:** "Time and the clock", B-19 and B-18 in `backlog.md`.

### S-18 Reinstall, or install an old installer

- **User:** honest.
- **Starting state:** the user has a build installed. The saved time is in
  the registry.
- **Steps:**
  1. The user uninstalls the app.
  2. The user installs the same build again, or an old installer.
- **App behavior:**
  - The uninstall does not delete the preferences
    (`HKCU\Software\JavaSoft\Prefs`). The saved time and the flag stay.
  - Same build: the app starts in the same state as before.
  - Old installer: its expiry is earlier. If the saved time is after it, the
    build looks expired. Path A (or path B, if the flag is set) runs, and the
    app opens in expired mode.
- **Next steps for the user:** install the newest build (S-05).
- **Expected result:** a reinstall does not reset the saved time. An old
  installer does not work after its expiry.
- **References:** "Known limits".

### S-19 App closed with End task, or a crash

- **User:** honest.
- **Starting state:** normal mode. Exports wait in the queue.
- **Steps:**
  1. The app stops with no normal close (End task, a crash, or a power
     failure).
  2. The user starts the app again.
- **App behavior:**
  - The run time after the last write of the saved time is lost (a maximum
    of 5 minutes). The user does not see an effect.
  - The queue stays (B-18). The running export starts again from the
    beginning.
- **Next steps for the user:** work as usual.
- **Expected result:** the expiry does not change for the user. The queue
  continues.
- **References:** "Time and the clock", B-18.

## Cheaters

### C-01 Clock back, offline

- **User:** cheater.
- **Starting state:** the expiry is in 20 days. The user turns off Wi-Fi and
  sets the clock 1 year back.
- **Steps:**
  1. The user starts the app and works.
- **App behavior:**
  - At each start, "Clock behind" adds 12 hours to the saved time and shows
    the notice.
  - While the app runs, the run time moves the saved time forward, also
    during sleep.
- **Next steps for the user:** none. To extend the use, the user must take
  an action after the line, for example delete the saved time in the
  registry.
- **Expected result:** the build expires after the remaining time: 20 days
  = 480 hours, used by run time and by 12 hours for each start. A user who
  starts the app 2 times each day gets 20 calendar days or less.
- **References:** "Clock behind", "Run time".

### C-02 Clock back at each start, with End task

- **User:** cheater.
- **Starting state:** no network. Before each start, the user sets the clock
  to the same date by hand.
- **Steps:**
  1. The user starts the app, works, and stops the app with End task.
  2. The user repeats step 1.
- **App behavior:**
  - The app writes the saved time every 5 minutes of run time. Each session
    loses a maximum of 5 minutes.
  - The saved time grows with the counted run time. When it is more than 24
    hours after the false date, "Clock behind" starts: each start then adds
    12 hours.
- **Next steps for the user:** none with normal actions.
- **Expected result:** the trick gives a maximum of 5 minutes for each
  session. The remaining time still runs out. To use the app for 1 hour, the
  user must set the clock and use End task 12 times.
- **References:** "Time and the clock" (the 5-minute write).

### C-03 Time tool for one program

- **User:** cheater.
- **Starting state:** the user starts the app with a tool that changes the
  time only for the app (for example, a "trial reset" tool).
- **Steps:**
  1. The user starts the app through the tool, with a date before the
     expiry.
- **App behavior:** the file time probe shows the real time of Windows. The
  system time is the later of the Java clock and the file time. The app
  uses the real time.
- **Next steps for the user:** none.
- **Expected result:** the tool has no effect. The app behaves as with the
  real clock.
- **References:** "File time probe".

### C-04 New device with an old installer, offline

- **User:** cheater.
- **Starting state:** a new device with no saved time. The user has an old
  installer. The device stays offline. The clock is set to one day after the
  build date of the installer.
- **Steps:**
  1. The user installs the old build and uses it.
- **App behavior:**
  - The saved time becomes the build moment. The system time is later, so
    "Clock behind" does not apply.
  - The build works until the clock gets to its expiry.
- **Next steps for the user:** none with normal actions on the same device.
- **Expected result:** a maximum of one build life (6 months or less) for
  each device. This is an accepted limit ("Known limits").
- **References:** "Build-date floor", "Known limits".

### C-05 New Windows user account with an old installer

- **User:** cheater.
- **Starting state:** as in C-04, but on the same device in a new Windows
  user account. The saved time is in `HKCU`, so the new account has none.
- **Steps:**
  1. The user creates a new Windows user account.
  2. The user sets the clock to one day after the build date of an old
     installer and keeps the device offline.
  3. The user installs the old build and copies the projects into the new
     account.
  4. When the build expires, the user repeats steps 1–3.
- **App behavior:** as in C-04, for each new account.
- **Expected result:** accepted limit (decided on 2026-10-02). A maximum of
  one build life (6 months or less) for each new account, with no limit to
  the number of accounts. The friction is high: the device stays offline
  with a wrong clock for months, and the projects move to each new account.
- **References:** "Known limits".
