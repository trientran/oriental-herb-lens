// Herb Lens photo uploads: verifies the user's Firebase ID token and stores a JPEG in R2.
//
//   POST /photos?speciesKey=<GBIF species key>
//   Authorization: Bearer <Firebase ID token>
//   Content-Type: image/jpeg
//   <JPEG bytes>
//
//   201 {"url": "<public URL of the stored photo>"}
//
// The app then adds that URL to herbs/{speciesKey}.images in Firestore, where security rules
// decide who may attach photos (including bans). No R2 credentials ship in the app.

import { AuthError, verifyIdToken } from './firebase-auth.js';

export const MAX_BYTES = 5 * 1024 * 1024; // the app sends ~100 KB (600 px, 70 % JPEG)

function json(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function isJpeg(bytes) {
  return bytes.length > 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

export async function handleRequest(request, env, verify = verifyIdToken) {
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
