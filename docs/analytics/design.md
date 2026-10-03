# Usage analytics design

Status: approved, 2026-10-01. Revised the same day after a review. This
design replaces the 2026-08-29 spec and the 2026-09-03 plan. Commit `29710b6` deleted them. Get them from `29710b6^` if
necessary.

## Decisions

- The app sends a **session summary**, not a stream of events. The summary is
  a set of counters for the current app session.
- The app sends the summary to our Cloudflare Worker. The Worker keeps one
  row for each session in D1.
- The analytics answer three questions:
  1. Which tabs and features do users use?
  2. How many exports start, complete, fail, or get cancelled? With which
     encoder? How fast are they?
  3. How long are sessions? Which app versions and OS families are in use?
- Analytics ship in the first release (B-9).

## What stays from the first attempt

| Part | Status |
|---|---|
| `Analytics.record(AnalyticsEvent)` as the only API for features | Keep. |
| `AnalyticsPreferences`: consent choice and notice version | Keep. Add the session count and the open-session flag. |
| `AnalyticsController`: on/off switch, `DisabledAnalytics` by default | Keep. `close()` at app shutdown keeps the consent choice (fixed 2026-10-01). Only `disable()` saves `DISABLED`. |
| `AnalyticsBuildConfig`: endpoint, privacy URL, notice version from system properties | Keep. Change the endpoint path. |
| `AnalyticsConsentDialog`, `PrivacyPage` | Keep. Change the dialog text and the "Collected" text. |
| `JdkAnalyticsTransport` | Keep. Add the 410 stop, a send with a time limit at exit, and the system proxy. |
| `analytics-worker`: kill switch, 90-day deletion, D1 | Keep. Replace the event table and the validation. |
| `AnalyticsBuffer`, `AnalyticsEnvelope`, sequence numbers, event batches | Remove. |
| `analytics-contract/v1` fixtures | Replace with summary fixtures. |

No build ever sent events, and the Worker and D1 are not deployed. Thus we
do not need compatibility with the old contract. Replace migration `0001`
and the v1 fixtures in place.

## Session

- A session starts when analytics becomes enabled in a process: at start, or
  when the user turns on analytics later.
- The session ID is a random UUID v4. It stays only in memory.
- A session ends at exit, or when the user turns off analytics. App shutdown
  never changes the consent choice.
- `duration_s` is process time while analytics is enabled. Use a monotonic
  clock. The app clamps it to 7 days.
- `active_s` is the part of `duration_s` while the main window is the active
  window. An app that is minimized or in the background does not add active
  time. `active_s` answers "How long are sessions?". `duration_s` only shows
  how long the app stays open.

## When the app sends

| Moment | What the app sends |
|---|---|
| Session start | The first summary (all counters 0). Thus the server counts a session even if the app crashes early. |
| Every 5 minutes | The summary, only if a counter or `active_s` changed. A change of `duration_s` alone does not cause a send. Thus an idle app does not send. |
| Normal exit | The last summary with `final = true`. See "Exit sequence". |
| User turns off analytics | Nothing. The app deletes the counters. |

- The app does not retry. The next summary replaces a lost one.
- A crash loses a maximum of 5 minutes of counts.
- A missing final summary is **not** a crash signal. The final send can fail
  at exit (a new TLS connection, a slow network). A Windows logoff does not
  close the window. "Turn off analytics" sends nothing. Use the
  `unclean_exit` counter to count crashes.
- A 410 response stops sending for the rest of the process.
- All analytics threads are daemon threads. The UI thread never waits for
  the network, except for the 500 ms limit at exit.

### Exit sequence

The JDK HTTP client closes idle connections after about 30 seconds. Thus the
final send usually needs a new connection. The sequence below gives the send
the most time and hides the wait from the user.

1. `windowClosing` counts running exports as `export_interrupted_<enc>`. Then
   it starts the final send. The 500 ms limit starts here.
2. `windowClosing` hides the main window.
3. `handle.close()` closes the services. `EnabledAnalytics.close()` waits for
   the final send until the 500 ms limit, then stops the transport.
4. The process exits.

## Wire format

`POST /v1/session`, `Content-Type: application/json`.

```json
{
  "schema_version": 1,
  "notice_version": 1,
  "session_id": "7df3a8ca-4d5d-44d1-913d-ae553df3916f",
  "app_version": "1.0.0",
  "os_family": "windows",
  "snapshot": 3,
  "final": false,
  "duration_s": 912,
  "active_s": 640,
  "counters": { "tab_points": 2, "tab_s_points": 540, "point_added": 31 }
}
```

- `snapshot` starts at 0 and increases by 1 with each send. The server keeps
  the summary with the highest `snapshot`.
- `counters` contains only keys from the counter list below. Each value is an
  integer in `0..1000000`. The app does not send a counter with the value 0.
- There are no other fields. The only strings are `app_version` and
  `os_family` (`windows`, `macos`, `linux`, `other`).
- `app_version` is `BuildInfo.VERSION`. The app and the Worker accept all
  values of `app_version` (decided on 2026-10-03). Thus a build with an
  unusual version does not lose its data. The request size limit is the only
  length limit.
  - The Worker stores a string as it is.
  - The Worker stores a number, a boolean, an object, or an array as its JSON
    text, for example `7` or `{"major":1}`.
  - A summary can omit `app_version`. The Worker stores a missing value or
    `null` as `unknown`.
- The app does not send a clock time, a time zone, a locale, a path, a file
  name, a project name, a score, a player name, or text from the user.

## Counters

The key list is closed. Both the app and the Worker check it. To add a key,
change both lists and the contract fixtures. Also update the privacy notice.
Increase the notice version if the new key collects a new type of data.
Deploy the Worker with the new key before the app release. The Worker
rejects a summary with an unknown key. Thus an app release that comes first
loses all of its sessions.

### Session

| Key | Meaning |
|---|---|
| `unclean_exit` | 1 if the previous analytics session on this install did not close normally. |
| `uncaught_error` | Uncaught exceptions in this session. The app sends only the count, not the exception. |
| `session_n_<bucket>` | Always 1. `<bucket>` is the number of this analytics session on this install: `1`, `2_5`, `6_20`, `21p`. |

- `unclean_exit`: at session start, the app sets a flag in Preferences. A JVM
  shutdown hook and `disable()` clear it. A shutdown hook runs at a normal
  exit and at a Windows logoff. It does not run at a JVM crash or a kill. At
  the next session start, a set flag gives `unclean_exit = 1`.
- `session_n_<bucket>`: the app keeps a session count in Preferences. It
  increases the count at each session start. Without an install ID, session
  rows cannot tell one user with 10 sessions from 10 users with 1 session.
  The bucket gives this information at a small privacy cost.

### Tabs

| Key | Meaning |
|---|---|
| `tab_<tab>` | The user opened the tab with a sidebar click. |
| `tab_s_<tab>` | Active seconds (see `active_s`) while the tab was the current tab. |

`<tab>` is one of: `projects`, `points`, `colors`, `crop_rotate`, `scoring`,
`stats`, `export`. The Test tab is not counted.

`tab_<tab>` does not count tab changes that the app makes: the Projects tab at
start, and the move to Points after a project opens. `tab_s_<tab>` counts the
current tab, independent of how it became current. The app adds the time of
the current tab at each tab change and before each send.

### Features

| Key | Meaning |
|---|---|
| `project_created` | A new project was created. |
| `project_opened` | An existing project was opened. |
| `video_open_failed` | The video of a project could not be opened. |
| `point_added` | A point was completed (start and end marked). |
| `point_deleted` | A point was deleted. |
| `point_favorited` | A point was marked as a favorite. |
| `comment_added` | A rally comment was added. |
| `score_recorded` | A point outcome was recorded in Scoring. |
| `color_changed` | The user changed a color adjustment. |
| `crop_rotate_changed` | The user changed crop or rotation. |
| `help_opened` | The Help window was opened. |

Count only actions that the user started. Do not count project loads,
programmatic UI updates, or changes that have no effect.

### Export

`<enc>` is one of: `software`, `nvenc`, `amf`, `qsv`.

| Key | Meaning |
|---|---|
| `export_started_<enc>` | An export job started to run. |
| `export_completed_<enc>` | The export completed. |
| `export_failed_<enc>` | The export failed. |
| `export_cancelled_<enc>` | The user cancelled a running export. |
| `export_interrupted_<enc>` | The export was running when the app closed. |
| `export_run_s_<enc>` | Sum of run times of the completed exports, in seconds. |
| `export_video_s_<enc>` | Sum of output video lengths of the completed exports, in seconds. |
| `export_fail_<reason>` | Failures by reason: `source_missing`, `output_write`, `process_start`, `ffmpeg_exit`, `other`. |
| `export_opt_<option>` | Started exports with the option: `scoreboard`, `comments`, `stats_card`, `favorites_only`, `idle_trim`. |
| `export_res_<p>` | Started exports by the short side of the output frame: `720`, `1080`, `1440`, `2160`, `other`. |

- `export_video_s / export_run_s` gives the encode speed of each encoder.
- Use seconds, not milliseconds. 1,000,000 ms is only about 17 minutes of
  export time. A millisecond sum can get to the value limit in one session.
- Use the short side of the frame. After a 90° rotation, a 1080p export has
  a height of 1920.
- A job cancelled while it waits in the queue is not counted. It did not
  start.
- All exports are MP4, so there is no container counter.

## Code structure

### Features and the architecture rule

`docs/architecture-rules.md` says that only `app` and `ui.privacy` can use
`analytics`. The features must record actions. Change the rule:

- Feature packages can import only `Analytics` and `AnalyticsEvent` (with
  its enums). A guard test checks this.
- Features get `Analytics` from `app` through the constructor. The default
  is `DisabledAnalytics`, so tests do not change.

### Events

`AnalyticsEvent` stays a sealed class. Change the subtypes to match the
counters, for example `TabOpened(tab)`, `PointAdded`,
`ExportFinished(encoder, outcome, runMs, videoS)`. Features never see
counter keys. `EnabledAnalytics` converts each event to counter changes.

### Where to record

| Action | Place |
|---|---|
| Tab opened | `goTo` in `SwingApplicationFactory` (the `app` package). `goTo` gets a `byUser` flag. Only user clicks on the sidebar buttons set it. The startup `doClick` does not set it. |
| Tab time, `active_s` | `SwingApplicationFactory`: `goTo` and a `WindowFocusListener` on the main window |
| Uncaught errors | The default handler in `SwingMainApp.kt` |
| Running exports at exit | `windowClosing` in `SwingApplicationFactory`, with data from `RenderQueueManager` |
| Help opened | `showHelp` in `SwingApplicationFactory` |
| Project created or opened, video open failed | `DefaultProjectsPresenter` |
| Point added, deleted, favorited | `PointsDispatcher` or the Points tab |
| Comment added | `CommentDispatcher.create` or the Points tab |
| Score recorded | Scoring tab |
| Color, crop or rotation changed | The Colors and Crop/Rotate tabs, user input only |
| Export started and result | `RenderQueueManager`: `RUNNING` and `finishRequest`. Use a small listener with only the facts above, not `RenderJob`. |

### Desktop classes

- `SessionCounters`: thread-safe counters. It creates the JSON body.
- `EnabledAnalytics`: owns the session, the counters, the active-time clock,
  and the 5-minute timer. `beginFinalSend()` starts the last summary.
  `close()` waits for that send until the 500 ms limit. Without
  `beginFinalSend()`, `close()` sends nothing.
- `AnalyticsController.disable()` stops delivery and saves `DISABLED`.
  `AnalyticsController.close()` stops delivery and does not change the
  choice. `AppServices.close()` calls `close()` at each exit. Thus `close()`
  must never call `disable()`.
- `windowClosing` calls `beginFinalSend()` in place of the `SessionEnded`
  event. See "Exit sequence".
- `AnalyticsPreferences` stores the consent choice, the session count, and
  the open-session flag. It stores no counters and no session ID.
- `JdkAnalyticsTransport` sends one summary and returns the status.
  `EnabledAnalytics` waits for the final send until the 500 ms limit and
  stops after a 410 response (changed on 2026-10-03, B-9 decision 9).
- The HTTP client uses the default proxy selector, and `main` sets
  `java.net.useSystemProxies=true`. Without this, users behind a proxy send
  nothing and see no error.
- Local logs contain only the status class and the count of sends. They do
  not contain request bodies or the session ID.

## Worker and database

- `POST /v1/session` only. Other routes return 404. The checks are in this
  order: kill switch (410), method, content type (415), size (413, limit
  8 KiB), JSON and schema (400). The full order is B-9 decision 6.
- A summary with an old `schema_version` gets 410. Thus an old app stops
  sending after we remove support for its schema.
- Success returns 204 with no body.
- A D1 count row for each hour limits the requests from one IP address, as
  in the feedback Worker (changed on 2026-10-03, see
  `docs/analytics/b-9-tasks.md`, decisions 2 and 3). The Worker does not
  store or log the IP address.

Table:

```sql
CREATE TABLE analytics_session (
  session_id TEXT PRIMARY KEY,
  first_received_at INTEGER NOT NULL,
  last_received_at INTEGER NOT NULL,
  schema_version INTEGER NOT NULL,
  notice_version INTEGER NOT NULL,
  app_version TEXT NOT NULL,
  os_family TEXT NOT NULL,
  snapshot INTEGER NOT NULL,
  final INTEGER NOT NULL,
  duration_s INTEGER NOT NULL,
  active_s INTEGER NOT NULL,
  counters TEXT NOT NULL CHECK (json_valid(counters) AND length(counters) <= 4096)
);
```

- The Worker inserts the first summary. It updates the row only when the new
  `snapshot` is higher (`ON CONFLICT ... DO UPDATE ... WHERE`).
  `first_received_at` does not change.
- The Worker rounds `first_received_at` and `last_received_at` down to the
  hour. Thus the server also keeps no exact clock time.
- The daily cron deletes rows with `last_received_at` older than 90 days.
- SQL files in `analytics-worker/queries/` answer the three questions. Run
  them with `npx wrangler d1 execute <db> --remote --file <query>`. There is
  no dashboard. Exclude `app_version = 'synthetic-smoke'`.
- The queries do not use `final = 0` as a crash count. They use
  `unclean_exit`.

## Privacy notice

- The notice is a page of the landing site (B-10). Thus the landing site
  must be live before the first release with analytics. The build gets the
  page URL through `-AnalyticsPrivacyUrl`, so the app code does not depend
  on the site.
- A page with the notice version, the effective date, the full counter list,
  what the app does not send, Cloudflare as the processor, D1 in the EU
  jurisdiction, the 90-day deletion, how to turn off analytics, and the
  contact e-mail.
- The page also tells about the version check request to GitHub
  (`docs/licensing/build-expiry-spec.md`). That request is separate from the
  analytics consent.
- The consent dialog and the Privacy page link to it. Change the "Collected"
  text of the Privacy page to "Counts of the tabs and features you use,
  export results, session length, app version, and OS family".
- Change the consent dialog text from "anonymous product events" to
  "anonymous usage counts".
- The page tells that data already sent stays for a maximum of 90 days after
  the user turns off analytics. We cannot delete it on request, because no
  value links a row to a user. Cloudflare receives the IP address to deliver
  the request. The Worker does not store it.
- At the first launch, show the Help overview after the user answers the
  consent dialog. At this time, both windows open together.

## Build and release

- `Build-AppImage.ps1` gets `-AnalyticsEndpoint`, `-AnalyticsPrivacyUrl`, and
  `-AnalyticsNoticeVersion`. It adds the three JVM properties only when all
  three are given.
- `Validate-AppImage.ps1` refuses an image without the three valid
  properties. A local build can give `-AllowNoAnalytics` (decided on
  2026-10-03).
- The release workflow passes the three values from GitHub variables. They
  are not secrets. The workflow does not deploy the Worker.
- Add to `release-checklist.md`: the privacy URL opens; the Worker returns
  204 for a synthetic summary; a fresh install shows the consent dialog; after
  "Enable analytics", a row appears in D1 within 5 minutes; the deployed
  Worker knows all counter keys of the release.

## Tests

- Contract fixtures (`analytics-contract/v1`): valid and invalid summaries.
  The Kotlin tests and the Worker tests both read them.
- Kotlin: counter key list, JSON body (`app_version` is `BuildInfo.VERSION`,
  see `VersionSourceTest`; all `app_version` values are valid), timer and "send only if changed" (an
  idle app does not send), active time stops when the window is not active,
  the exit sequence and the 500 ms limit, no send after disable, consent
  stays enabled after `close()` (done), `unclean_exit` and the session
  bucket, stop after 410, daemon threads, and one test for each place in
  "Where to record".
- Worker (vitest with a fake D1): each status code, upsert only with a higher
  snapshot, kill switch, retention, times rounded to the hour, no IP
  address or headers in stored values, and all `app_version` values (also a
  missing one) are accepted and stored as described in "Wire format".
- A guard test for the new architecture rule.

## Work order

1. Contract fixtures, the counter list in Kotlin and TypeScript, the Worker
   validation and the Worker tests.
2. Client: `SessionCounters`, `EnabledAnalytics` with the timer, the active
   time and the final send, transport changes, the session count and the
   open-session flag. Remove the buffer, the envelope and the sequence
   numbers.
3. Record actions in the features. Add the exit sequence. Change the
   architecture rule.
4. Worker: migration, upsert, rate limit, retention, SQL queries.
5. Privacy notice text (for the landing site), dialog and page text, the
   Help overview after the consent dialog, build parameters, release
   checklist.
6. Manual deployment by the operator: D1 in the EU jurisdiction, Worker
   deployment, synthetic test, then turn on ingestion.
