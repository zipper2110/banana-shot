# B-9 Tasks: Usage Analytics

This file tracks the work of B-9 (usage analytics). The backlog item is in
`docs/backlog.md`. The design is in `docs/analytics/design.md`. The design
is the reference for each detail. This file tells what is done and what is
open.

## Goal

The app sends an anonymous session summary to our Worker. The summary answers
the three questions of the design: which tabs and features users use, how
exports go, and how long sessions are. Analytics ship in the first release.

## State on 2026-10-03 (before T1)

The code was still the first attempt (event batches). The redesign of
2026-10-01 (session summaries) had not started. At that time:

- No build sends analytics. `EnabledAnalytics` puts events in a buffer and
  never sends them. No code creates `JdkAnalyticsTransport`.
- Only `windowClosing` records an event (`SessionEnded`). No feature records
  an action.
- The Worker, the migration, and the contract fixtures use the old
  `POST /v1/events/batch` format.
- No build sets the three JVM properties. Thus the app never shows the
  consent dialog.
- The Worker and D1 are not deployed.

Parts that work and stay:

- `AnalyticsController`: on/off switch. `close()` keeps the consent choice.
- `AnalyticsPreferences`: consent choice and notice version.
- `AnalyticsBuildConfig`: the three JVM properties.
- `AnalyticsConsentDialog` and `PrivacyPage` (the texts must change, T5).
- At the first start, the Overview help opens after the consent dialog
  closes (`AnalyticsConsentDialog.show(onClosed)`).
- `main` sets `java.net.useSystemProxies=true`.
- Worker: kill switch, 90-day retention cron, vitest with a fake D1.

## Decisions

Write each new decision here at once. The design decisions are in
`docs/analytics/design.md`.

| # | Decision |
|---|---|
| 1 | (2026-10-03) The work follows the "Work order" of the design. The tasks below are the steps of that order. |
| 2 | Q1 (2026-10-03, the author): the rate limit works as in the feedback Worker (B-8 decision 13): a D1 count row for each hour, with an HMAC of the hour and the IP address as the key, and the Worker secret `RATE_LIMIT_KEY`. Each request deletes the rows of the previous hours. This replaces the "Workers rate-limit binding" of the design. |
| 3 | Q1: the limit is 300 summaries from one IP address in one hour. One app sends a maximum of about 14 summaries in one hour (start, 12 timer sends, exit). Thus about 20 apps behind one shared address do not block each other. Over the limit, the Worker returns 429. The app does not retry, and the next summary replaces the lost one. |
| 4 | Q2 (2026-10-03, the author): `Validate-AppImage.ps1` refuses an image without the three valid analytics properties, as it does for the feedback endpoint (B-8 decision 23). A local build can give `-AllowNoAnalytics`. The release workflow never gives it. |
| 5 | Q3 (2026-10-03, the author): the privacy notice goes only on the landing site (B-10). There is no temporary page. B-10 has a reminder. |
| 6 | T1 (2026-10-03): the Worker responses have no body. The check order is: kill switch (410), path (404), method (405), content type (415), size (413, 8 KiB in bytes), JSON and schema (400), rate limit (429), store (204, or 503 when D1 fails). Thus a request that is not valid does not count for the rate limit. |
| 7 | T1: a `schema_version` below `MIN_SCHEMA_VERSION` gets 410 before all other body checks, so that an old app stops sending. Now `MIN_SCHEMA_VERSION` is 1, so no version gets 410. A version above the current version is invalid (400). The Worker accepts a counter value of 0, although the app does not send it. |
| 8 | T1: `analytics-contract/v1/counter-keys.json` is the reference list of the 74 counter keys. The Kotlin list (`AnalyticsSchema.COUNTER_KEYS`) and the TypeScript list (`src/counters.ts`) each have a test against it. `smoke-summary.json` is the synthetic summary for the deployment check. |
| 9 | T3 (2026-10-03): `AnalyticsTransport.post(body)` is a blocking send that returns the status. `EnabledAnalytics` owns one daemon thread for the timer and the sends. The 500 ms exit limit is in `EnabledAnalytics.close()`: it waits for the final send until 500 ms after `beginFinalSend()`. This replaces `sendWithin(payload, 500 ms)` of the design. |
| 10 | T3: the `app` package makes the HTTP client with `UpdateTrust.production` (the trust of E5-S4 and the system proxy, as the feedback sender), with no redirect, a connect timeout of 3 s, and a request timeout of 5 s. `analytics` stays a leaf package: it gets the app version and the client from `app`. |
| 11 | T3: the controller keeps the last tab and window state. When analytics starts later (consent, or the Privacy page), the new session gets them, without a count of a tab open. The open-session flag is cleared by a JVM shutdown hook and by `disable()`, not by `close()`. |
| 12 | T4 (2026-10-03): Crop/Rotate counts one change for each visit of the tab, when the tab closes. A drag sends many changes, so a count for each change has no meaning. A color slider counts one change when the user releases it. A change counts only when the stored adjustments change, because the store clamps each value. |
| 13 | T4: the app is active when the active window is the main window or a window that the main window owns (a dialog). A `KeyboardFocusManager` listener on `activeWindow` records `WindowActive`. Thus `active_s` does not stop while a dialog of the app is open. |
| 14 | T4: the export code does not use `analytics`. `RenderQueueManager` gives the facts of each run (`RenderRunFacts`, no path or name) to a `RenderRunListener`, and `app.RenderAnalytics` changes them into events. The listener is removed at the start of the exit, so the cancel of the exit does not count as cancelled. The running exports count as interrupted. The run time counts only when the video length is known. |
| 15 | T4: only a tab change by a click of the user counts as a tab open (`byUser`). A tab change by code (startup, "Go to point", the stats link) counts tab time but no open. |
| 16 | T5 (2026-10-03): the "Excluded" text of the Privacy page also changes. The old text said "identifiers", but a summary has a random session ID. The new text says "user or device IDs", and the notice tells that the session ID lives only in memory and does not link two sessions. The app texts are constants (`AnalyticsConsentDialog.INTRO`, `PrivacyPage.ANALYTICS_COLLECTED`, `ANALYTICS_EXCLUDED`), so that a test can check them. |
| 17 | T6 (2026-10-03): `Build-AppImage.ps1` refuses a part of the three analytics parameters, and it checks them with the rules of `AnalyticsBuildConfig`. The scripts keep the notice version as `$expectedNoticeVersion`; `AnalyticsBuildConfigTest` checks that it is `NOTICE_VERSION`. A new notice version thus needs a change of the scripts and of the GitHub variable. |
| 18 | (2026-10-03, the author): a development run can send to the production Worker. This is the easy way to test. The queries do not exclude the `-SNAPSHOT` versions. To exclude them later, filter on `app_version`. |

## Open questions

None. Write each new decision in "Decisions" at once.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| T1 | Contract and Worker validation | — | done |
| T2 | Worker storage, rate limit, and queries | T1 | done |
| T3 | Client core: counters, timer, transport | T1 | done |
| T4 | Record actions in the features | T3 | done |
| T5 | Texts and privacy notice | T1 | done |
| T6 | Build and release checks | T3 | done |
| T7 | Deployment and turn on | T1–T6, B-10 | in-progress |

Status values: `open`, `in-progress`, `done`. When a task is done, write
the test classes in its "Tests" line. Do not remove the task.

Next work (2026-10-03): T7, by the author. T7 also needs the landing site
(B-10).

### T1 Contract and Worker validation

Design: "Wire format", "Counters", "Worker and database", work order step 1.

- Replace the fixtures in `analytics-contract/v1` with valid and invalid
  session summaries. Keep the `app_version` cases (all values are valid).
- Put the closed counter key list in Kotlin and in TypeScript. A test on
  each side checks the list against the fixtures.
- Worker route: `POST /v1/session` only. Other routes return 404.
- Check order: kill switch (410), method, content type (415), size (413,
  8 KiB), JSON and schema (400). An old `schema_version` gets 410. Success
  returns 204.
- Counter values: integers in `0..1000000`. An unknown key is an error.
- Status: done.
- Tests: `analytics-worker/test/validation.test.ts`, `index.test.ts` (each
  status code). The Kotlin side of the contract: `AnalyticsSchemaTest`.

### T2 Worker storage, rate limit, and queries

Design: "Worker and database", work order step 4.

- Replace migration `0001` with the table `analytics_session`. No database
  is deployed, so a change in place is safe.
- Upsert: insert the first summary. Update only when `snapshot` is higher.
  `first_received_at` does not change.
- Round `first_received_at` and `last_received_at` down to the hour.
- Rate limit for each IP address (decisions 2 and 3). Copy the approach of
  `feedback-worker/src/rate-limit.ts`: a table `analytics_rate` and the
  secret `RATE_LIMIT_KEY`. Do not store or log the address. The retention
  cron also deletes old rate rows.
- Change the retention cron to the new table (`last_received_at` older than
  90 days).
- Fix the cron syntax in `wrangler.toml.example`. It is
  `[[triggers.crons]]`, which is wrong. Use `[triggers] crons = [...]`, as
  in the feedback Worker. Tell to keep the binding name `ANALYTICS_DB`.
- Add SQL files in `analytics-worker/queries/` for the three questions.
  Exclude `app_version = 'synthetic-smoke'`. Count crashes with
  `unclean_exit`, not with `final = 0`.
- Update `analytics-worker/README.md`: the new route and the deployment
  steps.
- Status: done. The queries ran on a SQLite copy of the migration with the
  contract fixtures.
- Tests: `analytics-worker/test/index.test.ts` (upsert, times, rate limit,
  no IP address or headers), `retention.test.ts`.

### T3 Client core: counters, timer, transport

Design: "Session", "When the app sends", "Desktop classes", work order
step 2.

- Change the `AnalyticsEvent` subtypes to match the counters. Features
  never see counter keys.
- `SessionCounters`: thread-safe counters. It makes the JSON body.
- `EnabledAnalytics`: owns the session, the counters, the active-time clock,
  and the 5-minute timer. It sends the first summary at session start. It
  sends every 5 minutes only if a counter or `active_s` changed.
  `beginFinalSend()` starts the last summary (`final = true`). `close()`
  waits for it until the 500 ms limit.
- `duration_s` uses a monotonic clock and is clamped to 7 days. `active_s`
  counts only while the main window is active.
- `JdkAnalyticsTransport`: change the path to `/v1/session`. Add
  `sendWithin(payload, 500 ms)`. Stop after a 410. Use
  `ProxySelector.getDefault()`. Log only the status class and the count of
  sends.
- `AnalyticsBuildConfig`: the endpoint must end in `/v1/session`.
- `AnalyticsPreferences`: add the session count and the open-session flag.
  A JVM shutdown hook and `disable()` clear the flag. Add `unclean_exit` and
  `session_n_<bucket>`.
- Remove `AnalyticsBuffer`, `AnalyticsEnvelope`, `AnalyticsSession`
  sequence numbers, and their tests.
- Status: done. `windowClosing` calls `beginFinalSend()` in place of
  `SessionEnded`. The full exit sequence is in T4.
- Tests: `EnabledAnalyticsTest`, `AnalyticsControllerTest`,
  `AnalyticsPreferencesTest`, `AnalyticsTransportTest`, `AnalyticsSchemaTest`,
  `AnalyticsBuildConfigTest`, `VersionSourceTest`.

### T4 Record actions in the features

Design: "Code structure", "Exit sequence", work order step 3.

- Change `docs/architecture-rules.md`: feature packages can import only
  `Analytics` and `AnalyticsEvent`. Add a guard test.
- Give `Analytics` to the features through the constructor. The default is
  `DisabledAnalytics`.
- Record each action of the "Where to record" table of the design:
  - Tabs: `goTo` gets a `byUser` flag. Tab time and `active_s` with a
    `WindowFocusListener`.
  - Uncaught errors: the default handler in `SwingMainApp.kt`.
  - Help opened: `showHelp`.
  - Projects: created, opened, video open failed.
  - Points: added, deleted, favorited. Comment added.
  - Scoring: score recorded.
  - Colors and Crop/Rotate: user input only.
  - Export: started, completed, failed (with reason), cancelled, options,
    resolution, run time, video length. Use a small listener on
    `RenderQueueManager`.
- Exit sequence in `windowClosing`: count running exports as interrupted,
  call `beginFinalSend()`, hide the window, then `handle.close()`. This
  replaces `record(SessionEnded)`.
- Status: done (decisions 12 to 15).
- Tests: `AnalyticsDependencyTest` (the guard), `SwingApplicationFactoryTest`
  (tabs, Help, project open, exit order), `DefaultProjectsPresenterTest`,
  `PointsCardSelectionTest`, `PointsDispatcherTest`,
  `SwingScoringPanelFlowTest`, `SwingColorAdjustmentsPanelTest`,
  `DefaultCropRotatePresenterAnalyticsTest`, `RenderAnalyticsTest` (also a
  failed run of the real render queue).

### T5 Texts and privacy notice

Design: "Privacy notice", work order step 5.

- Consent dialog: change "anonymous product events" to "anonymous usage
  counts".
- Privacy page: change "Collected: approved product action categories only."
  to the text of the design.
- Write the site text in `docs/analytics/privacy-notice.md`, as
  `docs/feedback/privacy-notice.md` does for the feedback. Content: the list
  of the design (notice version, date, counter list, what the app does not
  send, Cloudflare, D1 in the EU, 90 days, how to turn off, contact, the
  version check request).
- The site text goes on the site with the feedback notice (B-10).
- Status: done (decision 16). The site text is
  `docs/analytics/privacy-notice.md`. The effective date is written at the
  release.
- Tests: `PrivacyPageTest` (the Privacy page texts and the consent dialog
  text).

### T6 Build and release checks

Design: "Build and release", work order step 5.

- `Build-AppImage.ps1`: add `-AnalyticsEndpoint`, `-AnalyticsPrivacyUrl`,
  and `-AnalyticsNoticeVersion`. Add the three JVM properties only when all
  three are given. Do not use the old `tennis.record.` prefix of the
  shelved IntelliJ patch.
- `windows-release.yml`: give the three values from GitHub variables.
- `Validate-AppImage.ps1`: refuse an image without the three valid
  properties (decision 4). The endpoint is `https` and ends in
  `/v1/session`. The notice version is `NOTICE_VERSION`. Add the switch
  `-AllowNoAnalytics` for local builds.
- `release-checklist.md`: the privacy URL opens; the Worker returns 204 for
  a synthetic summary; a fresh install shows the consent dialog and then
  the Overview help; after "Enable analytics", a row appears in D1 within
  5 minutes; the deployed Worker knows all counter keys of the release.
- Dev run: to see the consent dialog, add the three properties to the VM
  options and clear `analytics.choice` and `analytics.noticeVersion` in the
  preferences. Write this in the README.
- Status: done (decision 17). The release checklist has the step "3a.
  Analytics Worker and privacy notice". `distribution/windows/README.md` tells
  about the parameters and the GitHub variables.
- Tests: `AnalyticsBuildConfigTest` (the scripts and the app agree). The
  check blocks of both scripts ran by hand on good, missing, partial, and bad
  values.

### T7 Deployment and turn on

Design: work order step 6. The author does these steps.

- Make D1 in the EU jurisdiction. Apply the migration. Set the secret
  `RATE_LIMIT_KEY` (a new random value, not the key of the feedback Worker).
- Deploy the Worker with `ANALYTICS_INGESTION_ENABLED=false`. Check the 410.
- Turn on ingestion. Send a synthetic summary
  (`app_version = 'synthetic-smoke'`). Check the 204 and the row.
- Set the three GitHub variables. The privacy URL is the notice page on the
  landing site (B-10, decision 5).
- Do the analytics steps of `release-checklist.md` with an installed
  dry-run build.
- Status (2026-10-03): in progress. The Worker is deployed on
  `bananashot-analytics.banana-shot-feedback.workers.dev` with ingestion on.
  The synthetic summary returned 204. The first deploy returned 503, because
  `d1 create` had set the binding name `bananashot_analytics` in place of
  `ANALYTICS_DB`. The synthetic row is in D1, with the receive times rounded
  down to the hour (18:20 gave 18:00 UTC). A development run (IDE, the three
  VM options) sent a full session: snapshot 0 at start, the final summary at
  exit, tab and export counters as expected, no tab open by code. The 410
  check is done, and the three GitHub variables are set. Open: the dry run
  with an installed build (the author), and the notice on the site (B-10).
