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
    guess. The cockpit shows the Windows languages and the app languages
    of the users.
  - A new app language needs no change of a contract or a worker. The
    contracts accept all languages of the list from this epic on.
  - The author knows the language of each feedback report, and can answer
    in this language.
- Prepares: the choice of the languages for E4, E7, and E8. The data needs
  some weeks, so do this epic early. Release it some weeks before E4.

Order of the deployment: the workers and the site accept and describe the
new fields before the app release that sends them (E2-T7).

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E2-T1 | The language list in the analytics contract | — | open |
| E2-T2 | The analytics worker and the cockpit | E2-T1 | open |
| E2-T3 | The app sends the two language attributes | E2-T1 | open |
| E2-T4 | Privacy notice, consent, and notice version | E2-T1 | open |
| E2-T5 | Language fields in the feedback report | E2-T1 | open |
| E2-T6 | Feedback form: the languages of the answer | — | open |
| E2-T7 | Deployment and release | E2-T1–E2-T6 | open |

### E2-T1 The language list in the analytics contract

- Status: open
- Plan: 10, "Decisions" ("Language values")
- Value: one fixed list of language values. The analytics data and the
  feedback reports use it, and it cannot hold free text that identifies a
  user. A new app language needs no contract change.
- Requirements:
  - The list has the primary language subtags (ISO 639-1, lower case) of
    the Windows display languages, about 40 values, plus `other`.
  - The list contains `en` and all candidates of E4, E7, and E8.
  - Two attributes of the extended level use the list:
    - `language`, the language of the app interface. It exists now with
      only `en`. It gets all values of the list.
    - `os_language`, the Windows display language. It is new.
  - The contract examples cover the two attributes: valid values, and
    invalid values (an unknown value, a region tag such as `es-ES`, upper
    case).
  - A new `schema_version` is not necessary if the attributes stay one
    JSON column. Write the decision in the contract.
- Acceptance:
  - The contract has the list. Each value is a Windows display language.
  - The contract examples cover the two attributes. A summary with
    `"language": "es"` is valid.
- Tests: —

### E2-T2 The analytics worker and the cockpit

- Status: open
- Plan: 10
- Value: the author sees the distribution of the Windows languages and of
  the app languages.
- Requirements:
  - The analytics worker accepts `language` and `os_language` with the
    values of the contract, and rejects other values. Its test against the
    contract stays.
  - The cockpit shows the number of extended sessions for each
    `os_language` and each week, and the same for `language`.
  - The cockpit can show how many users of a Windows language use the app
    in English. This shows the users that a new translation can help.
- Acceptance:
  - The worker tests accept the valid examples and reject the invalid
    examples of E2-T1.
  - The cockpit shows the languages with the synthetic data of the tests.
- Tests: —

### E2-T3 The app sends the two language attributes

- Status: open
- Plan: 10
- Value: the data comes from all users with the extended level.
- Requirements:
  - The app reads the Windows display language at the start
    (`Locale.getDefault(Locale.Category.DISPLAY)`), and sends its primary
    subtag as `os_language`. A value that is not in the list becomes
    `other`.
  - The app sends the app language as `language`, from the same list. In
    E2, the value is always `en`.
  - Only the extended level sends the attributes. The essential level does
    not change.
  - The `analytics` package stays a leaf package. The app start gives it
    the value.
- Acceptance:
  - With the display language `es-AR`, the summary has
    `"os_language": "es"`.
  - With a language that is not in the list, the summary has `"other"`.
  - An extended summary has `"language": "en"`.
  - An essential summary has no `os_language` and no `language`.
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
  - The analytics design says now that the app does not send the locale of
    the computer. The new text tells that the app sends only the language
    part of the Windows display language, from the fixed list, never the
    region.
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
- The cockpit shows the Windows languages and the app languages of the
  users.
- The analytics and the feedback contracts accept all languages of the
  list. A new app language needs no deployment.
- The feedback message shows the app language and the Windows language.
