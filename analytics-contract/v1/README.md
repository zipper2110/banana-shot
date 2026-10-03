# Analytics contract v1

These synthetic fixtures define the version-one session summary that the app sends to
`POST /v1/session`. The design is in `docs/analytics/design.md` ("Wire format" and "Counters").
The Kotlin tests and the Worker tests both read these files. The files contain no production
identifiers, paths, media data, or user-entered values.

- `counter-keys.json`: the closed list of counter keys. The app and the Worker each have their
  own list. A test on each side checks that its list is equal to this file. To add a key, change
  this file, both lists, and the privacy notice. Deploy the Worker before the app release.
- `valid-summaries.json`: summaries that the Worker accepts (`204`). One summary contains all
  counter keys. Other summaries show the unusual `app_version` values, because all values of
  `app_version` are accepted, also a missing one.
- `invalid-summaries.json`: summaries that the Worker refuses with `400`. Each item has a `name`
  and a `payload` (a JSON value) or a `raw` body (text that is not valid JSON).
- `smoke-summary.json`: the synthetic summary for the deployment check. Its `app_version` is
  `synthetic-smoke`. The queries exclude this value.
