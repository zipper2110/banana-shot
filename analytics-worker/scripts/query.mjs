// Runs one SQL file of queries/ on the remote D1 and prints the result rows.
// `wrangler d1 execute --remote --file` uses the import path and prints only statistics, so this script sends the
// text of the file with --command. Wrangler starts without a shell, so the SQL needs no quoting.
// Usage (in analytics-worker): npm run query -- queries/usage.sql
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const file = process.argv[2];
if (!file) {
  console.error('Usage: npm run query -- queries/<name>.sql');
  process.exit(2);
}
const sql = readFileSync(file, 'utf8')
  .split(/\r?\n/)
  .filter(line => !line.trimStart().startsWith('--'))
  .join(' ')
  .trim();
const wrangler = fileURLToPath(new URL('../node_modules/wrangler/bin/wrangler.js', import.meta.url));
const result = spawnSync(
  process.execPath,
  [wrangler, 'd1', 'execute', 'bananashot-analytics', '--remote', '--command', sql],
  { stdio: 'inherit' },
);
process.exit(result.status ?? 1);
