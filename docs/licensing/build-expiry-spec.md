# Build Expiry and Version Rules: Design Spec

This spec is item L-5.1 of
`elv2-migration-plan.md`. Assess its measures with `threat-model.md`.
`expiry-scenarios.md` gives the user scenarios that the spec must support.

## Goal

Now the app is free, and all features are available. Later, the author can
charge money for some features or for the full app. When this occurs, old free
builds must stop. Also, the installer of an old build must not work forever on
new devices.

Each build has an expiry date. Before the date, the user must install a newer
build. An update check tells the user that a newer build is available. A rules
file on a server can stop some versions earlier and tell the user why.

## Decisions

| Item | Decision |
|---|---|
| Build expiry | Build date plus 6 months |
| Warning | From 30 days before the expiry |
| Expired build | Expired mode: only the Export tab, a banner, and a dialog that the user can close. The export queue continues. The app does not open projects or start new exports. |
| Expiry during a session | The app checks the expiry while it runs. It goes to expired mode at once. The running export and the queued exports continue. |
| Read-only mode | No. Expired mode does not show projects. |
| Update check | Yes, in the first release. It shows a notice and opens the download page. |
| Automatic update | No. Do it after the first release. |
| Version rules from the server | Yes, in the first release |
| Offline use | The build expiry and the last saved rules apply. No grace period is necessary. |
| License keys | No. Do them before the first paid release. |
| Entitlement layer | No. Design it with the pricing model. |

All measures of this spec must be in the first release. A later release
cannot add a measure to a build that is already released. Example: if the
first release has no "Clock behind", a user of that build can move the clock
back, block the network, and use the build for years, also after a paid
release. The same
applies to each field of the rules file that the app reads (see "Changes to
the file format"). Do not release a build with only some of the measures.

## Build expiry

- The build generates a Kotlin source file with constants, for example
  `BuildInfo.kt`:
  - `const val BUILD_DATE: String`: the build date (UTC) in the format
    `yyyy-MM-dd`, for example `"2026-10-02"`. The value comes from
    `${maven.build.timestamp}` with `maven.build.timestamp.format` set to
    `yyyy-MM-dd`. Maven gives this timestamp in UTC.
  - `const val VERSION: String`: the project version (`${revision}`).
  - Use a Maven plugin that fills a source template, for example
    `templating-maven-plugin`. The Kotlin compiler must compile the generated
    folder.
- The app reads `BUILD_DATE` with `LocalDate.parse`. A date text is not more
  difficult to change than a number: both need a bytecode edit, which is
  after the line (see `threat-model.md`). A number would need a script in the
  build, because Maven cannot format a timestamp as a number of days.
- The Kotlin compiler copies each `const val` into the classes that use it.
  To change a value, a person must edit the bytecode. This is clearly a change
  of the checks, not a change of a setting.
- The build stores only the build date. The app calculates the expiry date:
  the build date plus 6 calendar months. Do not store the expiry date.
- "6 calendar months" uses the rules of the calendar. If the day does not
  exist in the target month, the result is the last day of that month. For
  example, 31 August plus 6 months is 28 February (29 February in a leap
  year). Java `LocalDate.plusMonths` and PowerShell `DateTime.AddMonths` both
  calculate this. The app and `Validate-Release.ps1` must get the same result.
- For the exact moment of the expiry, see "Expiry moment".
- Do not put the date or the version in these places:
  - a resource file in the JAR, for example a `.properties` file. The install
    is per user, so the user can write to the JAR. An archive tool (for example
    7-Zip) can edit a text file in the JAR in one step.
  - a system property or the jpackage `.cfg` file. The user can edit the
    `.cfg` file with a text editor.
- Do not add a switch that turns off the expiry. Tests set the clock and the
  dates through constructor parameters. A property or an environment variable
  is a bypass.
- Development builds also expire. They are built again often, so this has no
  effect on the work.
- A build in IntelliJ does not run the Maven phase `generate-sources`. Then
  the IDE uses the `BuildInfo.kt` from the last Maven build, with its old
  build date. If no Maven build ran for 6 months, a run from the IDE opens in
  expired mode. The fix is one Maven build, for example
  `mvn -DskipTests package`. When you implement this spec, add this
  information to "Getting started (development)" in `README.md`.
- The JAR has a small main class that prints the build date, the calculated
  expiry date, and the version, for example
  `org.litvin.license.BuildInfoPrinter`. It reads the same constants and the
  same calculation as the app. It has no options.
- `Validate-Release.ps1` runs this class with the bundled runtime. It must fail
  in these cases:
  - The class does not run, or it prints no build date or no version.
  - The expiry date is not 6 calendar months after the build date.
  - The build date is more than 7 days before the day of the check. Thus, an
    old build cannot be released by mistake.
  - The build date is later than the day of the check plus 1 day. The 1 day
    covers the time zones. Thus, a wrong build date (for example, a value set
    with `-D` for a test) cannot give a build a longer life. The check uses
    the clock of the same machine as the build, so it cannot find a wrong
    clock. The release runs on GitHub runners, and their clocks are correct.
  - The version is not the jpackage `--app-version`.
  - The bundled runtime does not contain one of the modules that the request
    for the rules file needs (see "Trust for the HTTPS connection"). The
    script gets the list with `runtime\bin\java.exe --list-modules`.

## Version rules file

The app reads one static JSON file over HTTPS. The file is in this repository
at `release/version-policy.json`. The app reads the raw GitHub URL of the file
on the `master` branch.

The URL must work only for 6 months after the last build that uses it,
because each build expires after that time. Thus, a later move of the file is
safe.

- The app waits a maximum of 10 seconds for the file. If the read fails, the
  app shows no error, except in the states that "Online check" names. It
  tries again on the schedule in "Online check".
- The app sets `java.net.useSystemProxies=true`, so it uses the proxy of
  Windows. If the read still fails (for example, behind a firewall), the build
  expiry and the saved file apply. The user does not get the update notice or
  new rules.

Example:

```json
{
  "schema": 1,
  "latest": {
    "version": "1.4.0",
    "downloadUrl": "https://github.com/zipper2110/tennis-record/releases/latest",
    "notes": "Faster export. New score overlay."
  },
  "rules": [
    {
      "id": "2027-01-project-save",
      "fromVersion": "1.2.0",
      "toVersion": "1.2.1",
      "stopsOn": "2027-01-15",
      "message": "This version can damage project files when it saves. Update to 1.2.2 or later."
    }
  ]
}
```

### Fields

- `schema`: the format version. The app ignores a file with a schema that it
  does not know.
- `latest`: the newest release. The update check uses it. This field is
  optional.
- `rules`: a list of rules. Each rule stops a range of versions on a date.
  - `id`: a unique name. The app uses it to show the warning again when a
    new rule makes the expiry earlier (see "Expiry warning"), and in the log.
  - `fromVersion`, `toVersion`: the range (both ends included). `toVersion`
    is required. `fromVersion` is optional: with no `fromVersion`, the rule
    stops all versions up to `toVersion`. The app ignores a rule with no
    `toVersion`. Thus, a mistake in the file cannot stop a version that is
    newer than the rule, also a version that is not released yet.
  - `stopsOn`: the date when the versions stop (see "Expiry moment"). A date
    in the past stops the versions at the next expiry check.
  - `message`: the reason. The app shows it in the warning and in the dialog
    of expired mode. This field is optional. English only until localization (B-11).

### Rules for the app

- The app version is `BuildInfo.VERSION` (see "Build expiry"). The app does
  not read the version from a system property, from the `.cfg` file, or from
  the JAR manifest. If it did, a user could set the version to `99.0.0` in
  the `.cfg` file. Then no rule with a `toVersion` would stop the build, and
  the update notice would not show.
- Now the code reads the version in these places. Change all of them:
  - `AppInfo.version` reads the system property `bananashot.version`. If the
    property has no value, it reads `Implementation-Version` from the JAR
    manifest. If that has no value, the version is `"development"`. Change
    `AppInfo.version` to `BuildInfo.VERSION`, and remove both fallbacks.
    `BuildInfo.VERSION` always has a value, because each build generates it.
  - `AppInfo.displayName` shows only the name when the version is
    `"development"`. Remove this case. The name always shows with the
    version, for example "BananaShot 1.4.0-SNAPSHOT" for a development build.
  - `EnabledAnalytics` reads the system property `bananashot.version`, with
    the default `"1.0.0"`. Change it to `BuildInfo.VERSION`.
  - `Build-AppImage.ps1` writes `-Dbananashot.version` into the `.cfg` file.
    Remove this option.
- Versions compare as numbers (`MAJOR.MINOR.PATCH`). The app reads the
  numbers at the start of the version and ignores the rest. A missing number
  is 0. Examples: `1.3-SNAPSHOT` is `1.3.0`. `1.2.1-beta` is `1.2.1`. A
  version that does not start with a number is `0.0.0`.
- Thus, a build from source with a development version is not outside the
  rules. A rule that stops old versions also stops it.
- The default version in `pom.xml` must always be the next version with
  `-SNAPSHOT`, for example `1.4.0-SNAPSHOT`. Then the development builds of the
  author are newer than all stopped versions. The update notice uses the same
  comparison.
- The effective expiry is the earliest date of: the build expiry, and the
  `stopsOn` of each rule that matches the app version.
- When a rule gives the effective expiry, the app writes the rule `id` and
  the date to the log. It does this when the effective expiry changes, and
  when the rule stops the version. The log helps with bug reports.
- The file can only make the expiry earlier. It cannot make it later. Thus, a
  false file cannot extend the use of a build.
- The file has no signature. HTTPS is sufficient for the file, because a false
  file cannot extend the use of a build. A false server time from the
  response can extend the use of a build. To send it, a person must add a
  certificate and use a proxy. These actions are after the line (see
  `threat-model.md`), so the app does not protect against them.
- The app ignores a rule that matches `latest.version`. Such a rule is always
  a mistake, because it also stops the version that the user must download.
- The app saves the last valid file in its data folder. If the app cannot
  read the server, it uses the saved file. A new valid file replaces the saved
  file. The app ignores a file that is not valid.
- The app ignores fields that it does not know. It still reads the fields
  that it knows.

### Expiry moment

- An expiry date (the build expiry or a `stopsOn`) is the first day on which
  the version does not work.
- The version stops at 00:00 UTC on that date. All computers use the same
  moment.
- The warning starts 30 × 24 hours before this moment.
- The user interface shows the moment in the local time of the computer, with
  the hour. Example: "This version works until 29 March 2027, 16:00." A date
  with no hour can be wrong by one day in the local time zone.

### Changes to the file format

If the file gets a new `schema`, all released builds ignore the file until
they expire (up to 6 months). They get no new rules and no update notice.
Thus:

- Add new fields, and keep `schema: 1`. A released build ignores the new
  fields.
- A new field is safe only if a released build that ignores it still does
  the correct thing. A field that limits a rule is not safe. Example: a rule
  gets `"onlyOn": "Windows 10"`. A released build ignores this field and stops
  the versions on all computers. For such a rule, use a new list that
  released builds do not read (for example `rulesV2`), or a new file.
- For a change that released builds cannot read correctly, add a new file
  (for example `release/version-policy-v2.json`). Do not change the schema of
  the old file. New builds read the new file. Keep the old file up to date
  until the last build that reads it expires (6 months after its build date).
  Then delete the old file.
- The server time does not depend on the file format (see "Time and the
  clock").

## Time and the clock

The app calculates the current time as follows:

- The system time is the later of: the Java clock
  (`System.currentTimeMillis`) and the file time from the probe (see "File
  time probe").
- The app keeps a saved time in its preferences. It writes the saved time at
  each expiry check, every 5 minutes of run time, and when it closes.
- The 5-minute write is necessary. Without it, a user can set the clock back
  to the same date at each start, work offline for less than 1 hour, and stop
  the app with End task. Then the run time is never saved, the saved time
  stays near the false date, and "Clock behind" never starts. With the
  5-minute write, each session loses at most 5 minutes of run time.
- While the app runs, it adds the run time to the saved time. The run time is
  the change of a counter that ignores changes of the system clock and counts
  the time while the computer sleeps. See "Run time".
- The build moment is 00:00 UTC on the build date (`BUILD_DATE`). The
  real time is never earlier than the build moment. See "Build-date floor".
- At start, the saved time becomes the later of: the saved time and the
  build moment. See "Build-date floor".
- At start, if the system time is more than 24 hours earlier than the saved
  time, the clock is behind. See "Clock behind".
- Only one instance of the app runs for each Windows account (B-19 in
  `backlog.md`). At start, the app takes the lock of B-19 before all steps of
  this section: before it reads or writes the saved time, before the
  build-date floor, and before "Clock behind". A process that does not get
  the lock (a second start) shows the message of B-19 and quits. It does not
  write the saved time, and it does not add the 12 hours of "Clock behind".
  If it did, each second start (for example, a double-click on the icon while
  the app runs) would use 12 hours of the time that is left.
- If the lock of B-19 fails, more than one instance can run.
  For this case, the writes of the saved time follow these rules. The first
  write of the saved time after a new server time replaces the stored value. Thus, the server
  correction of a time that is too far forward stays after the app closes.
  All other writes keep the later of the stored value and the value of the
  instance. Thus, an instance with no new server time cannot move the stored
  time back.
- Accepted: if an instance with a time that is too far forward runs together
  with an instance that got a server time, the first instance can write the
  false time again. It corrects itself at its next server time (in 24 hours
  or less). This needs a failed lock, two instances, and a wrong clock, so it
  is very rare.
- A server response is any HTTPS response from the host of the rules file,
  with any HTTP status (for example 404). The file in the response can be not
  valid or have an unknown schema. The `Date` header is correct in all these
  cases, and the trust in the host is the same. Thus, the correction of the
  clock continues if the file moves, breaks, or changes its schema. A
  connection that fails (no network, timeout, TLS error) is not a server
  response.
- The app does not follow redirects. A redirect (HTTP 3xx) from the host of
  the rules file is a server response with no valid file. Its `Date` header
  is a server time. Thus, the server time always comes from the host of the
  rules file, and never from a host that a redirect names.
- The server time is the `Date` header of a server response.
- Until the first server time in the session: if the system time is later
  than the saved time, the saved time becomes the system time.
- After a server time:
  - The saved time becomes the server time. Thus, the server time can move
    the saved time back, for example after a system clock that was too far
    forward.
  - For the rest of the session, the system time does not change the saved
    time. Only the run time moves it forward. Thus, a system clock that is
    too far forward has no effect after a server time.
- The current time is the saved time.

### Calculation of the saved time

The rules above give one calculation. Implement it as follows. Use this
section if a rule above seems to give a different result.

The app keeps an anchor in memory: an anchor time `A` and the counter value
`C_A` at that time. `C(now)` is the counter of "Run time".

- The saved time at each moment: `saved = A + (C(now) − C_A)`.
- At start, after the lock of B-19:
  1. `A` = the stored saved time (or no value), and `C_A = C(now)`.
  2. Build-date floor: if `A` has no value or is earlier than the build
     moment, `A` = the build moment.
  3. Clock behind: if the system time is more than 24 hours earlier than `A`,
     `A = A + 12 hours`.
- Before the first server time in the session, at each calculation (each
  tick, each expiry check): if the system time is later than `saved`, then
  `A` = the system time, and `C_A = C(now)`.
- When a server time arrives: `A` = the server time, and `C_A` = the counter
  value when the response arrived. After that, the system time does not
  change `A` for the rest of the session.
- Each write stores `saved` in the preferences, with the rules for more than
  one instance (see above).

Notes:

- The calculation keeps a forward move of the system clock also after the
  clock moves back in the same session. Example: the system time is 10 days
  ahead for 1 minute, and then it moves back. `saved` stays 10 days ahead and
  continues with the run time. A calculation such as
  `max(stored time + run time, system time)` loses this forward move, so do
  not use it.
- The difference between the counter and a clock that Windows corrects (NTP)
  is some seconds each day. It has no effect on the 24-hour limits.

The app does not protect the server time against a false response. A false
server time needs a certificate and a proxy, and both are after the line (see
`threat-model.md`). A wrong server time from the real host (for example, a
server with a wrong clock) has an effect only until the next server response.

### Run time

The run time must count the time while the computer sleeps or hibernates. If
it does not, the time stops while the laptop sleeps. Then a user who sets
the clock back, works offline, and never closes the app gets only the awake
time counted. Each of these actions is a normal user action (see
`threat-model.md`).

`System.nanoTime` is not sufficient. On Windows it uses
`QueryPerformanceCounter`, and it is not certain that this counter counts the
sleep. The result can be different on different computers.

- The app measures the run time with the Windows function `GetTickCount64`
  (`Kernel32.INSTANCE.GetTickCount64()` in jna-platform). It counts the
  milliseconds from the start of Windows. It includes the time in sleep and
  hibernation. Changes of the system clock have no effect on it.
- The run time is the change of this counter after a known moment, for
  example the start of the session or the moment of the last server time.
  The app does not save the counter, because it starts again from 0 at each
  start of Windows.
- The app does not compare the system time with the run time to find a
  sleep. A user who corrects a clock that was months behind, while the app
  runs, would then move the saved time months forward and lock the app.
- If the call fails (for example, the native library does not load), the app
  uses `System.nanoTime`. Then a sleep can be lost from the run time. The app
  writes this to the log one time and shows no error.
- Tests give the counter through a constructor parameter.

### Build-date floor

The real time is never earlier than the build moment. Without this rule, a
new device, or a device with no saved time, can have its clock set years
back. The app has no saved time, so it cannot see that the clock is behind.
Then the build works until the clock gets to the build expiry, which can be
years.

- At start, the saved time becomes at least the build moment. The app does
  this before the "Clock behind" check.
- Thus, a system clock that is earlier than the build moment by more than 24
  hours starts the "Clock behind" rule, also on a new device.
- Harm: none. A clock that is earlier than the build date is always wrong.
  An honest user with such a clock (for example, a flat clock battery) gets
  the "Clock behind" notice, which tells the cause and the fix.
- Result: a user who sets the clock back on a new device gets at most the
  time from the build moment to the build expiry (6 months or less). To get
  this time again, the user must delete the saved time in the registry. This
  is after the line.

### Clock behind

Without this rule, a user can move the clock back and block the network for
the app (for example, with a firewall rule). Then only the run time moves the
saved time forward. For example, 20 days until the expiry become 480 hours of
use. For a user who uses the app a few hours each week, this is some years.

- At start, before the first server time: if the system time is
  more than 24 hours earlier than the saved time, the app adds 12 hours to the
  saved time. It does this at each start while the clock is behind. The run
  time moves the saved time forward as usual.
- Only a process that has the lock of B-19 adds the 12 hours (see "Time and
  the clock"). A second start that quits adds nothing.
- Accepted: if the lock fails (fail open), each start adds 12 hours, also a
  second start while the app runs. This needs a failed lock and a clock that
  is behind, so it is very rare.
- The limit of 24 hours prevents false results from small clock errors, for
  example a computer that uses local time in its hardware clock.
- A server time sets the saved time to the server time. Thus, the 12 hours
  have no effect for an online user.
- The app shows a notice that is not modal: "The date on this computer is
  earlier than the date that <app name> used before. Check the clock of the
  computer and connect to the internet. Until then, each start of <app name>
  uses 12 hours of the time that is left for this version." The user can close
  the notice for the current session.
- The notice has a "Check now" button. It starts an online check at once
  (see "Online check"). While the check runs, the button shows "Checking…"
  and the user cannot click it. If the check gives no connection, the notice
  shows "<app name> cannot connect to the update server".
- When a server time arrives, the notice closes. Its text is then not true.
- In expired mode, the app does not show this notice. It still adds the 12
  hours. The banner of expired mode tells the user the cause and the fix.
- Result: for a user who moves the clock back, the time that is left is a
  number of starts (2 starts for each day), not a number of run hours.
- Harm: an offline user with a broken clock (for example, a flat clock
  battery) loses time faster if they start the app more than 2 times a day.
  The notice tells them the cause and the fix.

### File time probe

Some tools change the time only for one process. Then the Java clock of the
app is false, but the rest of Windows uses the correct time.

- At each expiry check, the app writes a small file in its data folder (for
  example `time-probe`). Then it reads the last-modified time of the file.
- Windows sets this time from its own clock when the file is written. A tool
  that changes the time for one process does not change it.
- The system time is the later of the Java clock and the file time (see the
  start of this section).
- If the app cannot write or read the file, it uses the Java clock only. It
  shows no error.
- Harm: none. For a normal computer, the file time and the Java clock are the
  same.

### Trust for the HTTPS connection

- The request for the rules file accepts a certificate if one of these
  stores trusts it: the store of the bundled runtime
  (`<java.home>/lib/security/cacerts`) or the Windows store (`Windows-ROOT`).
- The Windows store is necessary for offices that inspect TLS traffic. Their
  certificate is in the Windows store, but not in the bundled store. Without
  the Windows store, these users get no rules, no update notice, and no
  correction of the clock.
- The bundled store is also necessary. Windows gets some root certificates
  only when a program asks for them, so `Windows-ROOT` in Java can miss a root
  that GitHub uses.
- The bundled runtime must contain these modules:
  - `jdk.crypto.mscapi`: the `Windows-ROOT` key store.
  - `jdk.crypto.ec`: the elliptic-curve algorithms for TLS. In Java 17, they
    are not in `java.base`.
  - `java.net.http`: `HttpClient`.
- jpackage builds the runtime with jlink. jlink does not always add a module
  that only supplies a provider and exports no package, for example
  `jdk.crypto.mscapi`. If a module is missing, the request fails with no
  error to the user (fail open), and the users of an office with TLS
  inspection silently get no rules (S-12 in `expiry-scenarios.md`). Thus,
  `Validate-Release.ps1` checks the modules (see "Build expiry").
- If a module is missing, add it with the jpackage option `--add-modules` in
  `Build-AppImage.ps1`. This option replaces the default list of modules, so
  the list must also contain all other modules that the app needs.
- Do not pin the GitHub certificates. GitHub can change its certificate
  authority. Then all users would lose the server time and the rules.
- A person who adds a certificate can use a proxy to send a false server
  time. This is after the line. The app does not protect against it.

### Results

- If the user moves the clock back, the time still moves forward while the
  app runs, also while the computer sleeps. Each start also uses 12 hours.
  The build expires after at most the remaining time of use.
- A clock that is set earlier than the build date starts the "Clock behind"
  rule, also on a new device.
- A wrong server time from the real host has an effect only until the next
  server response.
- A tool that changes the time only for the app has no effect, because the
  file time shows the correct time.
- No maximum offline period applies. An offline user with a correct clock can
  use the app until the build expiry.
- A system clock that was set too far forward by mistake does not lock the
  user out when the app is online.
- The dialog and the banner of expired mode show the date that the app used.
  Thus, the user can see if the clock of the computer is wrong.
- In expired mode, the app still reads the rules file: every minute while it
  has no connection, and at once when the user clicks "Check now". If the
  server time shows that the build has not expired, the app leaves expired
  mode (see "Expired mode"). Thus, a wrong clock does not block the app when
  it is online.
- At start, a build that was not expired in the last session makes an online
  check before it goes to expired mode. Thus, a clock that is too far forward
  does not show expired mode for a moment at each start.
- After a server time, the app tells the user if the clock of the computer is
  wrong (see "Wrong clock notice").

## When the app does the checks

### Expiry check (local, no network)

- At start: the app calculates the effective expiry from the build expiry and
  the saved rules file. It does this before it shows the main window and before
  it opens a project. If the build looks expired, see "Start of a build that
  looks expired".
- During the session: the app does the same check every hour, when the user
  opens a project, and when the user starts an export. Thus, an app that is
  never closed also expires.
- The check for opening a project is in the function that opens a project,
  not in the user interface. The check for a new export is in the function
  that adds the export to the queue. Thus, a new way to open a project or to
  start an export (for example, a file association) gets the check with no
  extra work.
- The worker of the export queue does not check the expiry. An export that
  runs after the expiry contains only work from before the expiry: the job
  keeps all its data (the points, the overlays, the scoreboard, and the color,
  crop and rotate adjustments) from the moment when the user added it to the
  queue.
- Java schedulers use `System.nanoTime`, so an hourly timer can fire up to 1
  hour late after a sleep. Thus, the app uses a tick each minute. The tick
  calculates the current time in memory (see "Time and the clock") and
  compares it with the effective expiry and with the start of the warning. If
  the current time passed one of them, the app does the expiry check at once.
  If not, the app does the check when 1 hour of run time has passed since the
  last check. The run time includes the sleep (see "Run time"), so the first
  tick after a long sleep also does the check. The tick does not read or
  write files. When 5 minutes of run time have passed since the last write of
  the saved time, the tick writes the saved time to the preferences.
- If the build expires during a session, the app saves the open project and
  goes to expired mode (see "Expired mode"). The running export and the
  queued exports continue.
  - If the session has a server time, the expiry is real (the run time or a
    rule). The app goes to expired mode at once.
  - If the session has no server time, the expiry can come from a clock that
    moved forward. The app first makes one online check with the retries of
    path A (see "Start of a build that looks expired"). During the check, the
    app stays in normal mode. If the check shows that the build has not
    expired, the app stays in normal mode. If not, it goes to expired mode.

### Start of a build that looks expired

The app keeps a flag in its preferences: "expired mode in the last session".
The app sets the flag when it goes to expired mode. It clears the flag when
it leaves expired mode, and at each start in normal mode. Thus, a flag from
an old build (for example, before an update) does not select path B for the
new build.

- Path A: the build looks expired, and the flag is not set.
  - Before the main window opens, the app shows a small window: "Checking the
    date…". Thus, the user cannot open a project during the check.
  - The app makes an online check with retries: a maximum of 3 attempts, 2
    seconds apart. The check stops after 15 seconds in total, also if an
    attempt still waits for its 10-second limit (for example, behind a
    firewall that drops the connection).
  - Not expired: the normal main window opens. If the clock is wrong, the
    wrong clock notice shows.
  - Still expired, or no connection: the app opens in expired mode. The
    dialog shows the result of the check.
- Path B: the build looks expired, and the flag is set.
  - The app opens in expired mode at once. The dialog shows "Checking…" and
    the app makes an online check.
  - No connection: the dialog shows "<app name> cannot connect to the update
    server". The checks continue every minute.
  - Still expired: the dialog shows the text of an expired build.
  - Not expired: the dialog closes, and the app leaves expired mode.
- The flag selects only the path. It never changes the result of the expiry
  check. A user who deletes or changes the flag changes only which path
  runs. Thus, the flag needs no protection.

### Online check

An online check is a read of the rules file. It has one of these results:

- No connection: the read fails (no network, a timeout, a TLS error).
- Still expired: a server response arrives, and the expiry check after it
  still gives an expired build.
- Not expired: a server response arrives, and the expiry check after it does
  not give an expired build.

When the app does an online check:

- At start, in the background. It does not make the start slower. (For a
  build that looks expired, see "Start of a build that looks expired".)
- When the user clicks "Check now" in the expired dialog, the banner of
  expired mode, or the "Clock behind" notice. The check runs at once, and the
  schedule starts again from this check.
- After each check, on this schedule:

| State | Result of the last check | Next check |
|---|---|---|
| Expired mode, or "Clock behind" with no server time in the session | No connection | After 1 minute |
| Expired mode | Still expired | After 15 minutes |
| Normal | No connection | After 15 minutes |
| Normal | A server response with no file that the app can use | After 15 minutes |
| Normal | A valid file, or a file with an unknown schema | After 24 hours |

- "A server response with no file that the app can use" is an HTTP status
  that is not 200 (for example 404, 429, a server error, or a redirect), or
  a file that is not valid. The response still gives a server time.
- The interval of 15 minutes after such a response: a short error of GitHub
  then delays the rules and the update notice by 15 minutes, not by one day.
- A file with an unknown schema gets 24 hours. The author changed the schema
  on purpose (see "Changes to the file format"), so a short interval gives
  only load on the server.

- The interval of 1 minute costs nothing on the server: with no network, the
  request does not get to GitHub. The user waits for the network in this
  state, so a short interval is necessary.
- The interval of 15 minutes after "Still expired" limits the load on the
  server. A real expiry does not change in 15 minutes. Only a fix of a wrong
  rule changes it, and the user can click "Check now".
- With the interval of 24 hours, an app that is never closed also gets new
  rules.
- Only one check runs at a time. While a check runs, "Check now" shows
  "Checking…" and the user cannot click it.
- After each check, the app does the expiry check again.

## User interface

In the texts of this spec, `<app name>` is `AppInfo.NAME` (now "BananaShot").
The code uses `AppInfo.NAME`. Do not write the name into the texts, because
the name can change.

### Update notice

- When `latest.version` is newer than the app version, the app shows a notice
  that is not modal. The notice shows the version, the `notes`, a "Download"
  button, and a "Later" button.
- "Download" opens `latest.downloadUrl` in the browser.
- "Later" hides the notice for this version. The notice shows again for the
  next version.

### Expiry warning

- From 30 days before the effective expiry, the app shows a warning that is
  not modal. It shows the warning at start, and after that one time on each
  calendar day while it runs. The warning shows the moment of the expiry in
  local time (see "Expiry moment"), the rule `message` if there is one, and a
  "Download update" button.
- "Calendar day" is the date of the current time (see "Time and the clock")
  in the local time zone of the computer. The app keeps in memory the date
  when it last showed the warning. The hourly expiry check shows the warning
  when the date is now later. Thus, the warning shows again in the first hour
  after local midnight. The app does not save this date: each start shows
  the warning.
- The user can close the warning. It then stays hidden until the next
  calendar day.
- If a rule that the app has not seen in this session makes the effective
  expiry earlier, the warning shows at once, also if the user closed it. The
  app finds new rules by their `id`. It keeps the seen `id` values in memory
  for the session only. It does not save them.

### Expired mode

When the effective expiry is in the past, the app is in expired mode. The
mode is the same at start and during a session. The user can close and start
the app as usual. Each start of an expired build opens in expired mode.

- Tabs: only the Export tab shows. The app hides all other tabs, also the
  Projects tab. The app uses the same mechanism that hides the tabs when no
  project is open.
- Projects: the app does not open projects. During a session, the app saves
  the open project before it hides the tabs. The video player stops.
- Export tab: the export queue and the completed exports show and work as
  usual. The user can cancel an export and open the output folder.
- New exports: the user cannot start a new export. The start button has a
  different color. A click on it shows the dialog again. The app does not
  add the export to the queue.
- Export queue: the running export and the queued exports continue. Expired
  mode does not stop them and does not add to them.
- Banner: a panel at the top of the window says that this version has
  expired. It shows the date that the app used, the state of the online
  check, a "Check now" button, and a "Download update" button. The user
  cannot close the banner.
- Dialog: the app shows the dialog when it goes to expired mode and at each
  start in expired mode. The dialog is modal, and the user can close it.
  - It shows the rule `message` if there is one. If there is no message, it
    shows a default text, for example: "This version of <app name> has
    expired. Download the new version to continue. Your projects stay on
    your computer. Exports that you started before continue."
  - It shows the date that the app used and the state of the online check.
  - Buttons: "Check now", "Download update" (opens the download URL; the app
    does not quit), and "Close".
- The state of the online check, in the banner and in the dialog:
  - While a check runs: "Checking…".
  - No connection: "<app name> cannot connect to the update server. It
    checks again every minute. If the date is wrong, correct the clock and
    connect to the internet." The text does not say "no internet": a
    firewall or a blocked region can block only the server.
  - Still expired: no extra text. The server time confirms the date.
- The download URL is `latest.downloadUrl` from the saved file. If there is no
  saved file, the URL is the GitHub releases page.
- Leaving expired mode: if an online check shows that the build has not
  expired, the app closes the dialog, removes the banner, clears the flag
  (see "Start of a build that looks expired"), and shows the tabs again. The
  user can open a project and start exports as usual.

Dependency: B-18 in `backlog.md` (keep the export queue after the app
closes). Until B-18 is done, the queue is lost when the app closes. Then
expired mode at start shows only the completed exports.

Threat model: in expired mode, the user cannot do new work. The queued
exports contain only work from before the expiry. To add a job to a saved
queue, a person must edit a file in the data folder, which is after the line.

### Wrong clock notice

- After each server time, the app compares the system time with the server
  time. If they differ by more than 24 hours, the app shows a notice that is
  not modal: "The clock of this computer is wrong by N days. Correct the
  clock. If you do not, <app name> can stop while the computer is offline."
- The notice is necessary because a server time corrects the time only for
  the current session. With a clock that is too far forward, the next start
  with no network expires again. With a clock that is behind, the next start
  with no network starts "Clock behind" again.
- The app shows the notice one time in each session. The user can close it.
- Harm: none. The notice shows only when the clock is wrong by more than one
  day.

## Privacy

- The request for the rules file sends no user ID and no analytics data. The
  server sees only the IP address and the standard HTTP headers.
- The server is GitHub. The privacy text must name GitHub as the receiver of
  the request.
- The check is necessary for the license, so the user cannot turn it off. It
  is not part of the analytics consent.
- The privacy text in the app and the README must tell the user about this
  request, separately from the analytics text.
- Add this item to the lawyer review (L-6.2).

## Release steps

A test in this repository reads `release/version-policy.json`. It fails if
the file is not valid, if the file has no `latest`, if a rule has no
`toVersion`, if a rule matches `latest.version`, or if two rules have the same
`id`. Thus, a mistake in the file cannot stop all users. `latest` is optional
for the app, but required in the repository.

Add these steps to `release-checklist.md`:

- Before a release: make sure that no rule stops the new version by mistake.
- After a release: set the default version in `pom.xml` to the next version
  with `-SNAPSHOT`.
- After the release is published: set `latest` in
  `release/version-policy.json` to the new version, and push the change to
  `master`.
- Emergency rebuild: if no normal release is ready and the newest build
  expires in less than 60 days, push the next patch tag (for example
  `v1.4.1`) on the commit of the newest release. The release workflow builds
  it with a new build date, so the new build works for 6 more months. Then
  update `latest` in `release/version-policy.json`.
  - Why 60 days, not 30: the expiry warning starts 30 days before the
    expiry. With 60 days, the update notice gets to the users before the
    warning. Also, a new user who downloads the newest release gets a build
    with at least some months of use.
  - If the release workflow of the old commit fails (for example, an action
    that GitHub no longer supports), fix the workflow on a branch from the
    release commit. Then push the tag on the commit with the fix.
- Do not delete a natives release (for example `natives-2026-09`) while a
  release tag uses it. The release workflow downloads the natives release
  that `native-dependencies.json` of the tagged commit names. An emergency
  rebuild of an old commit fails if this natives release is deleted.
- Recommendation (not a check): after a release N, wait about 30 days before
  you add a rule that stops N-1. If N has a serious bug on some computers,
  users can install N-1 again until a fix is available. Stop N-1 earlier when
  necessary, for example when N-1 can damage project files. No test or script
  checks this step.

### Release age reminder

The recovery from "no release for 6 months" must not depend on the memory of
the author (rule 5 in `threat-model.md`). Thus, a workflow gives a reminder.

- A scheduled GitHub workflow runs one time each week. It finds the newest
  published `v*.*.*` release and its publish date.
- If the publish date is more than 4 months before the day of the run, the
  workflow opens an issue: "The newest release expires in less than 60 days.
  Make a release or an emergency rebuild." 4 months is the build life (6
  months) minus the 60 days of "Emergency rebuild".
- If an open issue with the same title exists, the workflow does not open a
  second one.
- The publish date is approximately the build date: the release workflow
  builds and publishes in one run. `Validate-Release.ps1` makes sure that the
  build date is at most 7 days earlier.
- With no published release, the workflow does nothing.
- Limit: in a public repository, GitHub turns off scheduled workflows after
  60 days with no activity in the repository. GitHub sends an email to the
  owner before it does this. Thus, if the author makes no commit for 60 days,
  the reminder can stop. The email from GitHub is then the reminder. Add a
  line about this to `release-checklist.md`.

## Known limits

These limits are accepted. A signed license file that the app checks
offline (L-6.3) does not close the limits that depend on the clock. Only an
online check can close them, for example an activation or a check each N
days. An online check has a cost for offline users. Decide this in L-6.3. The
limits for builds from source and for changed code only legal action (ELv2)
can close. Nothing can close the limit for the GPLv3 code.

The limits:

- A new device that stays offline can use an old installer if its clock is
  set to a date between the build date and the build expiry. The build-date
  floor limits this to one build life (6 months or less) for each device with
  no saved time.
- A new Windows user account on the same device has no saved time, because
  the saved time is in `HKCU`. A user who creates a new account, sets the
  clock to just after the build date of an old installer, and keeps the
  device offline gets one build life for each new account. The number of
  accounts has no limit. Each step is a normal user action. A copy of the
  saved time in `ProgramData` for the whole device was considered on
  2026-10-02 and rejected: the effort is too large for a method with this
  much friction (the device stays offline with a wrong clock for months, and
  the projects move to each new account). See C-05 in `expiry-scenarios.md`.
- A user who moves the clock back and blocks the network gets 12 hours for
  each start, plus the run time. The spec does not add a maximum offline
  period, because it would stop honest offline users. Decide this again with
  license keys (L-6.3).
- A person who builds from source gets a new build expiry each time. The
  version rules still stop such a build when it is online. To avoid the rules,
  the person must change the version or the checks. ELv2 forbids this, because
  the checks are license key functionality.
- A person who builds from source can remove the checks. ELv2 forbids this.
- A person can edit the build date or the version in the class files with a
  bytecode editor. ELv2 forbids this, because it changes the checks.
- A person can add a certificate to the Windows store or to the bundled
  `cacerts` file and use a proxy that sends a false server time. Then the
  build works with no limit. Each step is after the line, so the app does not
  protect against it.
- The code up to the Git tag `last-gpl` stays GPLv3. Anybody can build it and
  use it with no limits.
- Nobody can extend a build after its release. The rules file can only make
  the expiry earlier. If no new release comes for 6 months, all users stop.
  The recovery is an emergency rebuild (see "Release steps"). It needs a
  working release workflow and the natives release of the old commit. The
  release age reminder tells the author 60 days before the expiry. An extension in the rules file with no signature
  was considered on 2026-10-02 and rejected: the rebuild covers the case when
  the author is available, and no measure helps when the author is not.
  A signed extension in the rules file was considered and rejected
  (2026-09-30): it adds a key pair, a signing step, and a check in the app,
  and the value is too small for now. It works only for builds that contain
  the check, so a later addition cannot help builds that are already
  released.
- A user can delete the saved rules file. Then only the build expiry applies
  until the next read of the rules file.
- A user can delete the saved time in the registry. Then the app has no saved
  time, and it cannot see that the clock is behind, unless the clock is
  earlier than the build moment. The build-date floor limits this to one
  build life each time. This is after
  the line. An uninstall does not delete the saved time: the preferences stay
  in `HKCU\Software\JavaSoft\Prefs`. Keep it so when the installer changes.

## Accepted risks

These risks for honest users were considered on 2026-10-01 and accepted with
no change to the design.

- **A rule can stop a version with no warning.** The build expiry has a
  warning from 30 days before. A rule has a warning only if its `stopsOn` is
  30 days or more in the future. A rule with a `stopsOn` in the past stops the
  version at the next expiry check, also during work. The author decides the
  `stopsOn` date for each rule. An immediate stop is sometimes necessary, for
  example for a version that damages project files.
- **Some users cannot read the rules file.** Some regions and some firewalls
  block `raw.githubusercontent.com`. For these users
  the app is always offline: they get no rules, no update notice, and no
  correction of the clock from the server time. The build expiry still
  applies.
- **A clock that is too far forward locks an offline user.** One start with
  such a clock moves the saved time forward, and the saved time does not move
  back without a server time. The app stays locked until a server response,
  also after the user corrects the clock. The banner and the dialog of
  expired mode show the date that the app used, so the user can see the
  cause.
  - Do not add a stored clock offset to prevent this. A stored offset is a
    value in the preferences that a user can edit to move the time back.
- **A person with push access to `master` controls the rules file.** A push
  to `master` changes the file for all users in some minutes. The file has no
  signature. Such a person can do these things:
  - Set `latest.downloadUrl` to a page with a false installer.
  - Stop all versions: set `latest.version` to a high version, and add a rule
    with a `toVersion` just below it.
  - Push a mistake. The file is live before CI runs the test of the file.

  This adds almost no new risk. A person who can push to `master` can also
  push a `v*.*.*` tag. Then the release workflow builds their code and
  publishes it on the real releases page. The protection is the same for both
  cases: the security of the GitHub account.

## Out of scope

- Automatic download and installation of updates.
- License keys and the checks of their signatures.
- The entitlement layer (a check that enables or disables each feature).
- A read-only mode for expired builds.
- A signed extension of the build expiry (see "Known limits").
- What a user who does not pay can do with existing projects. Decide this with
  the pricing model (L-6.3). A project is a folder of JSON files, so the app
  cannot tell an old project from a new one.

## Tests

- The expiry calculation: build expiry only; a rule that is earlier; a rule
  that is later (no effect); a rule for a different version; a version that is
  not `MAJOR.MINOR.PATCH`.
- The expiry moment: the version works at 23:59:59 UTC on the day before the
  expiry date and stops at 00:00 UTC on it; the warning starts exactly 30 × 24
  hours before; the text shows the moment in the local time zone with the
  hour; 31 August plus 6 months is 28 February, and 29 February in a leap
  year.
- The versions: `1.3-SNAPSHOT` is `1.3.0`; a version with no number is
  `0.0.0`; a rule that matches `latest.version` is ignored; a rule with no
  `toVersion` is ignored; a rule with no `fromVersion` stops all versions up
  to its `toVersion`.
- The clock: the system clock moved back (the time still moves forward with
  the run time); the server time replaces a saved time that is too far forward;
  after a server time, a system time that moves back does not change the
  saved time; the saved time is written at each check, every 5 minutes of run
  time, and when the app closes; a session that ends with no close (End task)
  loses at most 5 minutes of run time; before a server time, a system time
  that moves 10 days forward and then back keeps the saved time 10 days
  ahead, and the run time continues from there.
- The run time: a jump of the counter (a sleep) moves the saved time forward,
  also offline with the clock behind; a change of the system clock does not
  change the run time; after a server time, a system time that moves
  forward by months does not move the saved time; if the counter is not
  available, the app uses `System.nanoTime` and writes one log line.
- The minute tick: the tick does the expiry check at once when the current
  time passes the effective expiry or the start of the warning; the first
  tick after a long sleep does the check; with no such event, the check runs
  when 1 hour of run time has passed since the last check.
- Manual check before the first release: on a laptop, start the app, put the
  laptop to sleep for 10 minutes, and wake it. The log must show a run time
  that includes the 10 minutes.
- The build-date floor: a saved time earlier than the build moment becomes
  the build moment at start; with no saved time and a system time 1 year
  before the build date, the "Clock behind" rule adds 12 hours and shows the
  notice.
- The server time: a server time that is earlier than the saved time moves
  the saved time back to the server time; a server time that is later than
  the saved time moves it forward.
- The clock behind: a system time more than 24 hours before the saved time
  adds 12 hours to the saved time at each start and shows the notice; a system
  time 23 hours before the saved time has no effect; a server response after
  that sets the saved time to the server time.
- The file time probe: a file time later than the Java clock becomes the
  system time; a Java clock later than the file time stays the system time; a
  probe that cannot write or read the file uses the Java clock and shows no
  error.
- The instance lock: a second start while the app runs does not get the lock,
  does not write the saved time, and does not add the 12 hours of "Clock
  behind", also when the clock is behind; the lock is taken before the saved
  time is read.
- More than one instance: a write with no new server time keeps the later of
  the stored value and the new value; the first write after a new server time
  replaces a stored value that is later; after that, a start with no network
  uses the corrected value.
- The HTTPS trust: the request accepts a certificate that only the bundled
  store trusts, and a certificate that only the Windows store trusts; it
  rejects a certificate that neither store trusts.
- The build information: `BuildInfo` has the build date and the version from
  the build; the expiry date is 6 calendar months after the build date;
  `AppInfo.version` and the analytics version are `BuildInfo.VERSION` also
  when the system property `bananashot.version` has a different value;
  `AppInfo.displayName` contains the version; `BUILD_DATE` is a valid
  `yyyy-MM-dd` date; `BuildInfoPrinter` prints the values that
  `Validate-Release.ps1` checks; `Validate-Release.ps1` fails when the
  bundled runtime does not contain `jdk.crypto.mscapi`, `jdk.crypto.ec`, or
  `java.net.http`.
- A system clock that is too far forward: with a network, the app opens in
  normal mode after the "Checking the date…" window and shows the wrong
  clock notice; with no network, the app opens in expired mode, and it leaves
  expired mode within 1 minute after the network is available.
- The rules file: not valid, unknown schema, no connection (the saved file
  applies), a timeout after 10 seconds, a new valid file replaces the saved
  file; a file with unknown fields is valid, and the app reads the known
  fields.
- The server response: a 404, a redirect, a file that is not valid, and a file
  with an unknown schema all give a server time; the app does not follow a
  redirect; a failed connection, a timeout, and a TLS error give no server
  time.
- The release age reminder (run it by hand with `workflow_dispatch`): a
  newest release older than 4 months opens one issue; a second run does not
  open a second issue; a newest release younger than 4 months opens no issue.
- `release/version-policy.json` in the repository is valid, has `latest`,
  each rule has a `toVersion`, no rule matches `latest.version`, and each
  `id` is unique.
- The user interface: the warning starts 30 days before the expiry, and it
  shows one time on each local calendar day in a long session (a session
  from 23:00 to 02:00 local time shows it at start and again before 01:00);
  a closed warning stays hidden for the rest of the calendar day and shows
  again on the next one; a new rule that makes the expiry earlier shows the
  warning at once, also after the user closed it; a rule that the app already
  saw in this session does not show it again.
- The log: a rule that gives the effective expiry writes its `id` and the
  date to the log.
- Expired mode: at start, an expired build shows only the Export tab, the
  banner, and the dialog, and it does not open a project; the dialog closes
  with "Close", and "Download update" opens the URL and does not quit; the
  start button shows the dialog and does not add an export; the function that
  opens a project and the function that adds an export both refuse in
  expired mode, also when the user interface does not call them.
- Expiry during a session: the check saves the open project, stops the
  player, hides all tabs except Export, and shows the banner and the dialog;
  the running export and the queued exports continue to the end; the
  keyboard shortcuts of the hidden tabs have no effect; with a server time in
  the session, the app goes to expired mode with no online check; with no
  server time in the session, the app makes an online check first and stays
  in normal mode if the check gives "Not expired".
- Leaving expired mode: an online check that gives "Not expired" closes the
  dialog, removes the banner, clears the flag, and shows the tabs again; the
  user can then open a project and start an export.
- Start of a build that looks expired: path A (no flag) shows "Checking the
  date…" before the main window, tries a maximum of 3 times 2 seconds apart,
  and stops after 15 seconds in total; "Not expired" opens the normal main
  window; "Still expired" and "No connection" open expired mode with that
  result in the dialog; path B (flag set) opens expired mode at once with the
  dialog in "Checking…", and "Not expired" then leaves expired mode; going to
  expired mode sets the flag, and leaving it clears the flag; a start in
  normal mode clears the flag; a deleted flag gives path A and the same final
  result.
- The online check schedule: "No connection" in expired mode or in "Clock
  behind" gives the next check after 1 minute; "Still expired" gives 15
  minutes; "No connection" in normal mode gives 15 minutes; in normal mode, a
  404, a server error, a redirect, or a file that is not valid gives 15
  minutes; a valid file or a file with an unknown schema gives 24 hours;
  "Check now" runs a check at once
  and starts the schedule again; a second click while a check runs has no
  effect.
- The state text: the banner and the dialog show "Checking…" while a check
  runs, the "cannot connect to the update server" text after "No
  connection", and no
  extra text after "Still expired".
- The "Clock behind" notice: "Check now" runs a check; the notice closes when
  a server time arrives; in expired mode the notice does not show, and the
  app still adds the 12 hours.
- The wrong clock notice: a system time 25 hours from the server time shows
  the notice with the number of days; 23 hours does not show it; the notice
  shows one time in each session.
