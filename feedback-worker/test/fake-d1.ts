/**
 * In-memory D1 for the Worker tests. It knows only the statements that the Worker uses.
 * An unknown statement throws, so a changed query cannot pass the tests by accident.
 */
export type Row = Record<string, unknown>;

export class FakeD1 {
  readonly reports = new Map<string, Row>();
  readonly rates = new Map<string, Row>();
  fail = false;

  prepare(sql: string): FakeStatement {
    return new FakeStatement(this, sql);
  }

  asD1(): D1Database {
    return this as unknown as D1Database;
  }

  reportRows(): Row[] {
    return [...this.reports.values()];
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
    if (this.sql.startsWith('SELECT delivered FROM feedback_report WHERE report_id = ?')) {
      const row = this.db.reports.get(this.args[0] as string);
      return (row ? { delivered: row.delivered } : null) as T | null;
    }
    if (this.sql.startsWith('INSERT INTO feedback_rate ') && this.sql.endsWith('RETURNING count')) {
      const [id, hour] = this.args as [string, number];
      const row = this.db.rates.get(id) ?? { id, hour, count: 0 };
      row.count = (row.count as number) + 1;
      this.db.rates.set(id, row);
      return { count: row.count } as T;
    }
    throw new Error(`Unknown query: ${this.sql}`);
  }

  private check() {
    if (this.db.fail) throw new Error('D1 is not available');
  }

  private execute(): number {
    if (this.sql.startsWith('INSERT OR IGNORE INTO feedback_report ')) {
      const row = this.columnsToRow();
      if (this.db.reports.has(row.report_id as string)) return 0;
      this.db.reports.set(row.report_id as string, row);
      return 1;
    }
    if (this.sql.startsWith('UPDATE feedback_report SET delivered = 1 WHERE report_id = ?')) {
      const row = this.db.reports.get(this.args[0] as string);
      if (!row) return 0;
      row.delivered = 1;
      return 1;
    }
    if (this.sql.startsWith('DELETE FROM feedback_report WHERE received_at < ?')) {
      return deleteWhere(this.db.reports, row => (row.received_at as number) < (this.args[0] as number));
    }
    if (this.sql.startsWith('DELETE FROM feedback_rate WHERE hour < ?')) {
      return deleteWhere(this.db.rates, row => (row.hour as number) < (this.args[0] as number));
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
  for (const [key, row] of rows) if (matches(row)) { rows.delete(key); changes++; }
  return changes;
}
