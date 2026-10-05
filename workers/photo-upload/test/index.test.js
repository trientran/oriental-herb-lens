import assert from 'node:assert/strict';
import { test } from 'node:test';
import { verifyIdToken } from '../src/firebase-auth.js';
import { MAX_BYTES, handleRequest } from '../src/index.js';
import { PROJECT, getKeys, signToken } from './helpers.js';

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4]);

function fakeEnv() {
  const stored = new Map();
  return {
    stored,
    FIREBASE_PROJECT_ID: PROJECT,
    PUBLIC_BASE_URL: 'https://pub-abc.r2.dev/',
    BUCKET: { put: async (key, value, options) => stored.set(key, { value, options }) },
  };
}

const verify = (token, project) => verifyIdToken(token, project, { getKeys });

async function upload({ token, speciesKey = '3035652', body = JPEG, type = 'image/jpeg', method = 'POST', path = '/photos', banned = async () => false } = {}) {
  const env = fakeEnv();
  const headers = { 'content-type': type };
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
