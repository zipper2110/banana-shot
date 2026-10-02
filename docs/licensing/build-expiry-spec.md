# Build Expiry and Version Rules: Design Spec

Status: draft. The decisions in the table were approved on 2026-09-30. The
author must still review the details. On 2026-10-01, a review with the threat
model added "Run time", "Build-date floor", and "Server time far forward".
On 2026-10-02, a second review removed the measures that stop only actions
after the line: the trusted time, "Server time far forward", the trust in the
bundled certificate store only, and the time in project files.
This spec is item L-5.1 of
`elv2-migration-plan.md`. Assess its measures with `threat-model.md`.

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

## Build expiry

- The build generates a Kotlin source file with constants, for example
  `BuildInfo.kt`:
  - `const val BUILD_EPOCH_DAY: Long`: the build date (UTC) as the number of
    days from 1970-01-01. A number is more difficult to find in a class file
    than a date text.
  - `const val VERSION: String`: the project version (`${revision}`).
  - Use a Maven plugin that fills a source template, for example
    `templating-maven-plugin`. The Kotlin compiler must compile the generated
    folder.
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

## Version rules file

The app reads one static JSON file over HTTPS. The file is in this repository
at `release/version-policy.json`. The app reads the raw GitHub URL of the file
on the `master` branch.

The URL must work only for 6 months after the last build that uses it,
because each build expires after that time. Thus, a later move of the file is
safe.

- The app waits a maximum of 10 seconds for the file. If the read fails, the
  app shows no error. It tries again at the next read.
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
  - `fromVersion`, `toVersion`: the range (both ends included). Each field is
    optional, but a rule must have at least one of them.
  - `stopsOn`: the date when the versions stop (see "Expiry moment"). A date
    in the past stops the versions at the next expiry check.
  - `message`: the reason. The app shows it in the warning and in the dialog
    of expired mode. This field is optional. English only until localization (B-11).

### Rules for the app

- The app version is `BuildInfo.VERSION` (see "Build expiry"). The app does
  not read the version from a system property, from the `.cfg` file, or from
  the JAR manifest. Now `AppInfo.version` and the analytics read the system
  property `tennis.record.version`, and `Build-AppImage.ps1` writes it into the
  `.cfg` file. Change both to `BuildInfo.VERSION`, and remove the property
  from `Build-AppImage.ps1`. If not, a user can set the version to `99.0.0` in
  the `.cfg` file. Then no rule with a `toVersion` stops the build, and the
  update notice does not show.
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
- The build moment is 00:00 UTC on the build date (`BUILD_EPOCH_DAY`). The
  real time is never earlier than the build moment. See "Build-date floor".
- At start, the saved time becomes the later of: the saved time and the
  build moment. See "Build-date floor".
- At start, if the system time is more than 24 hours earlier than the saved
  time, the clock is behind. See "Clock behind".
- More than one instance of the app can run. The first write of the saved
  time after a new server time replaces the stored value. Thus, the server
  correction of a time that is too far forward stays after the app closes.
  All other writes keep the later of the stored value and the value of the
  instance. Thus, an instance with no new server time cannot move the stored
  time back.
- Accepted: if an instance with a time that is too far forward runs together
  with an instance that got a server time, the first instance can write the
  false time again. It corrects itself at its next server time (in 24 hours
  or less). This needs two instances and a wrong clock, so it is rare.
- A server response is any HTTPS response from the host of the rules file,
  with any HTTP status (for example 404). The file in the response can be not
  valid or have an unknown schema. The `Date` header is correct in all these
  cases, and the trust in the host is the same. Thus, the correction of the
  clock continues if the file moves, breaks, or changes its schema. A
  connection that fails (no network, timeout, TLS error) is not a server
  response.
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
- The limit of 24 hours prevents false results from small clock errors, for
  example a computer that uses local time in its hardware clock.
- A server time sets the saved time to the server time. Thus, the 12 hours
  have no effect for an online user.
- The app shows a notice that is not modal: "The date on this computer is
  earlier than the date that <app name> used before. Check the clock of the
  computer and connect to the internet. Until then, each start of <app name>
  uses 12 hours of the time that is left for this version." The user can close
  the notice for the current session.
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
- In expired mode, the app still reads the rules file. If the server time
  shows that the build has not expired, the app leaves expired mode (see
  "Expired mode"). Thus, a wrong clock does not block the app when it is
  online.

## When the app does the checks

### Expiry check (local, no network)

- At start: the app calculates the effective expiry from the build expiry and
  the saved rules file. It does this before it shows the main window and before
  it opens a project.
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
  keeps the points, the overlays, and the scoreboard from the moment when the
  user added it to the queue. The job can read the color and crop settings
  from the project when it starts, but in expired mode the user cannot change
  them.
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
  goes to expired mode at once (see "Expired mode"). The running export and
  the queued exports continue.

### Rules file (network)

- The app reads the rules file in the background after start. It does not
  make the start slower.
- It reads the file again every 24 hours while it runs. Thus, an app that is
  never closed also gets new rules.
- After each read, the app does the expiry check again.

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
  not modal. It shows the warning at start, and after that one time each day
  while it runs (from the hourly check). The warning shows the moment of the
  expiry in local time (see "Expiry moment"), the rule `message` if there is
  one, and a "Download update" button.
- The user can close the warning. It then stays hidden until the next daily
  warning.
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
  expired. It shows the date that the app used and a "Download update"
  button. The user cannot close the banner.
- Dialog: the app shows the dialog when it goes to expired mode and at each
  start in expired mode. The dialog is modal, and the user can close it.
  - It shows the rule `message` if there is one. If there is no message, it
    shows a default text, for example: "This version of <app name> has
    expired. Download the new version to continue. Your projects stay on
    your computer. Exports that you started before continue."
  - Buttons: "Download update" (opens the download URL; the app does not
    quit) and "Close".
- The download URL is `latest.downloadUrl` from the saved file. If there is no
  saved file, the URL is the GitHub releases page.
- Leaving expired mode: if a server time or a new rules file shows that the
  build has not expired, the app removes the banner and shows the tabs again.
  The user can open a project and start exports as usual.

Dependency: B-18 in `backlog.md` (keep the export queue after the app
closes). Until B-18 is done, the queue is lost when the app closes. Then
expired mode at start shows only the completed exports.

Threat model: in expired mode, the user cannot do new work. The queued
exports contain only work from before the expiry. To add a job to a saved
queue, a person must edit a file in the data folder, which is after the line.

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
the file is not valid, if a rule matches `latest.version`, or if two rules
have the same `id`. Thus, a mistake in the file cannot stop all users.

Add these steps to `release-checklist.md`:

- Before a release: make sure that no rule stops the new version by mistake.
- After a release: set the default version in `pom.xml` to the next version
  with `-SNAPSHOT`.
- After the release is published: set `latest` in
  `release/version-policy.json` to the new version, and push the change to
  `master`.
- Recommendation (not a check): after a release N, wait about 30 days before
  you add a rule that stops N-1. If N has a serious bug on some computers,
  users can install N-1 again until a fix is available. Stop N-1 earlier when
  necessary, for example when N-1 can damage project files. No test or script
  checks this step.

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
    with no `toVersion`.
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
  `0.0.0`; a rule that matches `latest.version` is ignored.
- The clock: the system clock moved back (the time still moves forward with
  the run time); the server time replaces a saved time that is too far forward;
  after a server time, a system time that moves back does not change the
  saved time; the saved time is written at each check, every 5 minutes of run
  time, and when the app closes; a session that ends with no close (End task)
  loses at most 5 minutes of run time.
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
- More than one instance: a write with no new server time keeps the later of
  the stored value and the new value; the first write after a new server time
  replaces a stored value that is later; after that, a start with no network
  uses the corrected value.
- The HTTPS trust: the request accepts a certificate that only the bundled
  store trusts, and a certificate that only the Windows store trusts; it
  rejects a certificate that neither store trusts.
- The build information: `BuildInfo` has the build date and the version from
  the build; the expiry date is 6 calendar months after the build date;
  `AppInfo.version` is `BuildInfo.VERSION` also when the system property
  `tennis.record.version` has a different value; `BuildInfoPrinter` prints
  the values that `Validate-Release.ps1` checks.
- A system clock that is too far forward: the app starts in expired mode,
  then leaves expired mode after a server response.
- The rules file: not valid, unknown schema, no connection (the saved file
  applies), a timeout after 10 seconds, a new valid file replaces the saved
  file; a file with unknown fields is valid, and the app reads the known
  fields.
- The server response: a 404, a file that is not valid, and a file with an
  unknown schema all give a server time; a failed connection, a timeout, and
  a TLS error give no server time.
- `release/version-policy.json` in the repository is valid, no rule matches
  `latest.version`, and each `id` is unique.
- The user interface: the warning starts 30 days before the expiry, and it
  shows one time each day in a long session; a closed warning shows again at
  the next daily warning; a new rule that makes the expiry earlier shows the
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
  keyboard shortcuts of the hidden tabs have no effect.
- Leaving expired mode: a server time that shows that the build has not
  expired removes the banner and shows the tabs again; the user can then
  open a project and start an export.
