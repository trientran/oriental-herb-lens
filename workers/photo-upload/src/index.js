// Herb Lens photo uploads: verifies the user's Firebase ID token and stores a JPEG in R2.
//
//   POST /photos?speciesKey=<GBIF species key>
//   Authorization: Bearer <Firebase ID token>
//   Content-Type: image/jpeg
//   <JPEG bytes>
//
//   201 {"url": "<public URL of the stored photo>"}
//
// Banned accounts (bannedUsers/{uid}, see src/bans.js) get 403. The app then adds the URL to
// herbs/{speciesKey}.images in Firestore, where security rules also check bans. No R2
// credentials ship in the app.
//
// The web app calls it from the browser, so the origins in ALLOWED_ORIGINS get CORS headers
// (the mobile apps send no Origin).

import { isBanned } from './bans.js';
import { AuthError, verifyIdToken } from './firebase-auth.js';

export const MAX_BYTES = 5 * 1024 * 1024; // the app sends ~100 KB (600 px, 70 % JPEG)

function json(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function isJpeg(bytes) {
  return bytes.length > 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

function corsHeaders(request, env) {
  const origin = request.headers.get('origin');
  const allowed = (env.ALLOWED_ORIGINS ?? '').split(',').map((o) => o.trim()).filter(Boolean);
  if (!origin || !allowed.includes(origin)) return {};
  return {
    'access-control-allow-origin': origin,
    'access-control-allow-methods': 'POST',
    'access-control-allow-headers': 'authorization, content-type',
    'access-control-max-age': '86400',
    vary: 'origin',
  };
}

export async function handleRequest(request, env, verify = verifyIdToken, banned = isBanned) {
  const cors = corsHeaders(request, env);
  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
  const response = await handleUpload(request, env, verify, banned);
  for (const [name, value] of Object.entries(cors)) response.headers.set(name, value);
  return response;
}

async function handleUpload(request, env, verify, banned) {
  const url = new URL(request.url);
  if (url.pathname !== '/photos') return json(404, { error: 'Not found' });
  if (request.method !== 'POST') return json(405, { error: 'Use POST' });

  const token = /^Bearer (.+)$/.exec(request.headers.get('authorization') ?? '')?.[1];
  let uid;
  try {
    uid = await verify(token, env.FIREBASE_PROJECT_ID);
  } catch (error) {
    if (error instanceof AuthError) return json(401, { error: 'Sign in again' });
    throw error;
  }
  try {
    if (await banned(uid, env)) return json(403, { error: 'This account can no longer share photos' });
  } catch (error) {
    // Firestore unreachable: let the upload through; the rules still refuse to list it for a banned user
    console.warn('Ban check failed', error);
  }

  const speciesKey = url.searchParams.get('speciesKey') ?? '';
  if (!/^\d{1,12}$/.test(speciesKey)) return json(400, { error: 'speciesKey must be a GBIF species key' });
  if ((request.headers.get('content-type') ?? '').split(';')[0].trim() !== 'image/jpeg') {
    return json(415, { error: 'Only image/jpeg is accepted' });
  }
  const declared = Number(request.headers.get('content-length') ?? '0');
  if (declared > MAX_BYTES) return json(413, { error: 'Photo too large' });

  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.length > MAX_BYTES) return json(413, { error: 'Photo too large' });
  if (!isJpeg(bytes)) return json(415, { error: 'Not a JPEG' });

  const key = `photos/${speciesKey}/${crypto.randomUUID()}.jpg`;
  await env.BUCKET.put(key, bytes, {
    httpMetadata: { contentType: 'image/jpeg', cacheControl: 'public, max-age=31536000, immutable' },
    customMetadata: { uploader: uid, uploadedAt: new Date().toISOString() },
  });
  return json(201, { url: `${env.PUBLIC_BASE_URL.replace(/\/$/, '')}/${key}` });
}

export default {
  fetch: (request, env) => handleRequest(request, env),
};
