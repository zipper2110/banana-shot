/**
 * HTTP Basic authentication (decision 7). Only the password counts. Without a configured password, nobody gets in.
 */
export async function isAuthorized(request: Request, password: string | undefined): Promise<boolean> {
  if (!password) return false;
  const header = request.headers.get('authorization') ?? '';
  if (!header.startsWith('Basic ')) return false;
  let decoded: string;
  try {
    decoded = atob(header.slice('Basic '.length).trim());
  } catch {
    return false;
  }
  const given = decoded.slice(decoded.indexOf(':') + 1);
  return await sameText(given, password);
}

/** Compares the SHA-256 digests, so that the time does not depend on the matching prefix. */
async function sameText(a: string, b: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const [hashA, hashB] = await Promise.all([
    crypto.subtle.digest('SHA-256', encoder.encode(a)),
    crypto.subtle.digest('SHA-256', encoder.encode(b)),
  ]);
  const bytesA = new Uint8Array(hashA), bytesB = new Uint8Array(hashB);
  let difference = 0;
  for (let index = 0; index < bytesA.length; index++) difference |= bytesA[index] ^ bytesB[index];
  return difference === 0;
}
