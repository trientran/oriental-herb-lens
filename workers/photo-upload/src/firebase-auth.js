// Verifies Firebase Authentication ID tokens with WebCrypto, following
// https://firebase.google.com/docs/auth/admin/verify-id-tokens#verify_id_tokens_using_a_third-party_jwt_library

export const GOOGLE_JWKS_URL =
  'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';

const CLOCK_SKEW_SECONDS = 60;

export class AuthError extends Error {}

let cachedKeys = null; // { keys, expiresAt }

/** Google's current signing keys, cached for as long as the response's Cache-Control allows. */
export async function googleSigningKeys(fetchFn = fetch, nowMs = Date.now()) {
  if (cachedKeys && cachedKeys.expiresAt > nowMs) return cachedKeys.keys;
  const response = await fetchFn(GOOGLE_JWKS_URL);
  if (!response.ok) throw new Error(`Couldn't fetch Google signing keys: HTTP ${response.status}`);
  const { keys } = await response.json();
  const maxAge = /max-age=(\d+)/.exec(response.headers.get('cache-control') ?? '')?.[1];
  cachedKeys = { keys, expiresAt: nowMs + (maxAge ? Number(maxAge) * 1000 : 3600_000) };
  return keys;
}

export function clearKeyCache() {
  cachedKeys = null;
}

function base64UrlDecode(part) {
  const base64 = part.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(part.length / 4) * 4, '=');
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function decodeJson(part) {
  try {
    return JSON.parse(new TextDecoder().decode(base64UrlDecode(part)));
  } catch {
    throw new AuthError('Malformed token');
  }
}

/**
 * Returns the user's uid if [token] is a valid, unexpired ID token for [projectId].
 * Throws AuthError otherwise. [getKeys] returns Google's JWKs (injectable for tests).
 */
export async function verifyIdToken(token, projectId, { getKeys = googleSigningKeys, nowSeconds = Date.now() / 1000 } = {}) {
  const parts = (token ?? '').split('.');
  if (parts.length !== 3) throw new AuthError('Malformed token');
  const [headerPart, payloadPart, signaturePart] = parts;
  const header = decodeJson(headerPart);
  const payload = decodeJson(payloadPart);

  if (header.alg !== 'RS256') throw new AuthError('Unexpected algorithm');
  const jwk = (await getKeys()).find((key) => key.kid === header.kid);
  if (!jwk) throw new AuthError('Unknown signing key');

  const key = await crypto.subtle.importKey(
    'jwk',
    { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: 'RS256', ext: true },
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['verify'],
  );
  const signed = new TextEncoder().encode(`${headerPart}.${payloadPart}`);
  const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, base64UrlDecode(signaturePart), signed);
  if (!valid) throw new AuthError('Bad signature');

  if (payload.aud !== projectId) throw new AuthError('Wrong audience');
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw new AuthError('Wrong issuer');
  if (typeof payload.sub !== 'string' || payload.sub.length === 0) throw new AuthError('No subject');
  if (!(payload.exp > nowSeconds)) throw new AuthError('Expired');
  if (!(payload.iat <= nowSeconds + CLOCK_SKEW_SECONDS)) throw new AuthError('Issued in the future');
  if (payload.auth_time !== undefined && !(payload.auth_time <= nowSeconds + CLOCK_SKEW_SECONDS)) {
    throw new AuthError('Authenticated in the future');
  }
  return payload.sub;
}
