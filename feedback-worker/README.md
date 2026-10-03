# BananaShot feedback Worker

The Worker receives the feedback reports of the app (B-8, `docs/feedback/b-8-tasks.md`)
at `POST /v1/feedback`. It keeps the report text in D1 and sends each new report to the
author with a Telegram bot. The contract is in `feedback-contract/v1`.

- The Worker does not keep the log. It sends the log to Telegram as a `.log.gz` file and
  then drops it.
- The Worker does not keep the IP address. The rate limit (30 reports from one address in
  one hour) keeps an HMAC of the hour and the address, and deletes it after the hour.
- The daily cron job deletes the report rows that are older than 90 days. The copy in the
  Telegram chat is the responsibility of the author.
- When `FEEDBACK_INGESTION_ENABLED` is not `true`, the Worker returns `410`.

## Tests

Run these commands before each deployment:

```bash
npm ci
npm run typecheck
npm test
```

The tests use an in-memory D1 (`test/fake-d1.ts`), a fake Telegram (`test/fake-telegram.ts`),
and the shared fixtures in `feedback-contract/v1`.

## Make the Telegram bot

1. In Telegram, open `@BotFather`. Send `/newbot`. Give a name and a user name.
2. Keep the token that `@BotFather` gives. This is `TELEGRAM_BOT_TOKEN`.
3. Send one message to the new bot from the account of the author.
4. Open `https://api.telegram.org/bot<token>/getUpdates` in a browser. Find `"chat":{"id":…}`.
   This number is `TELEGRAM_CHAT_ID`.
5. Send `/setjoingroups` to `@BotFather` and select `Disable`. Then nobody can add the bot
   to a group.

## Deploy

1. Copy `wrangler.toml.example` to `wrangler.toml`. Git ignores `wrangler.toml`.
2. Make the database: `npx wrangler d1 create bananashot-feedback`. Write the database ID
   in `wrangler.toml`. Keep `binding = "FEEDBACK_DB"`. If `d1 create` offers to add a
   binding with a different name, do not accept it.
3. Apply the schema: `npx wrangler d1 migrations apply bananashot-feedback --remote`.
4. Set the secrets. `RATE_LIMIT_KEY` is a random text of 32 or more characters, for
   example from `openssl rand -hex 32`.

   ```bash
   npx wrangler secret put TELEGRAM_BOT_TOKEN
   npx wrangler secret put TELEGRAM_CHAT_ID
   npx wrangler secret put RATE_LIMIT_KEY
   ```

5. Deploy with `FEEDBACK_INGESTION_ENABLED = "false"`: `npx wrangler deploy`.
6. Check that `POST /v1/feedback` returns `410`.
7. Set `FEEDBACK_INGESTION_ENABLED = "true"` in `wrangler.toml` and deploy again.
8. Send a synthetic report. The bot must send a message to the author chat:

   ```bash
   curl.exe -i -X POST https://<worker-host>/v1/feedback -H "content-type: application/json" --data "@../feedback-contract/v1/smoke-report.json"
   ```

   In Windows PowerShell, `curl` is a short name for `Invoke-WebRequest`. Type `curl.exe`.

   A second send of the same file must return `200`.

9. Give the endpoint URL to the release build: the GitHub variable `FEEDBACK_ENDPOINT`
   (see `docs/release-checklist.md`).

To stop the reports at once, set `FEEDBACK_INGESTION_ENABLED = "false"` and deploy.

Do not commit the token, the chat ID, the rate key, the database ID, report exports,
`.dev.vars`, or `wrangler.toml`.
