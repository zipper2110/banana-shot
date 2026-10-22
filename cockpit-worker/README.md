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
| Site | D1 of the analytics Worker (`ANALYTICS_DB`), tables `site_daily` and `site_bot_daily` | The own page view counter and the bot counter of the site (`site/README.md`). Exact daily totals. Cloudflare Web Analytics samples its data, so the cockpit does not use it. |
| Worker health | Cloudflare GraphQL API, Workers invocations | Needs `CF_API_TOKEN`, `CF_ACCOUNT_ID`. |

Each source is separate. When one source fails or is not configured, its cards show the reason,
and the other cards work.

- `GET /`: the page (`public/`: plain HTML, CSS, and JavaScript, no build step).
- `GET /api/summary?days=1|7|30|90[&end=YYYY-MM-DD][&dev=1]`: all numbers as JSON. The period ends on the day `end` (UTC, included); without `end`, or with a day after today, it ends today. `days=1` is only that day. `dev=1` includes the
  `-SNAPSHOT` versions.
- `POST /api/snapshot`: takes a download snapshot now.
- The hourly cron takes the download snapshot. Then it sends the Telegram digest (see "Telegram digest").
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

1. Make `.dev.vars` (Git ignores it). With `COCKPIT_DEV_NO_AUTH=true`, the page on `localhost`
   asks for no password. Never set this variable in `wrangler.toml` or as a secret.

   ```
   COCKPIT_PASSWORD=dev
   COCKPIT_DEV_NO_AUTH=true
   ```

2. Fill the local databases with made-up data: `npm run dev:seed`. It uses only `--local`.
3. Start: `npm run dev`. Open `http://localhost:8791/`. The IDE can also start the
   configuration `cockpit` in `.claude/launch.json`.

## Deploy

GitHub Actions can do the deploy: run the workflow "Cloudflare deploy" from `master` and select "Cockpit Worker". The workflow runs `npm ci`, the type check, and the tests. Then it applies the migrations of `bananashot-cockpit` only and deploys the Worker.
Setup of the token: see `.github/workflows/cloudflare-deploy.yml`. The steps below are still necessary for a new database and for the secrets.

Do it soon after the first release: the download history starts on the day of the first snapshot.

`wrangler.toml` is in Git with the account ID and the IDs of the deployed databases. They are not
secrets. For a deployed cockpit, only `npx wrangler deploy` is necessary. The steps below are for
a new setup.

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

4. Worker health (optional, the rest works without it):
   - In the Cloudflare dashboard, make an API token with the permission
     **Account → Account Analytics → Read** for your account only.
     `npx wrangler secret put CF_API_TOKEN`
   - Write `CF_ACCOUNT_ID` in `wrangler.toml` (Workers & Pages → the account ID on the right).
5. Optional: `npx wrangler secret put GITHUB_TOKEN` (a fine-grained token with no
   permissions is enough for a public repository). Without it, GitHub allows 60 requests in one
   hour for each IP address, and Workers share their addresses.
6. Optional: the Telegram digest. Use the bot and the chat of the feedback Worker
   (`feedback-worker/README.md`, "Make the Telegram bot"):

   ```bash
   npx wrangler secret put TELEGRAM_BOT_TOKEN
   npx wrangler secret put TELEGRAM_CHAT_ID
   ```

7. `npx wrangler deploy`. Open the `workers.dev` URL, give the password, and click
   "Take a download snapshot now" once.

## Telegram digest

Each hour, after the download snapshot, the cron sends one Telegram message with the new items
since the last run (`src/notify.ts`). Without new items, it sends no message.

- Visits: the new visits of the site (`site_daily.visits`), by page, referrer host, country, and
  device. A visit is a page view that came from another site or from a direct open.
- Downloads: the new downloads of `BananaShot-win-Setup.exe` (installer) and of the `.nupkg` files
  (update packages), by release. The numbers come from the GitHub totals of the snapshot. The
  other release files, for example `releases.win.json`, do not count.
- New installs: the new first sessions of the app (`session_n_1`), by version and OS. There is no
  install ID, so this is an estimate (`analytics-worker/queries/usage.sql`). Only an install that
  started the app with analytics on counts. `synthetic-smoke` and `-SNAPSHOT` versions do not count.

The sources keep only totals. The table `notify_seen` keeps the totals of the last run, and the
next run sends the increase. The first run only keeps the totals and sends no message. When
Telegram refuses the message, the totals do not change, and the next run sends the items again.
The result of the last run is in the table `job_status` (`telegram_digest`).

Without the secrets `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`, the cron sends no digest. To stop
the digest, delete one secret: `npx wrangler secret delete TELEGRAM_CHAT_ID`.

Do not commit `.dev.vars`, the password, or the tokens.
