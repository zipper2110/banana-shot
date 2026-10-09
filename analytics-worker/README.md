# BananaShot analytics Worker

The Worker receives the anonymous session summaries of the app (B-9) and keeps one row for each
session in D1. The design is in `docs/analytics/design.md`. The tasks are in
`docs/analytics/b-9-tasks.md`.

- `POST /v1/session` with `Content-Type: application/json` and a body of 8 KiB or less. Other
  paths return `404`. Other methods return `405`.
- The responses have no body: `204` (stored), `400` (invalid summary), `410` (the kill switch is
  off, or the schema version is too old), `413`, `415`, `429` (rate limit), `503` (D1 failure).
- The summary format and the closed counter key lists are in `analytics-contract/v1`. The Worker
  refuses a summary with an unknown counter key, and an essential summary with a key that is not
  essential. A summary of schema version 1 (no `level`) gets `410`. Thus, deploy the Worker with the new keys before
  an app release that sends them.
- The Worker keeps the summary with the highest `snapshot` for each session. It rounds the
  receive times down to the hour.
- The Worker does not keep the IP address. The rate limit (300 summaries from one address in one
  hour) keeps an HMAC of the hour and the address, with the secret `RATE_LIMIT_KEY`.
- The daily cron deletes sessions with a last summary older than 90 days.
- The kill switch `ANALYTICS_INGESTION_ENABLED` must be `true`. Another value gives `410`, and the
  app stops sending for the rest of the process.

## Test

```bash
npm ci
npm run typecheck
npm test
```

The tests use an in-memory D1 (`test/fake-d1.ts`) and the shared fixtures in
`analytics-contract/v1`.

## Deploy

GitHub Actions can do the deploy: run the workflow "Deploy" from `master` and select "Analytics Worker". The workflow runs `npm ci`, the type check, and the tests. Then it applies the D1 migrations and deploys the Worker.
Setup of the token: see `.github/workflows/cloudflare-deploy.yml`. The steps below are still necessary for a new database and for the secrets.

`wrangler.toml` is in Git with the IDs of the deployed database. They are not secrets. The
first two steps are only for a new database.

1. Make the database in the EU jurisdiction:
   `npx wrangler d1 create bananashot-analytics --jurisdiction eu`.
2. Write the database ID in `wrangler.toml`. Keep `binding = "ANALYTICS_DB"`. If `d1 create` offers to add a binding with
   a different name, do not accept it.
3. Apply the schema: `npx wrangler d1 migrations apply bananashot-analytics --remote`. Do this
   also for a database that exists: migration `0002` adds the column `level`. Apply it before
   you deploy the Worker, because the new Worker writes this column.
4. Set the secret `RATE_LIMIT_KEY`: a new random text of 32 or more characters, for example from
   `openssl rand -hex 32`. Do not use the key of the feedback Worker.

   ```bash
   npx wrangler secret put RATE_LIMIT_KEY
   ```

5. Deploy with `ANALYTICS_INGESTION_ENABLED = "false"`: `npx wrangler deploy`.
6. Check that `POST /v1/session` returns `410`.
7. Set `ANALYTICS_INGESTION_ENABLED = "true"` in `wrangler.toml` and deploy again.
8. Send the synthetic summary. The Worker must return `204`:

   ```bash
   curl.exe -i -X POST https://<worker-host>/v1/session -H "content-type: application/json" --data "@../analytics-contract/v1/smoke-summary.json"
   ```

   In Windows PowerShell, `curl` is a short name for `Invoke-WebRequest`. Type `curl.exe`.

9. Check the row:
   `npx wrangler d1 execute bananashot-analytics --remote --command "SELECT * FROM analytics_session WHERE app_version = 'synthetic-smoke'"`.
10. Give the endpoint URL to the release build: the GitHub variable `ANALYTICS_ENDPOINT`
    (see `docs/release-checklist.md`).

To stop the summaries at once, set `ANALYTICS_INGESTION_ENABLED = "false"` and deploy.

## Queries

The SQL files in `queries/` answer the questions of the design. There is no dashboard. Each query
excludes `app_version = 'synthetic-smoke'`. `usage.sql` uses the last 12 weeks. The other queries
use the last 30 days.

```bash
npm run query -- queries/features.sql
```

Do not use `wrangler d1 execute --remote --file` for the queries. With `--file`, Wrangler uses the
import path and prints only statistics, not the result rows. `scripts/query.mjs` sends the text of
the file with `--command`.

- `usage.sql`: for each week, sessions, first sessions, returning sessions, active hours, and the
  percent of extended sessions. All sessions count. This answers "Does anyone use the app?".
- `features.sql`: tabs and features. Only the extended sessions.
- `exports.sql`: exports by encoder, with the encode speed. Only the extended sessions.
- `export-details.sql`: failure reasons, export options, and output resolutions. Only the extended
  sessions.
- `sessions.sql`: session length, app versions, OS families, crashes, and the session buckets. All
  sessions count.

Do not commit the rate key, data exports, or `.dev.vars`.
