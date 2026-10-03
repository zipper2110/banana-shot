/**
 * In-memory D1 for the Worker tests. It knows only the statements that the Worker uses.
 * An unknown statement throws, so a changed query cannot pass the tests by accident.
 */
export type Row = Record<string, unknown>;

export class FakeD1 {
  readonly sessions = new Map<string, Row>();
  readonly rates = new Map<string, Row>();
  retentionStatus: Row | null = null;
  fail = false;

  prepare(sql: string): FakeStatement {
    return new FakeStatement(this, sql);
  }

  asD1(): D1Database {
    return this as unknown as D1Database;
  }

  sessionRows(): Row[] {
    return [...this.sessions.values()];
  }
}

export class FakeStatement {
  private args: unknown[] = [];

  constructor(private readonly db: FakeD1, readonly sql: string) {}

  bind(...args: unknown[]): FakeStatement {
    this.args = args;
    return this;
  }

  async run(): Promise<{ meta: { changes: number } }> {
    this.check();
    return { meta: { changes: this.execute() } };
  }

  async first<T>(): Promise<T | null> {
    this.check();
    if (this.sql.startsWith('INSERT INTO analytics_rate ') && this.sql.endsWith('RETURNING count')) {
      const [id, hour] = this.args as [string, number];
      const row = this.db.rates.get(id) ?? { id, hour, count: 0 };
      row.count = (row.count as number) + 1;
      this.db.rates.set(id, row);
      return { count: row.count } as T;
    }
    if (this.sql === 'SELECT MIN(last_received_at) AS oldest FROM analytics_session') {
      const times = this.db.sessionRows().map(row => row.last_received_at as number);
      return { oldest: times.length ? Math.min(...times) : null } as T;
    }
    throw new Error(`Unknown query: ${this.sql}`);
  }

  private check() {
    if (this.db.fail) throw new Error('D1 is not available');
  }

  private execute(): number {
    if (this.sql.startsWith('INSERT INTO analytics_session ')
      && this.sql.includes('ON CONFLICT(session_id) DO UPDATE SET ')
      && this.sql.endsWith('WHERE excluded.snapshot > analytics_session.snapshot')) {
      const row = this.columnsToRow();
      const stored = this.db.sessions.get(row.session_id as string);
      if (!stored) {
        this.db.sessions.set(row.session_id as string, row);
        return 1;
      }
      if ((row.snapshot as number) <= (stored.snapshot as number)) return 0;
      this.db.sessions.set(row.session_id as string, { ...row, first_received_at: stored.first_received_at });
      return 1;
    }
    if (this.sql === 'DELETE FROM analytics_session WHERE last_received_at < ?') {
      return deleteWhere(this.db.sessions, row => (row.last_received_at as number) < (this.args[0] as number));
    }
    if (this.sql === 'DELETE FROM analytics_rate WHERE hour < ?') {
      return deleteWhere(this.db.rates, row => (row.hour as number) < (this.args[0] as number));
    }
    if (this.sql.startsWith('INSERT INTO analytics_retention_status ')) {
      const [ranAt, deletedCount, oldest] = this.args;
      this.db.retentionStatus = { id: 1, ran_at: ranAt, deleted_count: deletedCount, oldest_received_at: oldest };
      return 1;
    }
    throw new Error(`Unknown statement: ${this.sql}`);
  }

  private columnsToRow(): Row {
    const columns = /\(([^)]+)\) VALUES/.exec(this.sql)![1].split(',').map(column => column.trim());
    if (columns.length !== this.args.length) throw new Error(`${columns.length} columns, ${this.args.length} values`);
    return Object.fromEntries(columns.map((column, i) => [column, this.args[i]]));
  }
}

function deleteWhere(rows: Map<string, Row>, matches: (row: Row) => boolean): number {
  let changes = 0;
  for (const [key, row] of rows) {
    if (matches(row)) { rows.delete(key); changes++; }
  }
  return changes;
}
