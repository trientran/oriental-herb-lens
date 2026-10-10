// Herb Lens uploads: verifies the user's Firebase ID token and stores a JPEG or a shared model in R2.
//
//   POST /photos?speciesKey=<GBIF species key>
//   Authorization: Bearer <Firebase ID token>
//   Content-Type: image/jpeg
//   <JPEG bytes>
//
//   201 {"url": "<public URL of the stored photo>"}
//
//   POST /models[?id=<uuid>]        a model shared with the community (the Train tab); with an id,
//                                   a retry by the same user replaces the same file
//   Authorization: Bearer <Firebase ID token>
//   <.tflite bytes, at most 25 MB>
//
//   201 {"id": "<uuid>", "url": "<public URL>", "size": <bytes>}
//
//   DELETE /models/<uuid>           by its uploader only: 204 (also its Hugging Face copy and listing)
//
//   POST /models/<uuid>/huggingface {"sha256": "<hex>"}   by its uploader, when they asked for it:
//   publishes it in the Hugging Face organisation; 201 {"url": "https://huggingface.co/..."}
//
// Too many requests get 429 (wrangler.toml [[ratelimits]]): per address before the token is
// checked, then per account. Banned accounts (bannedUsers/{uid}, see src/bans.js) get 403. The
// app then lists the URL in Firestore (herbs/{speciesKey}.images, sharedModels/{id}), where the
// security rules also check bans. No R2 credentials ship in the app.
//
// The web app calls it from the browser, so the origins in ALLOWED_ORIGINS get CORS headers
// (the mobile apps send no Origin).

import { isBanned } from './bans.js';
import { HubError, deleteSharedModel, markPublished, namesAllowed, publish, sharedModel, unpublish } from './huggingface.js';
import { AuthError, verifyIdToken } from './firebase-auth.js';

export const MAX_BYTES = 5 * 1024 * 1024; // the app sends ~100 KB (600 px, 70 % JPEG)
export const MAX_MODEL_BYTES = 25 * 1024 * 1024; // a model with the large backbone is ~12 MB

/** False when [limiter] (a Workers rate limiting binding) says [key] has had its share; true without one. */
async function allowed(limiter, key) {
  if (!limiter || !key) return true;
  const { success } = await limiter.limit({ key });
  return success;
}

function tooMany() {
  const response = json(429, { error: 'Too many uploads; try again in a minute' });
  response.headers.set('retry-after', '60');
  return response;
}

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
    'access-control-allow-methods': 'POST, DELETE',
    'access-control-allow-headers': 'authorization, content-type',
    'access-control-max-age': '86400',
    vary: 'origin',
  };
}

export async function handleRequest(request, env, verify = verifyIdToken, banned = isBanned, fetchFn = fetch) {
  const cors = corsHeaders(request, env);
  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
  const response = await route(request, env, verify, banned, fetchFn);
  for (const [name, value] of Object.entries(cors)) response.headers.set(name, value);
  return response;
}

async function route(request, env, verify, banned, fetchFn) {
  const url = new URL(request.url);
  const model = /^\/models\/([0-9a-f-]{36})$/.exec(url.pathname)?.[1];
  const toHub = /^\/models\/([0-9a-f-]{36})\/huggingface$/.exec(url.pathname)?.[1];
  if (url.pathname === '/photos') {
    if (request.method !== 'POST') return json(405, { error: 'Use POST' });
    return withUser(request, env, verify, banned, (uid) => uploadPhoto(request, env, url, uid));
  }
  if (url.pathname === '/models') {
    if (request.method !== 'POST') return json(405, { error: 'Use POST' });
    return withUser(request, env, verify, banned, (uid) => uploadModel(request, env, url, uid));
  }
  if (model) {
    if (request.method !== 'DELETE') return json(405, { error: 'Use DELETE' });
    // A banned user may still take down their own models
    return withUser(request, env, verify, async () => false, (uid) => deleteModel(env, model, uid, fetchFn));
  }
  if (toHub) {
    if (request.method !== 'POST') return json(405, { error: 'Use POST' });
    return withUser(request, env, verify, banned, (uid) => publishModel(request, env, toHub, uid, fetchFn));
  }
  return json(404, { error: 'Not found' });
}

/** Runs [handle] with the signed-in user's uid, after the rate limits, the token and the ban check. */
async function withUser(request, env, verify, banned, handle) {
  // Per address first, so a flood of bad tokens is turned away before any verification work
  if (!(await allowed(env.UPLOADS_PER_ADDRESS, request.headers.get('cf-connecting-ip')))) return tooMany();

  const token = /^Bearer (.+)$/.exec(request.headers.get('authorization') ?? '')?.[1];
  let uid;
  try {
    uid = await verify(token, env.FIREBASE_PROJECT_ID);
  } catch (error) {
    if (error instanceof AuthError) return json(401, { error: 'Sign in again' });
    throw error;
  }
  if (!(await allowed(env.UPLOADS_PER_USER, uid))) return tooMany();
  try {
    if (await banned(uid, env)) return json(403, { error: 'This account can no longer share' });
  } catch (error) {
    // Firestore unreachable: let the upload through; the rules still refuse to list it for a banned user
    console.warn('Ban check failed', error);
  }
  return handle(uid);
}

async function readBody(request, max) {
  const declared = Number(request.headers.get('content-length') ?? '0');
  if (declared > max) return null;
  const bytes = new Uint8Array(await request.arrayBuffer());
  return bytes.length > max ? null : bytes;
}

async function uploadPhoto(request, env, url, uid) {
  const speciesKey = url.searchParams.get('speciesKey') ?? '';
  if (!/^\d{1,12}$/.test(speciesKey)) return json(400, { error: 'speciesKey must be a GBIF species key' });
  if ((request.headers.get('content-type') ?? '').split(';')[0].trim() !== 'image/jpeg') {
    return json(415, { error: 'Only image/jpeg is accepted' });
  }
  const bytes = await readBody(request, MAX_BYTES);
  if (!bytes) return json(413, { error: 'Photo too large' });
  if (!isJpeg(bytes)) return json(415, { error: 'Not a JPEG' });

  const key = `photos/${speciesKey}/${crypto.randomUUID()}.jpg`;
  await env.BUCKET.put(key, bytes, {
    httpMetadata: { contentType: 'image/jpeg', cacheControl: 'public, max-age=31536000, immutable' },
    customMetadata: { uploader: uid, uploadedAt: new Date().toISOString() },
  });
  return json(201, { url: `${env.PUBLIC_BASE_URL.replace(/\/$/, '')}/${key}` });
}

/** A TensorFlow Lite flatbuffer: its file identifier "TFL3" sits at bytes 4 to 7. */
function isTflite(bytes) {
  return bytes.length > 8 && bytes[4] === 0x54 && bytes[5] === 0x46 && bytes[6] === 0x4c && bytes[7] === 0x33;
}

async function uploadModel(request, env, url, uid) {
  // The app names the share (?id=<uuid>), so a retry after the app was closed reuses the same file
  const requested = url.searchParams.get('id');
  if (requested !== null && !/^[0-9a-f-]{36}$/.test(requested)) return json(400, { error: 'id must be a UUID' });
  const id = requested ?? crypto.randomUUID();
  const key = `models/${id}.tflite`;
  if (requested !== null) {
    const existing = await env.BUCKET.head(key);
    if (existing && existing.customMetadata?.uploader !== uid) return json(409, { error: 'That id is taken' });
  }
  const bytes = await readBody(request, MAX_MODEL_BYTES);
  if (!bytes) return json(413, { error: 'Model too large' });
  if (!isTflite(bytes)) return json(415, { error: 'Not a TensorFlow Lite model' });

  await env.BUCKET.put(key, bytes, {
    httpMetadata: {
      contentType: 'application/octet-stream',
      contentDisposition: `attachment; filename="${id}.tflite"`,
      cacheControl: 'public, max-age=31536000, immutable',
    },
    customMetadata: { uploader: uid, uploadedAt: new Date().toISOString() },
  });
  return json(201, { id, url: `${env.PUBLIC_BASE_URL.replace(/\/$/, '')}/${key}`, size: bytes.length });
}

async function deleteModel(env, id, uid, fetchFn) {
  const key = `models/${id}.tflite`;
  const stored = await env.BUCKET.head(key);
  if (!stored) return json(404, { error: 'No such model' });
  if (stored.customMetadata?.uploader !== uid) return json(403, { error: 'Only its uploader can remove a model' });
  // Its Hugging Face copy and listing go with it (the app also removes the listing itself)
  const listed = await sharedModel(env, id, fetchFn).catch(() => null);
  if (listed?.huggingFaceUrl && env.HF_TOKEN) {
    try {
      await unpublish(env, listed.huggingFaceUrl, fetchFn);
    } catch (error) {
      console.warn('Hugging Face copy not removed', error);
    }
  }
  await env.BUCKET.delete(key);
  if (listed) await deleteSharedModel(env, id, fetchFn).catch((error) => console.warn('Listing not removed', error));
  return new Response(null, { status: 204 });
}

async function publishModel(request, env, id, uid, fetchFn) {
  if (!env.HF_TOKEN || !env.FIREBASE_SERVICE_ACCOUNT) return json(503, { error: 'Publishing on Hugging Face is not set up' });
  const { sha256 } = await request.json().catch(() => ({}));
  if (!/^[0-9a-f]{64}$/.test(sha256 ?? '')) return json(400, { error: 'sha256 of the model file is required' });
  const model = await sharedModel(env, id, fetchFn);
  if (!model) return json(404, { error: 'No such shared model' });
  if (model.uploaderId !== uid) return json(403, { error: 'Only its sharer can publish it' });
  if (model.huggingFace === 'published' && model.huggingFaceUrl) return json(200, { url: model.huggingFaceUrl });
  if (model.huggingFace !== 'requested') return json(409, { error: 'Its sharer didn\'t ask for Hugging Face' });
  if (!namesAllowed([model.name, ...model.species])) {
    await markPublished(env, id, null, fetchFn);
    return json(422, { error: 'Its names can\'t be published' });
  }
  const stored = await env.BUCKET.get(`models/${id}.tflite`);
  if (!stored) return json(404, { error: 'No such model file' });
  try {
    const url = await publish(env, model, new Uint8Array(await stored.arrayBuffer()), sha256, fetchFn);
    await markPublished(env, id, url, fetchFn);
    return json(201, { url });
  } catch (error) {
    if (!(error instanceof HubError)) throw error;
    console.warn('Hugging Face publishing failed', error);
    return json(502, { error: 'Hugging Face publishing failed; it stays requested' });
  }
}

export default {
  fetch: (request, env) => handleRequest(request, env),
};
