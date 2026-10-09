#!/usr/bin/env bash
# Deploys one Cloudflare target from the repository root.
# Usage: cloudflare-deploy.sh site|analytics-worker|feedback-worker|cockpit-worker
# Wrangler reads CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID from the environment.
set -euo pipefail

target="${1:?Give the target: site, analytics-worker, feedback-worker, or cockpit-worker}"

# Runs the checks of a Worker, applies its D1 migrations, and deploys it.
# The migrations come first, because the new code can use the new columns.
deploy_worker() {
  local dir="$1" database="$2"
  cd "$dir"
  npm ci
  npm run typecheck
  npm test
  npx wrangler d1 migrations apply "$database" --remote
  npx wrangler deploy
}

# Sends the synthetic smoke summary to the deployed analytics Worker and checks the status.
# The Worker writes all columns for each summary, so a missing migration also gives an error here.
# With ingestion on, the Worker must return 204. With ingestion off, it must return 410.
smoke_check_analytics() {
  local endpoint="${ANALYTICS_ENDPOINT:-}"
  if [ -z "$endpoint" ]; then
    echo "::error::The Worker is deployed, but the smoke check cannot run. Set the GitHub variable ANALYTICS_ENDPOINT."
    exit 1
  fi
  local expected=410
  if grep -Eq '^ANALYTICS_INGESTION_ENABLED *= *"true"' analytics-worker/wrangler.toml; then expected=204; fi
  # A new Worker version can need some seconds to reach all locations. Try a few times.
  local attempt status=""
  for attempt in 1 2 3; do
    status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 -X POST "$endpoint" \
      -H 'content-type: application/json' --data @analytics-contract/v1/smoke-summary.json || true)
    if [ "$status" = "$expected" ]; then
      echo "Smoke check: $status, as expected."
      return 0
    fi
    echo "Smoke check attempt $attempt: got $status, expected $expected."
    sleep 10
  done
  echo "::error::The deployed analytics Worker returned $status for the smoke summary. Expected $expected."
  exit 1
}

case "$target" in
  site)
    # The site has no package.json. It uses the wrangler of analytics-worker (site/README.md).
    (cd analytics-worker && npm ci)
    wrangler=analytics-worker/node_modules/.bin/wrangler
    "$wrangler" deploy --config site/wrangler.toml
    "$wrangler" deploy --config site/www-redirect/wrangler.toml
    ;;
  analytics-worker)
    # deploy_worker changes the directory, so run it in a subshell.
    (deploy_worker analytics-worker bananashot-analytics)
    smoke_check_analytics
    ;;
  feedback-worker) deploy_worker feedback-worker bananashot-feedback ;;
  # The cockpit applies only the migrations of its own database. It reads the other two databases.
  cockpit-worker) deploy_worker cockpit-worker bananashot-cockpit ;;
  *)
    echo "Unknown target: $target" >&2
    exit 1
    ;;
esac
