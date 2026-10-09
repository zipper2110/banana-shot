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

## The contact form of the website

The site sends to `POST /v1/site-feedback`, not to `/v1/feedback`.

- The body has only `report_id`, `topic`, `message`, the optional `email`, and the
  optional `trap`. The rules of these fields are the same as above. Other keys give `422`.
- `trap` is a hidden field of the form. People leave it empty. When it has a value, the
  Worker returns `201` and drops the report.
- Only the origin `https://banana-shot-editor.app` can send. Another origin, or no
  `Origin` header, gives `403` with `{"error": "origin"}`.
- Each answer has the CORS headers. `OPTIONS` returns `204`, also when the kill switch is off.
- The Worker keeps the report with `app_version` = `website` and empty OS and Java fields.
  The site reports and the app reports have the same rate limit.

The fixtures contain no real addresses, paths, or logs.
