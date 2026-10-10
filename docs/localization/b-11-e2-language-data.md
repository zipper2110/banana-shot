# B-11 E2: Language Data

This file holds the tasks of epic E2 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 10 (Analytics), 11 ("Feedback"), 14 (Documentation),
  "Decisions" ("Language values", "Language attributes level")
- Depends on: — (it does not depend on E1)
- Size: a few days, plus some weeks for the data.
- Value:
  - The author selects the next languages from real data, not from a
    guess. The cockpit shows the Windows languages and the app languages
    of almost all users, because the essential level sends them. The
    users who select the extended level are few and not typical. A user
    who does not read English well is less likely to select it.
  - A new app language needs no change of a contract or a worker. The
    contracts accept all languages of the list from this epic on.
  - The author knows the language of each feedback report, and can answer
    in this language.
- Prepares: the choice of the languages for E4, E7, and E8. The data needs
  some weeks, so do this epic early. Release it some weeks before E4.

Order of the work:

1. The author approves the updated legitimate interest assessment (E2-T8)
   before the release. The essential level rests on it.
2. The workers and the site accept and describe the new fields before the
   app release that sends them (E2-T7).

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E2-T8 | Legitimate interest assessment for the language attributes | — | open |
| E2-T1 | The language list and the essential attributes in the analytics contract | — | open |
| E2-T2 | The analytics worker and the cockpit | E2-T1 | open |
| E2-T3 | The app sends the two language attributes | E2-T1 | open |
| E2-T4 | Privacy notice, consent dialog, and notice version | E2-T1, E2-T8 | open |
| E2-T5 | Language fields in the feedback report | E2-T1 | open |
| E2-T6 | Feedback form: the languages of the answer | — | open |
| E2-T7 | Deployment and release | E2-T1–E2-T6, E2-T8 | open |

E2-T8 is the first task, but it keeps its number, so that the other task
IDs do not change.

### E2-T8 Legitimate interest assessment for the language attributes

- Status: open
- Plan: 10, "Decisions" ("Language attributes level")
- Value: the essential level stays lawful with the new attributes. The
  essential level is on by default and rests on legitimate interest (GDPR
  Article 6(1)(f)), not on consent.
- Requirements:
  - `docs/analytics/legitimate-interest.md` covers `language` and
    `os_language` before the release:
    - Purpose test: the interest "decide which languages to translate, and
      see if the users use the translations".
    - Necessity test: the extended level alone gives biased data. A user
      who does not read English well is less likely to select an opt-in in
      an English dialog. Thus opt-in data undercounts the users who need a
      translation.
    - Balancing test: each attribute is one code from a fixed list, never a
      region or a full locale. Name the new risk: a rare language with a
      rare app version can make one session easy to see in a small user
      base. It does not identify a person, because the server keeps no IP
      address and no install ID.
    - Safeguards: the list stays short, and rare languages become `other`.
      The cockpit shows a language only when it has at least 5 sessions in
      the period. It counts smaller groups as `other`.
    - Open risk (ePrivacy Article 5(3)): the app now also reads the Windows
      display language from the device. Add this to the open risk of B-9
      decision 26.
  - The "Processing" section lists the two attributes.
- Acceptance:
  - The author reviews and approves the updated assessment. The file has
    the date of the review.
  - If the result of the balancing test is negative, the two attributes
    stay on the extended level. Then change this epic and the plan before
    the other tasks continue.
- Tests: — (decision)

### E2-T1 The language list and the essential attributes in the analytics contract

- Status: open
- Plan: 10, "Decisions" ("Language values", "Language attributes level")
- Value: one fixed list of language values. The analytics data and the
  feedback reports use it, and it cannot hold free text that identifies a
  user. A new app language needs no contract change. The contract allows
  the language attributes on the essential level.
- Requirements:
  - The list has the primary language subtags (ISO 639-1, lower case) of
    the Windows display languages, about 40 values, plus `other`.
  - The list contains `en` and all candidates of E4, E7, and E8.
  - Two attributes use the list:
    - `language`, the language of the app interface. It exists now with
      only `en`. It gets all values of the list.
    - `os_language`, the Windows display language. It is new.
  - The contract tells which attributes the essential level can send: only
    `language` and `os_language` (for example a list `essentialAttributes`,
    as `essentialCounterKeys` for the counters). The extended level sends
    all attributes. `theme`, `accent`, and `sport` stay on the extended
    level only.
  - The contract examples cover the two attributes: valid values, and
    invalid values (an unknown value, a region tag such as `es-ES`, upper
    case).
  - The examples cover the levels: an essential summary with the two
    language attributes is valid. An essential summary with `theme` is
    invalid.
  - The smoke summary (essential) has the two language attributes. Thus
    the deployment check covers them.
  - A new `schema_version` is not necessary if the attributes stay one
    JSON column. Write the decision in the contract.
- Acceptance:
  - The contract has the list. Each value is a Windows display language.
  - The contract examples cover the two attributes. A summary with
    `"language": "es"` is valid.
  - The current example "attributes in an essential summary" changes to an
    attribute that is not essential, and stays invalid.
- Tests: —

### E2-T2 The analytics worker and the cockpit

- Status: open
- Plan: 10
- Value: the author sees the distribution of the Windows languages and of
  the app languages.
- Requirements:
  - The analytics worker accepts `language` and `os_language` with the
    values of the contract on the essential and the extended level. It
    rejects other values, and it rejects other attributes on the essential
    level. Its test against the contract stays.
  - The cockpit shows the number of sessions (essential and extended) for
    each `os_language` and each week, and the same for `language`.
  - The cockpit shows a language only when it has at least 5 sessions in
    the period. It counts smaller groups as `other` (E2-T8).
  - The cockpit can show how many users of a Windows language use the app
    in English. This shows the users that a new translation can help.
- Acceptance:
  - The worker tests accept the valid examples and reject the invalid
    examples of E2-T1.
  - The cockpit shows the languages with the synthetic data of the tests.
    A language with fewer than 5 sessions shows as `other`.
- Tests: —

### E2-T3 The app sends the two language attributes

- Status: open
- Plan: 10
- Value: the data comes from all users who did not turn off the
  statistics.
- Requirements:
  - The app reads the Windows display language at the start
    (`Locale.getDefault(Locale.Category.DISPLAY)`), and sends its primary
    subtag as `os_language`. A value that is not in the list becomes
    `other`.
  - The app sends the app language as `language`, from the same list. In
    E2, the value is always `en`.
  - The essential level and the extended level send the two attributes.
    The other attributes stay on the extended level only.
  - A change from extended to essential deletes the extended counters and
    the extended attributes. It keeps the two language attributes.
  - With the statistics off, the app sends nothing, as now.
  - The `analytics` package stays a leaf package. The app start gives it
    the value.
- Acceptance:
  - With the display language `es-AR`, the summary has
    `"os_language": "es"`.
  - With a language that is not in the list, the summary has `"other"`.
  - An essential summary and an extended summary have `"language": "en"`
    and `os_language`.
  - An essential summary has no `theme`, `accent`, or `sport`.
  - After a change from extended to essential, the next summary has the two
    language attributes and no other attributes.
  - The app test against the contract passes.
- Tests: —

### E2-T4 Privacy notice, consent dialog, and notice version

- Status: open
- Plan: 10, "Decisions" ("Notice version", "Language attributes level")
- Value: the users know about the new data and its level before the app
  sends it. They can turn off the statistics.
- Requirements:
  - The privacy notice on the site, the first-start dialog, and the Privacy
    page list the app language and the Windows language under the
    essential level, with the legal basis "legitimate interest". They
    still tell where to turn off all statistics.
  - The analytics design changes in "Levels" and "Attributes": the
    essential level sends the two language attributes. The other
    attributes stay on the extended level.
  - The analytics design says now that the app does not send the locale of
    the computer. The new text tells that the app sends only the language
    part of the Windows display language, from the fixed list, never the
    region.
  - The notice version increases from 2 to 3. The app shows the dialog
    again.
- Acceptance:
  - A user with the stored notice version 2 sees the first-start dialog
    again. Until the user answers, the app sends the essential level (with
    the language attributes) and no extended summary, as for each changed
    notice version.
  - A user who turned off the statistics sees no change and sends nothing.
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
  - The author approved the assessment of E2-T8.
  - Deploy the analytics worker, the feedback worker, the cockpit, and the
    site with the new privacy notice.
  - Then release the app with E2-T3 to E2-T6.
  - The release checklist has the rule "for a change of a contract, deploy
    the workers and the site first, then release the app".
- Acceptance:
  - The smoke summary (essential, with the two language attributes) and the
    smoke report with the new fields pass on the production workers before
    the release.
  - The cockpit shows the first `os_language` data after the release.
- Tests: — (manual)

## Done when

- All tasks are `done`.
- The cockpit shows the Windows languages and the app languages of all
  users with the essential or the extended level.
- The legitimate interest assessment covers the two language attributes.
- The analytics and the feedback contracts accept all languages of the
  list. A new app language needs no deployment.
- The feedback message shows the app language and the Windows language.
