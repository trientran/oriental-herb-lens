// Checks whether an account is banned: bannedUsers/{uid} in Firestore, which the admin creates
// (tools/moderate.py ban). Read with a service account, since clients can't read bans and App
// Check would turn away requests from the Worker. The key is the Worker secret
// FIREBASE_SERVICE_ACCOUNT: wrangler secret put FIREBASE_SERVICE_ACCOUNT < key.json

export const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const SCOPE = 'https://www.googleapis.com/auth/datastore';

let cachedToken = null; // { token, expiresAt }

export function clearTokenCache() {
  cachedToken = null;
}

const base64Url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const encodeJson = (obj) => base64Url(new TextEncoder().encode(JSON.stringify(obj)));

async function signingKey(pem) {
  const der = Uint8Array.from(atob(pem.replace(/-----[^-]+-----/g, '').replace(/\s/g, '')), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
}

/** An OAuth access token for the service account, from a signed JWT; reused until close to expiry. */
async function accessToken(account, fetchFn, nowMs) {
  if (cachedToken && cachedToken.expiresAt > nowMs + 60_000) return cachedToken.token;
  const iat = Math.floor(nowMs / 1000);
  const unsigned = `${encodeJson({ alg: 'RS256', typ: 'JWT' })}.${encodeJson({
    iss: account.client_email, scope: SCOPE, aud: TOKEN_URL, iat, exp: iat + 3600,
  })}`;
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', await signingKey(account.private_key), new TextEncoder().encode(unsigned));
  const response = await fetchFn(TOKEN_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${base64Url(signature)}` }),
  });
  if (!response.ok) throw new Error(`Service account token refused: HTTP ${response.status}`);
  const { access_token: token, expires_in: expiresIn } = await response.json();
  cachedToken = { token, expiresAt: nowMs + expiresIn * 1000 };
  return token;
}

/**
 * A Firestore REST request as the service account, to [path] under the database's documents
 * (e.g. "sharedModels/<id>"); null when no service account is set up.
 */
export async function firestoreRequest(env, path, init = {}, fetchFn = fetch, nowMs = Date.now()) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) return null;
  const token = await accessToken(JSON.parse(env.FIREBASE_SERVICE_ACCOUNT), fetchFn, nowMs);
  const url = `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/${path}`;
  return fetchFn(url, { ...init, headers: { ...(init.headers ?? {}), authorization: `Bearer ${token}` } });
}

/** True when bannedUsers/{uid} exists; false when it doesn't, or when no service account is set up. */
export async function isBanned(uid, env, fetchFn = fetch, nowMs = Date.now()) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) return false;
  const token = await accessToken(JSON.parse(env.FIREBASE_SERVICE_ACCOUNT), fetchFn, nowMs);
  const url = `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/bannedUsers/${encodeURIComponent(uid)}`;
  const response = await fetchFn(url, { headers: { authorization: `Bearer ${token}` } });
  if (response.status === 404) return false;
  if (response.ok) return true;
  throw new Error(`Ban check failed: HTTP ${response.status}`);
}
