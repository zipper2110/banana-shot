# Analytics contract v1

These synthetic fixtures define the session summary (schema version 3, and the older version 2)
that the app sends to `POST /v1/session`. The directory name is the version of the path, not of the
schema. The design is in `docs/analytics/design.md` ("Wire format", "Counters", and "Attributes").
The Kotlin tests and the Worker tests both read these files. The files contain no production
identifiers, paths, media data, or user-entered values.

- `counter-keys.json`: the closed list of counter keys (`counterKeys`), and the keys that an
  essential summary can contain (`essentialCounterKeys`). The app and the Worker each have their
  own lists. A test on each side checks that its lists are equal to this file. To add a key, change
  this file, both lists, and the privacy notice. Deploy the Worker before the app release.
- `attributes.json`: the closed list of the session attributes and their values (`attributes`),
  and the attributes that have an array value (`arrayAttributes`). Only schema 3 summaries at the
  extended level can have attributes. The app and the Worker each have their own lists, and a test
  on each side checks them against this file. To add an attribute or a value, change this file,
  both lists, the cockpit names, and the privacy notice. Deploy the Worker before the app release.
- `valid-summaries.json`: summaries that the Worker accepts (`204`). One extended summary contains
  all counter keys. One essential summary contains all essential keys. One schema 3 summary
  contains all attributes, with all values of each array attribute. Other summaries show the unusual `app_version` values, because all values of
  `app_version` are accepted, also a missing one.
- `invalid-summaries.json`: summaries that the Worker refuses with `400`. Each item has a `name`
  and a `payload` (a JSON value) or a `raw` body (text that is not valid JSON).
- `smoke-summary.json`: the synthetic essential summary for the deployment check. Its
  `app_version` is `synthetic-smoke`. The queries exclude this value.
