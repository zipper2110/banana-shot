# Analytics contract v1

These synthetic fixtures define the session summary (schema version 4, and the older versions 3 and 2)
that the app sends to `POST /v1/session`. The directory name is the version of the path, not of the
schema. The design is in `docs/analytics/design.md` ("Wire format", "Counters", and "Attributes").
The Kotlin tests and the Worker tests both read these files. The files contain no production
identifiers, paths, media data, or user-entered values.

- `counter-keys.json`: the closed list of counter keys (`counterKeys`), and the keys that an
  essential summary can contain (`essentialCounterKeys`). The app and the Worker each have their
  own lists. A test on each side checks that its lists are equal to this file. To add a key, change
  this file, both lists, and the privacy notice. Deploy the Worker before the app release.
- `attributes.json`: the closed list of the session attributes and their values (`attributes`),
  the attributes that have an array value (`arrayAttributes`), and the attributes that an essential
  summary can contain (`essentialAttributes`). The app and the Worker each have their own lists,
  and a test on each side checks them against this file. To add an attribute or a value, change
  this file, both lists, the cockpit names, and the privacy notice. Deploy the Worker before the app
  release.
  - Schema 2 has no attributes.
  - Schema 3 (notice version 2): only an extended summary has attributes, and not `os_language`.
  - Schema 4 (notice version 3): an essential summary can have the essential attributes. An
    extended summary can have all attributes.
- The language values (B-11 E2): `language` (the app language) and `os_language` (the Windows
  display language) use one fixed list. It has the primary language subtags (ISO 639-1, lower case)
  of the Windows display languages, and `other`. Never a region or a full locale. A new app
  language needs no change of the contract, because the list has all candidates. The feedback
  contract uses the same list for `app_language` and `os_language`.
- Schema 4 is necessary because the Worker keeps one notice version for each schema version. The
  attributes stay in one JSON column, so the database does not change.
- `valid-summaries.json`: summaries that the Worker accepts (`204`). One extended summary contains
  all counter keys. One essential summary contains all essential keys. One schema 4 summary
  contains all attributes, with all values of each array attribute. Other summaries show the unusual `app_version` values, because all values of
  `app_version` are accepted, also a missing one.
- `invalid-summaries.json`: summaries that the Worker refuses with `400`. Each item has a `name`
  and a `payload` (a JSON value) or a `raw` body (text that is not valid JSON).
- `smoke-summary.json`: the synthetic essential summary for the deployment check. It has the two
  language attributes, so the check covers them. Its
  `app_version` is `synthetic-smoke`. The queries exclude this value.
