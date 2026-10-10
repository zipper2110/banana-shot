# B-11 E2: Language Data

This file holds the tasks of epic E2 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 10 (Analytics), 11 ("Feedback"), 14 (Documentation)
- Depends on: — (it does not depend on E1)
- Feature: the cockpit shows the Windows languages of the users. The
  feedback message shows the language of the user. The author can select
  the next languages from real data.
- Prepares: the choice of the languages for E4, E7, and E8. The data needs
  some weeks, so do this epic early. Release it some weeks before E4.
- Size: a few days, plus the time for the data.

Order of the deployment: the workers accept the new fields before the app
release that sends them (E2-T7).

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E2-T1 | The values of `os_language` in the analytics contract | — | open |
| E2-T2 | The analytics worker and the cockpit | E2-T1 | open |
| E2-T3 | The app sends `os_language` | E2-T1 | open |
| E2-T4 | Privacy notice, consent, and notice version | E2-T1 | open |
| E2-T5 | Language fields in the feedback report | — | open |
| E2-T6 | Feedback form: the languages of the answer | — | open |
| E2-T7 | Deployment and release | E2-T1–E2-T6 | open |

### E2-T1 The values of `os_language` in the analytics contract

- Status: open
- Plan: 10
- Work:
  - Make the list of the values: the primary language subtags (ISO 639-1)
    of about 40 languages, plus `other`. Use the languages of the Windows
    display languages. The list must contain all candidates of E7 and E8.
  - Add the attribute `os_language` to
    `analytics-contract/v1/attributes.json`.
  - Add valid and invalid examples to `valid-summaries.json` and
    `invalid-summaries.json` (an unknown value, a region tag such as
    `es-ES`, upper case).
  - Decide if the attribute needs a new `schema_version`. The attributes are
    one JSON column (migration `0003_attributes.sql`), so a new column is
    not necessary.
- Acceptance:
  - The contract has the list, and each value has a reason (a Windows
    display language).
  - The examples cover the new attribute.
- Tests: —

### E2-T2 The analytics worker and the cockpit

- Status: open
- Plan: 10
- Work:
  - `analytics-worker/src/attributes.ts`: accept `os_language` with the
    values of the contract. Keep the test against the contract.
  - Add a query in `analytics-worker/queries/` that counts the extended
    sessions for each `os_language` and each week.
  - `cockpit-worker/src/app-usage.ts`: show the distribution of
    `os_language`.
- Acceptance:
  - The worker tests accept the valid examples and reject the invalid
    examples of E2-T1.
  - The cockpit shows the languages with the synthetic data of the tests.
- Tests: —

### E2-T3 The app sends `os_language`

- Status: open
- Plan: 10
- Work:
  - Add `os_language` to `AnalyticsEvent` and to the attribute list of
    `AnalyticsSchema`. Keep the test against the contract.
  - `app` reads `Locale.getDefault(Locale.Category.DISPLAY).language` at
    the start, and changes a value that is not in the list to `other`.
    `analytics` stays a leaf package: it gets the value from `app`.
  - Only the extended level sends the attribute. The essential level does
    not change.
- Acceptance:
  - With the display language `es-AR`, the summary has `"os_language":
    "es"`.
  - With a language that is not in the list, the summary has `"other"`.
  - An essential summary has no `os_language`.
- Tests: —

### E2-T4 Privacy notice, consent, and notice version

- Status: open
- Plan: 10, "Decisions" ("Notice version")
- Work:
  - `docs/analytics/design.md`: the new attribute in "Attributes".
  - The privacy notice: `site/public/privacy/index.html` (the full text)
    and the maintainer notes in `docs/analytics/privacy-notice.md`.
  - The app texts: `AnalyticsConsentDialog` and `PrivacyPage`.
  - Increase `AnalyticsSchema.NOTICE_VERSION` from 2 to 3. Change
    `$expectedNoticeVersion` in `distribution/windows/Build-AppImage.ps1`
    and the GitHub variable `ANALYTICS_NOTICE_VERSION`
    (B-9 decision 17). The app asks for consent again.
- Acceptance:
  - `AnalyticsBuildConfigTest` passes with the new notice version.
  - The texts of the app and of the site name the Windows language.
  - A user with the stored notice version 2 sees the consent dialog again.
- Tests: —

### E2-T5 Language fields in the feedback report

- Status: open
- Plan: 11 ("Feedback")
- Work:
  - `feedback-contract/v1/report.schema.json`: the optional fields
    `app_language` and `os_language`. `os_language` uses the values of
    E2-T1. `app_language` uses the values of the analytics attribute
    `language`. Add examples to `valid-reports.json` and
    `invalid-reports.json`.
  - `feedback-worker/src/validation.ts`: accept the new fields. The schema
    has `additionalProperties: false`, so the worker must accept them
    before the app sends them.
  - `feedback-worker/src/telegram.ts`: show the two languages in the
    message.
  - The app (`feedback` package) adds the two fields to the report. `app`
    gives the values, so `feedback` stays a leaf package.
  - `feedback-contract/v1/README.md`, `docs/feedback/privacy-notice.md`,
    and the site privacy page: the new fields.
- Acceptance:
  - The worker tests accept a report with and without the new fields.
  - The Telegram message of the smoke report shows the languages.
- Tests: —

### E2-T6 Feedback form: the languages of the answer

- Status: open
- Plan: 11 ("Feedback")
- Work:
  - The author decides in which languages the author can answer.
  - The feedback form (`ui/feedback`) tells these languages. Example: "You
    can write in English or Spanish."
- Acceptance:
  - The form shows the text. A presenter test checks it.
- Tests: —

### E2-T7 Deployment and release

- Status: open
- Plan: 10 ("Order"), 11
- Work:
  - Deploy the analytics worker, the feedback worker, and the cockpit.
  - Deploy the site with the new privacy notice.
  - Then release the app with E2-T3 to E2-T6.
  - `docs/release-checklist.md`: the order "workers and site first, then
    the app" for a change of a contract.
- Acceptance:
  - The smoke summary and the smoke report with the new fields pass on the
    production workers before the release.
  - The cockpit shows the first `os_language` data after the release.
- Tests: — (manual)

## Done when

- All tasks are `done`.
- The cockpit shows the Windows languages of the users.
- The feedback message shows the app language and the Windows language.
