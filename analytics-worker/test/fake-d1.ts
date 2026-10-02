/**
 * In-memory D1 for the Worker tests. It knows only the statements that the Worker uses.
 * An unknown statement throws, so a changed query cannot pass the tests by accident.
 */
export type Row = Record<string, unknown>;

export class FakeD1 {
  readonly events = new Map<string, Row>();
  retentionStatus: Row | null = null;
  failBatch = false;

  prepare(sql: string): FakeStatement {
    return new FakeStatement(this, sql);
  }

  async batch(statements: FakeStatement[]): Promise<unknown[]> {
    if (this.failBatch) throw new Error('D1 is not available');
    return statements.map(statement => statement.execute());
  }

  asD1(): D1Database {
    return this as unknown as D1Database;
  }

  eventRows(): Row[] {
    return [...this.events.values()];
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
    return { meta: { changes: this.execute() } };
  }

  async first<T>(): Promise<T | null> {
    if (!this.sql.startsWith('SELECT MIN(received_at) AS oldest FROM analytics_event')) throw new Error(`Unknown query: ${this.sql}`);
    const times = this.db.eventRows().map(row => row.received_at as number);
    return { oldest: times.length ? Math.min(...times) : null } as T;
  }

  execute(): number {
    if (this.sql.startsWith('INSERT OR IGNORE INTO analytics_event ')) {
      const row = this.columnsToRow();
      const key = `${row.session_id}|${row.sequence_number}`;
      if (this.db.events.has(key)) return 0;
      this.db.events.set(key, row);
      return 1;
    }
    if (this.sql.startsWith('DELETE FROM analytics_event WHERE received_at < ?')) {
      const cutoff = this.args[0] as number;
      let changes = 0;
      for (const [key, row] of this.db.events) {
        if ((row.received_at as number) < cutoff) { this.db.events.delete(key); changes++; }
      }
      return changes;
    }
    if (this.sql.startsWith('INSERT INTO analytics_retention_status ')) {
      const [ranAt, deletedCount, oldest] = this.args;
      this.db.retentionStatus = { id: 1, ran_at: ranAt, deleted_count: deletedCount, oldest_received_at: oldest, consecutive_failures: 0 };
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
