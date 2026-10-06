/**
 * A D1 on node:sqlite for the tests (decision 14). The queries run on real SQLite, as in D1.
 */
import { readdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';

const require = createRequire(import.meta.url);
const { DatabaseSync } = require('node:sqlite') as typeof import('node:sqlite');

type Database = InstanceType<typeof DatabaseSync>;

class Statement {
  private args: unknown[] = [];

  constructor(private readonly db: Database, readonly sql: string) {}

  bind(...args: unknown[]): Statement {
    this.args = args;
    return this;
  }

  private params() {
    return this.args.map(arg => typeof arg === 'boolean' ? Number(arg) : arg) as (string | number | null)[];
  }

  async all<T>(): Promise<{ results: T[] }> {
    return { results: this.db.prepare(this.sql).all(...this.params()).map(row => ({ ...row })) as T[] };
  }

  async first<T>(): Promise<T | null> {
    const row = this.db.prepare(this.sql).get(...this.params());
    return row ? { ...row } as T : null;
  }

  async run(): Promise<{ meta: { changes: number } }> {
    const result = this.db.prepare(this.sql).run(...this.params());
    return { meta: { changes: Number(result.changes) } };
  }
}

export class SqliteD1 {
  readonly db: Database = new DatabaseSync(':memory:');

  constructor(...migrationDirs: string[]) {
    for (const dir of migrationDirs) {
      for (const file of readdirSync(dir).filter(name => name.endsWith('.sql')).sort()) {
        this.db.exec(readFileSync(join(dir, file), 'utf8'));
      }
    }
  }

  prepare(sql: string): Statement {
    return new Statement(this.db, sql);
  }

  async batch(statements: Statement[]) {
    this.db.exec('BEGIN');
    try {
      for (const statement of statements) await statement.run();
      this.db.exec('COMMIT');
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }

  asD1(): D1Database {
    return this as unknown as D1Database;
  }
}

const root = new URL('../..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
export const MIGRATIONS = {
  analytics: join(root, 'analytics-worker', 'migrations'),
  feedback: join(root, 'feedback-worker', 'migrations'),
  cockpit: join(root, 'cockpit-worker', 'migrations'),
};
