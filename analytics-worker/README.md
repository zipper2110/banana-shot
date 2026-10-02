# BananaShot analytics Worker

The Worker accepts only version-one, typed analytics envelopes at `POST /v1/events/batch`. Copy `wrangler.toml.example` to the ignored `wrangler.toml` and fill binding IDs locally. Keep `ANALYTICS_INGESTION_ENABLED=false` until the manual deployment checklist and synthetic smoke exercise are complete.

Run `npm ci`, `npm run typecheck`, and `npm test` before a manual deployment. The tests are in `test/`. They use an in-memory D1 (`test/fake-d1.ts`) and the shared fixtures in `analytics-contract/v1`.

The Worker accepts all `app_version` values, also a missing one. It stores a string as it is, another value as its JSON text, and a missing value or `null` as `unknown`. Do not commit credentials, binding IDs, event exports, `.dev.vars`, or the local Wrangler configuration.
