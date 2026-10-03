# B-8 Tasks: Feedback from Users

This file tracks the work of B-8 (bug reports, ideas, and questions from
users). The backlog item is in `docs/backlog.md`.

## Goal

Each user can send a problem, an idea, or a question to the author in a few
clicks. The user does not need an email app or an account. The author can
reply when the user gives an email address.

## Decisions

Decided on 2026-10-03:

| # | Decision |
|---|---|
| 1 | The first release has an in-app feedback form (option B). The app sends the report to our Cloudflare Worker. The Worker sends it to the author. |
| 2 | The email address is optional. The form tells the user that the author can reply only to an address. |
| 3 | The user can attach the log files. The user sees the data before the app sends it. Attachment is off by default. |
| 4 | Users find the form from three entry points: a sidebar button, the error dialogs, and the Help pages. |
| 5 | When the app cannot send, the user can copy the report or write an email (`mailto:`). The app keeps the text. |
| 6 | Replies inside the app (option C) come after the first release. See B-33 in `docs/backlog.md`. |
| 7 | The author has no dedicated mail address now. The first release uses the personal Gmail (`CONTACT_EMAIL`) for the replies. When the app domain exists (B-10), change to an address on that domain. |
| 8 | Q1: a separate Worker `feedback-worker`, not a route in `analytics-worker`. It has its own storage, kill switch, and retention. Reports contain personal data (an email address and logs), and analytics data is anonymous. |
| 9 | Q2: the Worker sends each new report to the author with a Telegram bot. The reason: the Cloudflare `send_email` binding needs a domain on Cloudflare, and the author has no domain yet. The bot token and the chat ID are Worker secrets. The author replies from Gmail. When the domain exists, change to the Cloudflare email (or add it). |
| 10 | Q3: the Worker does not keep the log. It sends the log to Telegram as a file and then drops it. D1 keeps only the report text and the app data. Thus, the server keeps less personal data. |
| 11 | Q4: the Worker keeps a report row for 90 days, the same as the analytics retention. Then the daily cron job deletes it. The copy in the Telegram chat is the author's responsibility (a bot can delete its messages only for 48 hours). |
| 12 | Q5: the app attaches the last 2 MB of the log: the end of `bananashot.log`, and the end of `bananashot.1.log` if the newest file is shorter than 2 MB. The app compresses it with gzip (about 200 KB). |
| 13 | T1 rate limit: 30 reports from one IP address in one hour (changed from 10 on 2026-10-03: users behind one shared address, for example an office or a club, must not block each other). The key of a count row is an HMAC of the hour and the address with the Worker secret `RATE_LIMIT_KEY`. Each request deletes the rows of the previous hours. |
| 14 | T1: the body limit is 3 MiB (3,145,728 bytes). The Worker sends the log to Telegram as a `.log.gz` file. It does not decompress it. |
| 15 | T1: `message` must contain a character that is not white space. Error bodies are `{"error": "<code>"}` and never contain a request value. See `feedback-contract/v1/README.md`. |
| 16 | T2: "Send" needs a topic and a message. The sidebar opens the form with no topic, so the user must select one. |
| 17 | T2: the form keeps the draft when the user closes it. The `report_id` stays until the Worker has the report (`200` or `201`). Thus, "Try again" and a send after an edit use the same ID. A new report starts after "New report" or at the next open after a sent report. |
| 18 | T2: "Show the data" shows the report JSON without the `log` key, and then the plain log text. The app reads the log one time for each report ID, so "Show the data" and "Send" use the same text. |
| 19 | T2: after `410`, `429`, a `5xx`, or no connection, the form offers "Try again". After another status (for example `422`), a new try cannot succeed: the form offers only "Copy report" and "Write an email". |
| 20 | T2: the send timeout is 30 seconds. The client follows no redirect. "Write an email" puts a maximum of 1,500 characters in the `mailto:` link. For a longer text, the app also copies the full report to the clipboard. |
| 21 | T3: the entry points are callbacks from `app`, so the tab, Help, and More packages do not depend on `ui.feedback`. "Report this problem" is in "Unexpected error", "Export failed", and "Failed to read manifest" of the export. The error text is the title and the text of the dialog. |
| 22 | T4: the site text is in `docs/feedback/privacy-notice.md`. It goes on the site with the analytics notice (B-10). |
| 23 | T5: `Validate-AppImage.ps1` refuses an image without a valid endpoint. A local build can give `-AllowNoFeedbackEndpoint`. The release workflow never gives it. |
| 24 | T2 (changed on 2026-10-03 by the author): after a successful send, the form closes, and a popup shows the thank-you text with the report ID. The popup has only a Close button. The form has no "sent" state and no "New report" button. |

## Open questions

None. Write each new decision in "Decisions" at once.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| T1 | Feedback Worker (`feedback-worker`) | — | done |
| T2 | Feedback form in the app | the contract of T1 | done |
| T3 | Entry points | T2 | done |
| T4 | Texts and privacy | T2 | done |
| T5 | Build, deployment, and release checks | T1–T4 | in-progress |

Status values: `open`, `in-progress`, `done`. When a task is done, write
the test classes in its "Tests" line. Do not remove the task.

Next work (2026-10-03): the code of T1–T5 is done, and the Worker is
deployed. The author must set the GitHub variable `FEEDBACK_ENDPOINT` and do
the manual check of T5 with an installed dry-run build. The site text of T4
waits for the site (B-10).

### T1 Feedback Worker

The server that receives the reports, keeps them, and sends them to the
author.

- Status: done. The deployment is in T5.
- Contract:
  - A JSON schema and fixtures describe the report. The app tests and the
    Worker tests use the same fixtures (as `analytics-contract` does).
  - Fields: `report_id` (random UUID v4, made by the app), `topic`
    (`problem`, `idea`, `question`, or `other`), `message` (1 to 10,000
    characters), `email` (optional), `app_version`, `os_name`, `os_version`,
    `java_version`, `error` (optional, the text of the error dialog), and
    `log` (optional, gzip, Base64).
  - `report_id` stays the key of the report. B-33 uses it for replies.
- Receive and keep:
  - `POST /v1/feedback` accepts a valid report and returns `201` with the
    `report_id`.
  - The Worker refuses a request that is not JSON (`415`), a body larger
    than 3 MB (`413`), and an invalid report (`422`).
  - The Worker keeps the same `report_id` only one time. A second request
    with the same ID returns `200` and changes nothing. Thus, a retry from
    the app does not make a copy.
  - The kill switch `FEEDBACK_INGESTION_ENABLED` is `false` by default. When
    it is not `true`, the Worker returns `410`.
  - The Worker limits the number of reports from one IP address in each
    hour. Over the limit, it returns `429`. The Worker does not keep the IP
    address.
- Send to the author:
  - For each new report, the Telegram bot sends a message to the author
    chat (decision 9). The message has the topic, the `report_id`, the user
    address, the app data, and the message text.
  - A Telegram message has a limit of 4,096 characters. A longer report
    text goes as a file. The log goes as a file.
  - The Worker keeps the log only in memory during the request
    (decision 10).
  - If Telegram fails, the Worker keeps the row with `delivered = false` and
    returns `503`. The app keeps the report and tries again (T2). A retry
    with the same `report_id` sends to Telegram again. Thus, the log is not
    lost. This changes the rule of the second request above: `200` with no
    change applies only to a delivered report.
- Retention: a daily cron job deletes report rows older than the retention
  of decision 11 (90 days).
- Tests: `feedback-worker/test/index.test.ts`, `validation.test.ts`,
  `telegram.test.ts`, `retention.test.ts` (104 tests). The Kotlin side of the
  contract: `FeedbackReportTest` (with `FeedbackContract`).

### T2 Feedback form in the app

The dialog where the user writes and sends a report.

- Status: done
- Build configuration:
  - The JVM property `bananashot.feedback.endpoint` sets the endpoint. It
    must be an `https` URL that ends in `/v1/feedback`.
  - Without a valid endpoint, the form opens, but "Send" is replaced with
    "Copy report" and "Write an email". Development runs work with no
    server.
- The form:
  - The dialog has: the topic (Problem, Idea, Question, Other), the message,
    the optional email address, and the checkbox "Attach the log files".
  - The dialog shows the data that the app adds: the app version, the
    version of Windows, and the Java version.
  - "Send" is not available while the message is empty.
  - The dialog remembers the email address for the next report. The user
    can delete it.
  - An entry point can fill in the topic and the error text (T3).
- Log attachment:
  - The app attaches the log data of decision 12 only when the checkbox is on.
  - "Show the data" opens the exact text that the app sends.
  - The form tells that the logs contain the names and folders of videos
    and projects, but not the videos.
- Send, result, and failure:
  - The send runs in the background. The dialog shows the progress, and the
    user can continue to use the app.
  - After `201` or `200`, the form closes and starts a new report. The app
    keeps the email address. A popup "Report sent" shows "Thank you", the
    report ID, and if the author can reply. It has only a Close button
    (decision 24).
  - On a network error, a `5xx`, a `410`, or a `429`, the dialog keeps the
    text and offers "Try again", "Copy report", and "Write an email".
    "Try again" sends the same `report_id`.
  - The send uses the system proxy and the trust of E5-S4 of
    `l-5.2-epics.md`.
  - No test sends a request to a real host (`NetworkGuardExtension`).
- Tests: `FeedbackReportTest`, `FeedbackLogTest`, `HttpFeedbackSenderTest`
  (local HTTP and HTTPS servers, the trust of E5-S4),
  `DefaultFeedbackPresenterTest`, `FeedbackPanelTest`.

### T3 Entry points

The places where the user finds the form.

- Status: done
- Sidebar: a "Feedback" button next to "Help". It is always visible, also
  with no open project. It opens the form with no topic.
- Error dialogs: the "Unexpected error" dialog and the error dialogs of the
  export have a "Report this problem" button. It opens the form with the
  topic "Problem", the error text, and the checkbox "Attach the log files"
  on. The user can change all of it.
- Help pages: the end of each page has the line "Is something not clear?
  Tell us." The link opens the form with the topic "Question".
- Contact page in More: "Send feedback" is the first action. The email
  address stays as the second way.
- Tests: `HelpPanelTest` (the "Tell us" link on each page), `MorePanelTest`
  (the first action of Contact), `DefaultFeedbackPresenterTest` (the requests
  of the entry points). The error dialogs and the sidebar button have no
  automatic test: check them with the release checklist.

### T4 Texts and privacy

The texts that tell the user what the app sends and why.

- Status: done. The site text (`docs/feedback/privacy-notice.md`) waits for
  the site (B-10).
- The Privacy page in More and the privacy policy on the site tell: what a
  report contains, where it goes, how long the server keeps it, and how to
  ask for its deletion. The texts name Telegram, because each report goes
  through Telegram to the author (decision 9).
- The analytics consent does not cover the feedback. A report goes only
  when the user clicks "Send".
- Reply address: the form and the Contact page show the address of
  decision 7. When the domain exists, a change of `CONTACT_EMAIL` in
  `SwingApplicationFactory` replaces the address.
- Tests: `PrivacyPageTest`, `FeedbackPanelTest` (the reply address).

### T5 Build, deployment, and release checks

The steps that make the feature work in a release build.

- Status: in-progress. The scripts, the workflow, the README, and the
  checklist are done. The Worker is deployed (2026-10-03). Open: the
  GitHub variable `FEEDBACK_ENDPOINT` and the manual check from an
  installed dry-run build (the author does them).
- Deployed (2026-10-03): the Worker `bananashot-feedback` at
  `https://bananashot-feedback.banana-shot-feedback.workers.dev/v1/feedback`,
  with D1 `bananashot-feedback`, the three secrets, and the daily cron
  `15 3 * * *`. With the kill switch off, the Worker returned `410`. With
  the kill switch on, the synthetic report returned `201` and the author
  chat got the message. A second send returned `200`.
- Deployment fixes (2026-10-03): the cron syntax in
  `wrangler.toml.example` was wrong (`[[triggers.crons]]`). It is now
  `[triggers] crons = [...]`. `wrangler d1 create` offered a binding with
  a different name. The example and the README now tell to keep
  `FEEDBACK_DB`.
- Build: `Build-AppImage.ps1` has the parameter `-FeedbackEndpoint`.
  `windows-release.yml` gives it from a GitHub variable.
  `Validate-AppImage.ps1` checks that a release build has the property.
- Deployment: the Worker and D1 are deployed. The Telegram bot token and
  the chat ID are Worker secrets. `wrangler.toml.example` and a README tell
  how to deploy and how to make the bot.
  `FEEDBACK_INGESTION_ENABLED` is `true` in production.
- Manual check from an installed dry-run build:
  - Send a report with a log and an email address. The author gets the
    Telegram message with the log file.
  - With the network off, the dialog keeps the text and offers the
    fallbacks.
  - Add these checks to `release-checklist.md` (done: steps 3 and 7).
- Tests: no automatic test. The release workflow runs `Validate-AppImage.ps1`.
