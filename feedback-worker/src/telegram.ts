import { decodeBase64, type Report } from './validation';

/** Telegram refuses a message text that is longer than 4,096 characters. */
export const TELEGRAM_TEXT_LIMIT = 4096;

export type TelegramConfig = { token: string; chatId: string };

const topicTitles: Record<Report['topic'], string> = { problem: 'Problem', idea: 'Idea', question: 'Question', other: 'Other' };

/** The lines above the message text: the topic, the ID, the reply address, and the app data. */
export function header(report: Report): string {
  return [
    `New report: ${topicTitles[report.topic]}`,
    `ID: ${report.report_id}`,
    `Reply to: ${report.email ?? 'no address'}`,
    `App: ${report.app_version} · ${report.os_name} ${report.os_version} · Java ${report.java_version}`,
    `Log: ${report.log ? 'attached' : 'no'}`,
  ].join('\n');
}

/** The full text of a report: the header, the message, and the error text. */
export function fullText(report: Report): string {
  const parts = [header(report), report.message];
  if (report.error) parts.push(`Error:\n${report.error}`);
  return parts.join('\n\n');
}

/**
 * Sends the report to the author chat. A text that is too long for one message goes as a file. The log goes as a
 * file. Throws when Telegram does not accept a request. The log stays only in memory.
 */
export async function deliver(report: Report, config: TelegramConfig): Promise<void> {
  const text = fullText(report);
  if (text.length <= TELEGRAM_TEXT_LIMIT) {
    await call(config, 'sendMessage', JSON.stringify({ chat_id: config.chatId, text, disable_web_page_preview: true }));
  } else {
    await call(config, 'sendMessage', JSON.stringify({ chat_id: config.chatId, text: `${header(report)}\n\nThe text is long. It is in the file.`, disable_web_page_preview: true }));
    await sendDocument(config, new Blob([text], { type: 'text/plain;charset=utf-8' }), `report-${report.report_id}.txt`, `Text of ${report.report_id}`);
  }
  if (report.log) {
    const bytes = decodeBase64(report.log);
    if (!bytes) throw new Error('The log is not Base64');
    await sendDocument(config, new Blob([bytes], { type: 'application/gzip' }), `bananashot-log-${report.report_id}.log.gz`, `Log of ${report.report_id}`);
  }
}

async function sendDocument(config: TelegramConfig, file: Blob, fileName: string, caption: string): Promise<void> {
  const form = new FormData();
  form.set('chat_id', config.chatId);
  form.set('caption', caption);
  form.set('document', file, fileName);
  await call(config, 'sendDocument', form);
}

async function call(config: TelegramConfig, method: string, body: string | FormData): Promise<void> {
  const headers = typeof body === 'string' ? { 'content-type': 'application/json' } : undefined;
  const response = await fetch(`https://api.telegram.org/bot${config.token}/${method}`, { method: 'POST', headers, body });
  const result = await response.json().catch(() => null) as { ok?: boolean } | null;
  // The error must not contain the URL: the URL contains the bot token.
  if (!response.ok || result?.ok !== true) throw new Error(`Telegram ${method} failed with status ${response.status}`);
}
