# BananaShot cockpit Worker

The private dashboard of the author (B-41). One page shows what happens with the app: site visits,
downloads, app usage, features, exports, errors, and feedback. The decisions and the tasks are in
`docs/cockpit/b-41-tasks.md`. The ideas for later are in B-43 in `docs/backlog.md`.

## What it reads

| Section | Source | Note |
|---|---|---|
| Downloads | GitHub releases API → own D1 `bananashot-cockpit` | An hourly cron saves the totals. The history starts with the first snapshot. |
| App usage, features, exports, crashes | D1 of the analytics Worker (`ANALYTICS_DB`) | Read only. Totals only. |
| Feedback, errors in reports | D1 of the feedback Worker (`FEEDBACK_DB`) | Read only. No email addresses on the page. |
| Site | Cloudflare GraphQL API, Web Analytics | Needs `CF_API_TOKEN` and `CF_ACCOUNT_ID`. Filtered by `SITE_HOST`. |
| Worker health | Cloudflare GraphQL API, Workers invocations | Needs `CF_API_TOKEN`, `CF_ACCOUNT_ID`. |

Each source is separate. When one source fails or is not configured, its cards show the reason,
and the other cards work.

- `GET /`: the page (`public/`: plain HTML, CSS, and JavaScript, no build step).
- `GET /api/summary?days=7|30|90[&dev=1]`: all numbers as JSON. `dev=1` includes the
  `-SNAPSHOT` versions.
- `POST /api/snapshot`: takes a download snapshot now.
- Each request needs the password (HTTP Basic, any user name). Without the secret
  `COCKPIT_PASSWORD`, the Worker returns `503`.

## Test

```bash
npm ci
npm run typecheck
npm test
```

The tests run the real SQL on `node:sqlite` with the migrations of the three databases
(`test/sqlite-d1.ts`). Node 22.5 or later.

## Local preview

1. Copy `wrangler.toml.example` to `wrangler.toml`. Git ignores `wrangler.toml`. The placeholder
   IDs work for the local preview.
2. Make `.dev.vars` (Git ignores it). With `COCKPIT_DEV_NO_AUTH=true`, the page on `localhost`
   asks for no password. Never set this variable in `wrangler.toml` or as a secret.

   ```
   COCKPIT_PASSWORD=dev
   COCKPIT_DEV_NO_AUTH=true
   ```

3. Fill the local databases with made-up data: `npm run dev:seed`. It uses only `--local`.
4. Start: `npm run dev`. Open `http://localhost:8791/`. The IDE can also start the
   configuration `cockpit` in `.claude/launch.json`.

## Deploy

Do it soon after the first release: the download history starts on the day of the first snapshot.

1. In `wrangler.toml`, write the database IDs of the analytics and the feedback Workers (from
   their `wrangler.toml`). Do not run `migrations apply` for these two databases from here.
2. Make the own database and apply its schema:

   ```bash
   npx wrangler d1 create bananashot-cockpit
   npx wrangler d1 migrations apply bananashot-cockpit --remote
   ```

   Write the new ID in `wrangler.toml` (`binding = "COCKPIT_DB"`).
3. Set the password. Use a long random text, for example from `openssl rand -base64 24`, and
   keep it in your password manager:

   ```bash
   npx wrangler secret put COCKPIT_PASSWORD
   ```

4. Site numbers and Worker health (optional, the rest works without them):
   - In the Cloudflare dashboard, make an API token with the permission
     **Account → Account Analytics → Read** for your account only.
     `npx wrangler secret put CF_API_TOKEN`
   - Write `CF_ACCOUNT_ID` in `wrangler.toml` (Workers & Pages → the account ID on the right).
   - `SITE_HOST` is `banana-shot-editor.app`. The site numbers are filtered by this host.
5. Optional: `npx wrangler secret put GITHUB_TOKEN` (a fine-grained token with no
   permissions is enough for a public repository). Without it, GitHub allows 60 requests in one
   hour for each IP address, and Workers share their addresses.
6. `npx wrangler deploy`. Open the `workers.dev` URL, give the password, and click
   "Take a download snapshot now" once.

Do not commit `wrangler.toml`, `.dev.vars`, the password, or the tokens.
