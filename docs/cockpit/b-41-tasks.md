# B-41 Tasks: Cockpit (admin dashboard)

This file tracks the work of B-41 (a private dashboard for the author). The
backlog item is in `docs/backlog.md`. The ideas for later are in B-43.

## Goal

The author opens one page and sees what happens with the app:

- Do people visit the site?
- Do people download the app?
- Do people start and use the app? Which features and exports?
- Which errors occur?
- Which feedback comes in?

## Decisions

Decided on 2026-10-06:

| # | Decision |
|---|---|
| 1 | The name is "cockpit". The code is a new Worker in `cockpit-worker/` (Worker name `bananashot-cockpit`). It is not a part of the analytics Worker: the cockpit only reads, and a fault in it must not stop the ingestion of the app data. |
| 2 | The cockpit reads the D1 databases of the analytics Worker (`ANALYTICS_DB`) and of the feedback Worker (`FEEDBACK_DB`) with its own bindings. It does not write to them. It does not apply their migrations on the remote databases. |
| 3 | The cockpit has its own D1 database `bananashot-cockpit` for the download history. An hourly cron reads the GitHub releases and writes the `download_count` of each release file for the current UTC day. The last write of a day is the total of the day. The downloads of a day are the increase from the previous snapshot day. The first snapshot day is the base line and has no value. |
| 4 | The download history keeps only the releases of the app: no drafts, and no tags that start with `natives-`. "Setup" is `BananaShot-win-Setup.exe`. All other files are "other files". The Setup count includes the downloads of "Update and restart" (B-30), so the page shows the new installs (first sessions) next to it. |
| 5 | The site numbers come from the Cloudflare GraphQL Analytics API (Web Analytics, `rumPageloadEventsAdaptiveGroups`). The health of the Workers comes from `workersInvocationsAdaptive`. Both need an API token with "Account Analytics: Read". Without the token, these two sections show "not configured" and the other sections work. The site numbers are filtered by the host (`SITE_HOST`, default `banana-shot-editor.app`), because the automatic setup of Web Analytics shows no site tag. `CF_WEB_ANALYTICS_SITE_TAG` is optional and replaces the host filter. |
| 6 | Each data source is a separate section of the API response. A failure in one source shows an error in its section only. |
| 7 | Access: HTTP Basic authentication with the secret `COCKPIT_PASSWORD`. The user name is not checked. Without the secret, the Worker returns `503` for each request (fail closed). The static files of the page are also behind the check (`run_worker_first`). Cloudflare Access is a better option for later (B-43). |
| 8 | The page shows only totals for the analytics data. It never shows a single analytics session. |
| 9 | The page shows the feedback reports one by one, because the author reads them anyway in Telegram (same purpose, B-8). It shows the topic, the date, the version, the OS, the text, the first line of the error, and the report ID. It does not show the email address, only "email given". The author replies from Telegram. |
| 10 | The page excludes `app_version = 'synthetic-smoke'` always. It excludes the development versions (`%-SNAPSHOT`, B-9 decision 18) by default. A switch on the page includes them. |
| 11 | The periods are the last 7, 30, or 90 days (UTC days). 90 days is the retention of the analytics and the feedback data. Each number has the change against the previous period of the same length. The weekly charts always show the last 12 weeks (Monday to Sunday, UTC). |
| 12 | The page is plain HTML, CSS, and JavaScript in `cockpit-worker/public/`, with no build step and no external scripts. The charts are SVG that the page draws itself. The page had a light and a dark mode; decision 16 replaces them with one theme. |
| 13 | "Crash-free sessions" is `1 - (sum of unclean_exit) / sessions`. `unclean_exit` comes with the next session after a crash, so a crash shows on the day of the next start. |
| 14 | The tests use real SQL: an adapter on `node:sqlite` with the migrations of the three databases. Thus the queries are tested as they run in D1. |
| 15 | The local preview has no password prompt (asked by the author). The Worker skips the check only when both are true: `COCKPIT_DEV_NO_AUTH = "true"` (only in `.dev.vars`, which `wrangler deploy` does not use) and the request host is `localhost`, `127.0.0.1`, or `[::1]`. Never put `COCKPIT_DEV_NO_AUTH` in `wrangler.toml` or in a secret. |
| 16 | The page has one theme: mid-grey dark (asked by the author on 2026-10-06). It does not follow the light or dark setting of the system. Page `#2a2c30`, cards `#34363b`. The chart colors are the dark steps of the dataviz palette; the validator passes on `#34363b` (worst adjacent CVD ΔE 8.4). Red is lighter on this surface: marks `#e65c5c` (3.5:1) and text `#f38585` (4.9:1). |

## Open questions

Write each new decision in "Decisions" at once.

- Q1: Does the Web Analytics API accept a 90-day range on the free plan? If
  not, the cockpit falls back to 30 days and shows a note. Check after the
  deploy.
- Q2: closed (2026-10-06). The filter `requestHost` works with the real API: the deployed
  cockpit shows the site visits and the download page views. No site tag is needed.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| T1 | Worker skeleton: config, auth, static page, tests with `node:sqlite` | — | done |
| T2 | Download snapshots: cron, GitHub API, D1 table, daily and weekly series | T1 | done |
| T3 | App usage, errors, and exports from the analytics database | T1 | done |
| T4 | Feedback from the feedback database | T1 | done |
| T5 | Site numbers and Worker health from the GraphQL API | T1 | done |
| T6 | The page: KPI tiles, funnel, charts, tables, period switch, dark mode | T2–T5 | done |
| T7 | Local preview with seed data, README, deploy steps | T6 | done |
| T8 | Deploy: database, secrets, token, first snapshot | T7 | done |

Status values: `open`, `in-progress`, `done`.

- T1 to T7 (2026-10-06): 22 tests pass (`npm test`), and the types check. Checked in the local
  preview with the seed data: all sections load, the tooltips work, light and dark mode, and no
  sideways scroll at 375 px. The site and Worker sections were checked with a fake API answer in
  the browser, because the local preview has no token.
- T8 (2026-10-06): the database `bananashot-cockpit` is made in the EU jurisdiction
  (ID `a35c78d9-d7a9-46bc-bd1f-ba6099c56046`), and its migration is applied. The Worker is
  deployed (wrangler 4.147.0, version ID `99328488-e761-4837-9229-07b4379b935a`) at
  `https://bananashot-cockpit.banana-shot-feedback.workers.dev`, with the cron `17 * * * *`.
  Checked: without the secret, each path returns `503` (the page, the static files, and the
  API). Open: the author sets `COCKPIT_PASSWORD` and `CF_API_TOKEN`, then checks the page,
  takes the first snapshot, and checks Q1 and Q2.
- T8 (2026-10-06): the password works. The first `CF_API_TOKEN` had 1 character (the paste
  into the hidden prompt of `wrangler secret put` failed in Windows PowerShell). The Worker now
  refuses a token with an invalid format with a clear message, and trims white space. The
  first request to the Cloudflare API from the Worker often fails at once with "Network
  connection lost"; the Worker tries once more. The crash-free rate of 76.9% came only from
  the test builds of the author (0.9.0 and 0.9.1 on the day of the update test).
- T8 (2026-10-06): the author set the real token (the first tries went to the analytics
  Worker; that copy is deleted). Checked on the deployed page: all sections load with no
  error in the Worker log. The first download snapshot comes with the first app release
  (GitHub has only the `natives-` release now). Q1 (90 days) is still open.
