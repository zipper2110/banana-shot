# B-11 E2: Language Data

This file holds the tasks of epic E2 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 10 (Analytics), 11 ("Feedback"), 14 (Documentation)
- Depends on: — (it does not depend on E1)
- Size: a few days, plus some weeks for the data.
- Value:
  - The author selects the next languages from real data, not from a
    guess. The cockpit shows the Windows languages of the users.
  - The author knows the language of each feedback report, and can answer
    in this language.
- Prepares: the choice of the languages for E4, E7, and E8. The data needs
  some weeks, so do this epic early. Release it some weeks before E4.

Order of the deployment: the workers and the site accept and describe the
new fields before the app release that sends them (E2-T7).

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E2-T1 | The list of language values in the analytics contract | — | open |
| E2-T2 | The analytics worker and the cockpit | E2-T1 | open |
| E2-T3 | The app sends `os_language` | E2-T1 | open |
| E2-T4 | Privacy notice, consent, and notice version | E2-T1 | open |
| E2-T5 | Language fields in the feedback report | E2-T1 | open |
| E2-T6 | Feedback form: the languages of the answer | — | open |
| E2-T7 | Deployment and release | E2-T1–E2-T6 | open |

### E2-T1 The list of language values in the analytics contract

- Status: open
- Plan: 10
- Value: one fixed list of language values. The analytics data and the
  feedback reports use it, and it cannot hold free text that identifies a
  user.
- Requirements:
  - The list has the primary language subtags (ISO 639-1, lower case) of
    the Windows display languages, about 40 values, plus `other`.
  - The list contains all candidates of E7 and E8.
  - The contract has the new attribute `os_language` of the extended level,
    with this list.
  - The contract examples cover the attribute: valid values, and invalid
    values (an unknown value, a region tag such as `es-ES`, upper case).
  - A new `schema_version` is not necessary if the attributes stay one
    JSON column. Write the decision in the contract.
- Acceptance:
  - The contract has the list. Each value is a Windows display language.
  - The contract examples cover the new attribute.
- Tests: —

### E2-T2 The analytics worker and the cockpit

- Status: open
- Plan: 10
- Value: the author sees the distribution of the Windows languages.
- Requirements:
  - The analytics worker accepts `os_language` with the values of the
    contract, and rejects other values. Its test against the contract
    stays.
  - The cockpit shows the number of extended sessions for each
    `os_language` and each week.
- Acceptance:
  - The worker tests accept the valid examples and reject the invalid
    examples of E2-T1.
  - The cockpit shows the languages with the synthetic data of the tests.
- Tests: —

### E2-T3 The app sends `os_language`

- Status: open
- Plan: 10
- Value: the data comes from all users with the extended level.
- Requirements:
  - The app reads the Windows display language at the start
    (`Locale.getDefault(Locale.Category.DISPLAY)`), and sends its primary
    subtag. A value that is not in the list becomes `other`.
  - Only the extended level sends the attribute. The essential level does
    not change.
  - The `analytics` package stays a leaf package. The app start gives it
    the value.
- Acceptance:
  - With the display language `es-AR`, the summary has
    `"os_language": "es"`.
  - With a language that is not in the list, the summary has `"other"`.
  - An essential summary has no `os_language`.
  - The app test against the contract passes.
- Tests: —

### E2-T4 Privacy notice, consent, and notice version

- Status: open
- Plan: 10, "Decisions" ("Notice version")
- Value: the users know about the new attribute before the app sends it,
  and they consent again.
- Requirements:
  - The analytics design, the privacy notice on the site, and the texts of
    the consent dialog and of the Privacy page name the Windows language.
  - The notice version increases from 2 to 3. The app asks for consent
    again.
- Acceptance:
  - A user with the stored notice version 2 sees the consent dialog again.
    The app sends no extended summary before the new consent.
  - The build check of the notice version passes with 3.
- Notes: the notice version is in three places: `AnalyticsSchema`, the
  check in `Build-AppImage.ps1`, and the GitHub variable
  `ANALYTICS_NOTICE_VERSION` (B-9 decision 17). Change all three in the
  same release.
- Tests: —

### E2-T5 Language fields in the feedback report

- Status: open
- Plan: 11 ("Feedback")
- Value: the author knows the language of the user, and can answer in it.
- Requirements:
  - The feedback report has two optional fields: `app_language` (the
    language of the app) and `os_language` (the Windows display language).
    Both use the list of E2-T1. Thus a new app language does not need a
    change of the feedback contract.
  - The feedback worker accepts reports with and without the fields. The
    schema has `additionalProperties: false`, so the worker must accept the
    fields before the app sends them.
  - The message to the author (Telegram) shows the two languages.
  - The feedback privacy notice and the contract documentation name the
    fields.
  - The `feedback` package stays a leaf package. The app start gives it
    the values.
- Acceptance:
  - The worker tests accept a report with and without the new fields, and
    reject a value that is not in the list.
  - The message of the smoke report shows the two languages.
- Tests: —

### E2-T6 Feedback form: the languages of the answer

- Status: open
- Plan: 11 ("Feedback")
- Value: a user who does not write English knows that they can write in
  their language.
- Requirements:
  - The author decides in which languages the author answers. Write the
    list in the plan, "Decisions".
  - The feedback form tells these languages. Example: "You can write in
    English or Spanish."
- Acceptance:
  - The form shows the text. A presenter test checks it.
- Tests: —

### E2-T7 Deployment and release

- Status: open
- Plan: 10 ("Order"), 11
- Value: no summary and no report is lost during the change.
- Requirements:
  - Deploy the analytics worker, the feedback worker, the cockpit, and the
    site with the new privacy notice.
  - Then release the app with E2-T3 to E2-T6.
  - The release checklist has the rule "for a change of a contract, deploy
    the workers and the site first, then release the app".
- Acceptance:
  - The smoke summary and the smoke report with the new fields pass on the
    production workers before the release.
  - The cockpit shows the first `os_language` data after the release.
- Tests: — (manual)

## Done when

- All tasks are `done`.
- The cockpit shows the Windows languages of the users.
- The feedback message shows the app language and the Windows language.
