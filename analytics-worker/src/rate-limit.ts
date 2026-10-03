/**
 * The number of summaries that one IP address can send in one hour (B-9 decision 3). One app sends a maximum of
 * about 14 summaries in one hour, so about 20 apps behind one shared address do not block each other.
 */
export const HOURLY_LIMIT = 300;

export const HOUR = 60 * 60 * 1000;

/**
 * Counts the summaries of one IP address in the current hour. Returns false when the address is over the limit.
 *
 * The Worker does not keep the IP address. The key of a row is an HMAC of the hour and the address, with the secret
 * [key]. Without the secret, a person who reads the table cannot find the address. Each call deletes the rows of
 * the previous hours, so a row stays for one hour at most. This is the method of the feedback Worker (B-9
 * decision 2).
 */
export async function allow(db: D1Database, key: string, ip: string, now = Date.now()): Promise<boolean> {
  const hour = Math.floor(now / HOUR);
  const id = await hmac(key, `${hour}|${ip}`);
  await db.prepare('DELETE FROM analytics_rate WHERE hour < ?').bind(hour).run();
  const row = await db.prepare('INSERT INTO analytics_rate (id,hour,count) VALUES (?,?,1) ON CONFLICT(id) DO UPDATE SET count = count + 1 RETURNING count').bind(id, hour).first<{ count: number }>();
  return (row?.count ?? 1) <= HOURLY_LIMIT;
}

async function hmac(key: string, value: string): Promise<string> {
  const encoder = new TextEncoder();
  const cryptoKey = await crypto.subtle.importKey('raw', encoder.encode(key), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('HMAC', cryptoKey, encoder.encode(value));
  return [...new Uint8Array(signature)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}
