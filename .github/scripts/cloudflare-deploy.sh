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

case "$target" in
  site)
    # The site has no package.json. It uses the wrangler of analytics-worker (site/README.md).
    (cd analytics-worker && npm ci)
    wrangler=analytics-worker/node_modules/.bin/wrangler
    "$wrangler" deploy --config site/wrangler.toml
    "$wrangler" deploy --config site/www-redirect/wrangler.toml
    ;;
  analytics-worker) deploy_worker analytics-worker bananashot-analytics ;;
  feedback-worker) deploy_worker feedback-worker bananashot-feedback ;;
  # The cockpit applies only the migrations of its own database. It reads the other two databases.
  cockpit-worker) deploy_worker cockpit-worker bananashot-cockpit ;;
  *)
    echo "Unknown target: $target" >&2
    exit 1
    ;;
esac
