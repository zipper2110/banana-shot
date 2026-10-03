import { vi } from 'vitest';

/** One request of the Worker to the Telegram Bot API. */
export type TelegramCall = { method: string; url: string; json?: Record<string, unknown>; form?: Record<string, File | string> };

/** Replaces `fetch` with a fake Telegram. Set `failAt` to fail the call with that index (0 is the first call). */
export class FakeTelegram {
  readonly calls: TelegramCall[] = [];
  failAt: number | null = null;
  failAll = false;

  install() {
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      const method = url.substring(url.lastIndexOf('/') + 1);
      const call: TelegramCall = { method, url };
      if (typeof init.body === 'string') call.json = JSON.parse(init.body);
      else if (init.body instanceof FormData) call.form = Object.fromEntries(init.body.entries());
      const index = this.calls.length;
      this.calls.push(call);
      if (this.failAll || this.failAt === index) return Response.json({ ok: false, description: 'Bad Request' }, { status: 400 });
      return Response.json({ ok: true, result: {} });
    }));
  }
}
