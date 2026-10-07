import assert from 'node:assert/strict';
import { test } from 'node:test';
import { verifyIdToken } from '../src/firebase-auth.js';
import { MAX_BYTES, MAX_MODEL_BYTES, handleRequest } from '../src/index.js';
import { PROJECT, getKeys, signToken } from './helpers.js';

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4]);

function fakeEnv() {
  const stored = new Map();
  return {
    stored,
    FIREBASE_PROJECT_ID: PROJECT,
    PUBLIC_BASE_URL: 'https://pub-abc.r2.dev/',
    BUCKET: {
      put: async (key, value, options) => stored.set(key, { value, options }),
      head: async (key) => (stored.has(key) ? { customMetadata: stored.get(key).options.customMetadata } : null),
      delete: async (key) => stored.delete(key),
    },
  };
}

const verify = (token, project) => verifyIdToken(token, project, { getKeys });

/** A rate limiter that allows [limit] calls per key. */
function limiter(limit) {
  const counts = new Map();
  return { limit: async ({ key }) => { counts.set(key, (counts.get(key) ?? 0) + 1); return { success: counts.get(key) <= limit }; } };
}

async function upload({ token, speciesKey = '3035652', body = JPEG, type = 'image/jpeg', method = 'POST', path = '/photos', banned = async () => false, env = fakeEnv(), ip } = {}) {
  const headers = { 'content-type': type };
  if (ip) headers['cf-connecting-ip'] = ip;
  if (token !== null) headers.authorization = `Bearer ${token ?? (await signToken())}`;
  const request = new Request(`https://worker.example${path}?speciesKey=${speciesKey}`, {
    method,
    headers,
    body: method === 'POST' ? body : undefined,
  });
  const response = await handleRequest(request, env, verify, banned);
  return { response, env, json: await response.json() };
}

test('stores the JPEG under its species and returns the public URL', async () => {
  const { response, env, json } = await upload();

  assert.equal(response.status, 201);
  assert.match(json.url, /^https:\/\/pub-abc\.r2\.dev\/photos\/3035652\/[0-9a-f-]{36}\.jpg$/);
  const [[key, { value, options }]] = [...env.stored];
  assert.equal(`https://pub-abc.r2.dev/${key}`, json.url);
  assert.deepEqual([...value], [...JPEG]);
  assert.equal(options.httpMetadata.contentType, 'image/jpeg');
  assert.equal(options.customMetadata.uploader, 'uid-123');
});

test('requires a valid sign-in', async () => {
  assert.equal((await upload({ token: null })).response.status, 401);
  assert.equal((await upload({ token: 'garbage' })).response.status, 401);
  assert.equal((await upload({ token: await signToken({ aud: 'other' }) })).response.status, 401);
});

test('rejects bad requests without storing anything', async () => {
  for (const [args, status] of [
    [{ speciesKey: 'abc' }, 400],
    [{ speciesKey: '' }, 400],
    [{ type: 'image/png' }, 415],
    [{ body: new Uint8Array([0x89, 0x50, 0x4e, 0x47]) }, 415],
    [{ body: new Uint8Array(MAX_BYTES + 1).fill(0xff) }, 413],
    [{ method: 'GET' }, 405],
    [{ path: '/other' }, 404],
  ]) {
    const { response, env } = await upload(args);
    assert.equal(response.status, status, JSON.stringify(Object.keys(args)));
    assert.equal(env.stored.size, 0);
  }
});

test('banned accounts are refused and nothing is stored', async () => {
  const { response, env } = await upload({ banned: async (uid) => uid === 'uid-123' });
  assert.equal(response.status, 403);
  assert.equal(env.stored.size, 0);
});

test('an unreachable ban list does not block uploads', async () => {
  const { response } = await upload({ banned: async () => { throw new Error('offline'); } });
  assert.equal(response.status, 201);
});

test('answers the browser preflight for allowed origins only', async () => {
  const env = { ...fakeEnv(), ALLOWED_ORIGINS: 'https://med-herb-lens.pages.dev,http://localhost:8080' };
  const preflight = (origin) => handleRequest(new Request('https://worker.example/photos', { method: 'OPTIONS', headers: { origin } }), env, verify);

  const allowed = await preflight('https://med-herb-lens.pages.dev');
  assert.equal(allowed.status, 204);
  assert.equal(allowed.headers.get('access-control-allow-origin'), 'https://med-herb-lens.pages.dev');
  assert.match(allowed.headers.get('access-control-allow-headers'), /authorization/);
  assert.equal((await preflight('https://evil.example')).headers.get('access-control-allow-origin'), null);
});

test('adds CORS headers to responses for an allowed origin', async () => {
  const env = { ...fakeEnv(), ALLOWED_ORIGINS: 'http://localhost:8080' };
  const request = new Request('https://worker.example/photos?speciesKey=3035652', {
    method: 'POST',
    headers: { origin: 'http://localhost:8080', 'content-type': 'image/jpeg', authorization: `Bearer ${await signToken()}` },
    body: JPEG,
  });
  const response = await handleRequest(request, env, verify, async () => false);

  assert.equal(response.status, 201);
  assert.equal(response.headers.get('access-control-allow-origin'), 'http://localhost:8080');
});

test('an account sending too many photos at once is asked to wait', async () => {
  const env = { ...fakeEnv(), UPLOADS_PER_USER: limiter(2) };

  assert.equal((await upload({ env })).response.status, 201);
  assert.equal((await upload({ env })).response.status, 201);
  const { response } = await upload({ env });

  assert.equal(response.status, 429);
  assert.equal(response.headers.get('retry-after'), '60');
  assert.equal(env.stored.size, 2);
});

test('one address is limited before its tokens are checked', async () => {
  const env = { ...fakeEnv(), UPLOADS_PER_ADDRESS: limiter(1) };
  let verified = 0;
  const counting = async (token, project) => { verified++; return verify(token, project); };
  const request = () => new Request('https://worker.example/photos?speciesKey=1', {
    method: 'POST', headers: { 'content-type': 'image/jpeg', 'cf-connecting-ip': '203.0.113.9', authorization: 'Bearer bad' }, body: JPEG,
  });

  assert.equal((await handleRequest(request(), env, counting, async () => false)).status, 401);
  assert.equal((await handleRequest(request(), env, counting, async () => false)).status, 429);
  assert.equal(verified, 1);
});

const TFLITE = new Uint8Array([0x1c, 0, 0, 0, 0x54, 0x46, 0x4c, 0x33, 9, 9, 9]);

async function send(env, { method = 'POST', path = '/models', body = TFLITE, sub = 'uid-123', token } = {}) {
  const request = new Request(`https://worker.example${path}`, {
    method,
    headers: { authorization: `Bearer ${token ?? (await signToken({ sub }))}`, 'content-type': 'application/octet-stream' },
    body: method === 'POST' ? body : undefined,
  });
  return handleRequest(request, env, verify, async () => false);
}

test('a shared model is stored under its own id, with its uploader', async () => {
  const env = fakeEnv();
  const response = await send(env);
  const json = await response.json();

  assert.equal(response.status, 201);
  assert.match(json.id, /^[0-9a-f-]{36}$/);
  assert.equal(json.url, `https://pub-abc.r2.dev/models/${json.id}.tflite`);
  assert.equal(json.size, TFLITE.length);
  assert.equal(env.stored.get(`models/${json.id}.tflite`).options.customMetadata.uploader, 'uid-123');
});

test('only TensorFlow Lite files within the size limit are accepted as models', async () => {
  const env = fakeEnv();
  assert.equal((await send(env, { body: JPEG })).status, 415);
  const big = new Uint8Array(MAX_MODEL_BYTES + 1);
  big.set(TFLITE);
  assert.equal((await send(env, { body: big })).status, 413);
  assert.equal(env.stored.size, 0);
});

test('only its uploader can remove a shared model', async () => {
  const env = fakeEnv();
  const { id } = await (await send(env)).json();

  assert.equal((await send(env, { method: 'DELETE', path: `/models/${id}`, sub: 'someone-else' })).status, 403);
  assert.equal((await send(env, { method: 'DELETE', path: `/models/${id}` })).status, 204);
  assert.equal(env.stored.size, 0);
  assert.equal((await send(env, { method: 'DELETE', path: `/models/${id}` })).status, 404);
});

test('a share named by the app can be retried by its sharer, not taken by someone else', async () => {
  const env = fakeEnv();
  const id = '7d444840-9dc0-11d1-b245-5ffdce74fad2';
  const first = await (await send(env, { path: `/models?id=${id}` })).json();
  const again = await send(env, { path: `/models?id=${id}` });

  assert.equal(first.id, id);
  assert.equal(again.status, 201);
  assert.equal(env.stored.size, 1);
  assert.equal((await send(env, { path: `/models?id=${id}`, sub: 'someone-else' })).status, 409);
  assert.equal((await send(env, { path: '/models?id=not-a-uuid' })).status, 400);
});

