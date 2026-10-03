# Feedback contract v1

These synthetic fixtures define the report that the app sends to `POST /v1/feedback`
(B-8, `docs/feedback/b-8-tasks.md`). The Kotlin tests and the Worker tests use the
same files.

- `report.schema.json` is the JSON schema of a report.
- `valid-reports.json` contains reports that the Worker accepts.
- `invalid-reports.json` contains reports that the Worker refuses with `422`. An item
  has a `payload` (a JSON value) or a `raw` body (a text that is not valid JSON).

## Rules that the schema cannot show

- `message` must contain one or more characters that are not white space.
- `log` is the Base64 text of gzip data. The decoded data must start with the gzip
  bytes `1F 8B`.
- The length of a text is the number of UTF-16 code units (as `String.length` in
  Kotlin and in JavaScript).

## Responses

| Status | Body | Meaning |
|---|---|---|
| `201` | `{"report_id": "…"}` | The Worker kept the report and sent it to the author. |
| `200` | `{"report_id": "…"}` | The Worker sent this `report_id` before. It changed nothing. |
| `404`, `405` | none | Wrong path or method. |
| `410` | `{"error": "disabled"}` | The kill switch is off. |
| `413` | `{"error": "too_large"}` | The body is larger than 3 MB. |
| `415` | `{"error": "not_json"}` | The content type is not `application/json`. |
| `422` | `{"error": "invalid"}` | The report is not valid. |
| `429` | `{"error": "rate_limited"}` | Too many reports from one IP address in this hour. |
| `503` | `{"error": "unavailable"}` | The storage or Telegram failed. The app tries again with the same `report_id`. |

An error body never contains a value from the request.

The fixtures contain no real addresses, paths, or logs.
