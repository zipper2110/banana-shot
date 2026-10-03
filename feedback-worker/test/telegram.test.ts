import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { deliver, fullText, header, TELEGRAM_TEXT_LIMIT } from '../src/telegram';
import { validateReport, type Report } from '../src/validation';
import { FakeTelegram } from './fake-telegram';
import { LOG, report } from './fixtures';

const config = { token: '123:token', chatId: '42' };
let telegram: FakeTelegram;

const valid = (overrides: Record<string, unknown> = {}): Report => validateReport(report(overrides))!;

beforeEach(() => {
  telegram = new FakeTelegram();
  telegram.install();
});

afterEach(() => vi.unstubAllGlobals());

describe('the message text', () => {
  it('has the topic, the ID, the address, and the app data', () => {
    expect(header(valid())).toBe([
      'New report: Problem',
      'ID: 00000000-0000-4000-8000-000000000201',
      'Reply to: user@example.test',
      'App: 1.0.0 · Windows 11 10.0 · Java 21.0.4',
      'Log: no',
    ].join('\n'));
  });

  it('tells when the user gave no address', () => {
    expect(header(valid({ email: undefined }))).toContain('Reply to: no address');
  });

  it('adds the error text after the message', () => {
    expect(fullText(valid({ error: 'Export failed' }))).toMatch(/The export stops\.\n\nError:\nExport failed$/);
  });
});

describe('deliver', () => {
  it('sends a short report as one message', async () => {
    await deliver(valid(), config);
    expect(telegram.calls).toHaveLength(1);
    expect(telegram.calls[0].json).toEqual({ chat_id: '42', text: fullText(valid()), disable_web_page_preview: true });
  });

  it('sends a report of exactly 4,096 characters as one message', async () => {
    const base = fullText(valid({ message: '' + 'x' }));
    const value = valid({ message: 'x'.repeat(TELEGRAM_TEXT_LIMIT - base.length + 1) });
    expect(fullText(value)).toHaveLength(TELEGRAM_TEXT_LIMIT);
    await deliver(value, config);
    expect(telegram.calls.map(call => call.method)).toEqual(['sendMessage']);
  });

  it('sends a longer text as a file', async () => {
    const value = valid({ message: 'x'.repeat(TELEGRAM_TEXT_LIMIT) });
    await deliver(value, config);
    expect(telegram.calls.map(call => call.method)).toEqual(['sendMessage', 'sendDocument']);
    expect(telegram.calls[0].json!.text).toContain('It is in the file.');
    const file = telegram.calls[1].form!.document as File;
    expect(file.name).toBe('report-00000000-0000-4000-8000-000000000201.txt');
    expect(await file.text()).toBe(fullText(value));
  });

  it('sends the log as a gzip file', async () => {
    await deliver(valid({ log: LOG }), config);
    expect(telegram.calls.map(call => call.method)).toEqual(['sendMessage', 'sendDocument']);
    const file = telegram.calls[1].form!.document as File;
    expect(file.name).toBe('bananashot-log-00000000-0000-4000-8000-000000000201.log.gz');
    const bytes = new Uint8Array(await file.arrayBuffer());
    expect([bytes[0], bytes[1]]).toEqual([0x1f, 0x8b]);
    expect(telegram.calls[1].form!.chat_id).toBe('42');
  });

  it('throws when Telegram refuses a request', async () => {
    telegram.failAll = true;
    await expect(deliver(valid(), config)).rejects.toThrow('Telegram sendMessage failed with status 400');
  });

  it('does not put the token in the error', async () => {
    telegram.failAll = true;
    await expect(deliver(valid(), config)).rejects.not.toThrow(/token/);
  });
});
