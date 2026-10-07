import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { TOKEN_URL, clearTokenCache } from '../src/bans.js';
import { modelCard, namesAllowed, repoName, slug } from '../src/huggingface.js';
import { handleRequest } from '../src/index.js';
import { verifyIdToken } from '../src/firebase-auth.js';
import { PROJECT, getKeys, signToken } from './helpers.js';

const keyPair = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify'],
);
const pkcs8 = Buffer.from(await crypto.subtle.exportKey('pkcs8', keyPair.privateKey)).toString('base64');
const verify = (token, project) => verifyIdToken(token, project, { getKeys });
const ID = '0f8fad5b-d9cb-469f-a165-70867728950e';
const SHA = 'a'.repeat(64);
const FILE = new Uint8Array([0x1c, 0, 0, 0, 0x54, 0x46, 0x4c, 0x33, 1, 2, 3, 4]);

beforeEach(clearTokenCache);

/** A Worker environment with the model in R2 and a fake Firestore entry, Hugging Face and Google. */
function world({ huggingFace = 'requested', uploaderId = 'uid-123', name = 'Garden herbs', hfToken = 'hf_secret', chunked = false } = {}) {
  const calls = [];
  const doc = {
    name: { stringValue: name },
    species: { arrayValue: { values: [{ stringValue: 'Mint' }, { stringValue: 'Bạc hà' }] } },
    backbone: { stringValue: 'mobilenet_v3_large' },
    trainable: { booleanValue: true },
    uploaderId: { stringValue: uploaderId },
    huggingFace: { stringValue: huggingFace },
  };
  const stored = new Map([[`models/${ID}.tflite`, { value: FILE, options: { customMetadata: { uploader: uploaderId } } }]]);
  let docExists = true;
  const fetchFn = async (url, init = {}) => {
    const method = init.method ?? 'GET';
    calls.push({ url: String(url), method, init });
    if (url === TOKEN_URL) return Response.json({ access_token: 'google-token', expires_in: 3600 });
    if (String(url).includes('firestore.googleapis.com')) {
      if (method === 'GET') return docExists ? Response.json({ fields: doc }) : new Response('', { status: 404 });
      if (method === 'PATCH') {
        Object.assign(doc, JSON.parse(init.body).fields);
        return Response.json({});
      }
      if (method === 'DELETE') { docExists = false; return Response.json({}); }
    }
    if (url === 'https://huggingface.co/api/repos/create') return Response.json({ url: 'x' });
    if (String(url).endsWith('/info/lfs/objects/batch')) {
      const header = chunked ? { chunk_size: '8', 1: 'https://s3.example/part1', 2: 'https://s3.example/part2' } : {};
      return Response.json({ objects: [{ oid: SHA, size: FILE.length, actions: { upload: { href: chunked ? 'https://hub.example/complete' : 'https://s3.example/put', header }, verify: { href: 'https://hub.example/verify' } } }] });
    }
    if (String(url).startsWith('https://s3.example/')) return new Response('', { status: 200, headers: { etag: `"etag-${String(url).slice(-1)}"` } });
    if (String(url).startsWith('https://hub.example/')) return Response.json({});
    if (String(url).includes('/commit/main')) return Response.json({ commitUrl: 'c', commitOid: 'o' });
    if (url === 'https://huggingface.co/api/repos/delete') return Response.json({});
    throw new Error(`unexpected ${method} ${url}`);
  };
  const env = {
    FIREBASE_PROJECT_ID: PROJECT,
    PUBLIC_BASE_URL: 'https://pub-abc.r2.dev/',
    HF_TOKEN: hfToken,
    FIREBASE_SERVICE_ACCOUNT: JSON.stringify({ client_email: 'w@x.iam.gserviceaccount.com', private_key: `-----BEGIN PRIVATE KEY-----\n${pkcs8}\n-----END PRIVATE KEY-----\n` }),
    BUCKET: {
      get: async (key) => (stored.has(key) ? { arrayBuffer: async () => stored.get(key).value.buffer } : null),
      head: async (key) => (stored.has(key) ? { customMetadata: stored.get(key).options.customMetadata } : null),
      delete: async (key) => stored.delete(key),
    },
  };
  return { env, calls, doc, stored, fetchFn, docExists: () => docExists };
}

async function call(w, { method = 'POST', path = `/models/${ID}/huggingface`, body = { sha256: SHA }, sub = 'uid-123' } = {}) {
  const request = new Request(`https://worker.example${path}`, {
    method,
    headers: { authorization: `Bearer ${await signToken({ sub })}`, 'content-type': 'application/json' },
    body: method === 'POST' ? JSON.stringify(body) : undefined,
  });
  return handleRequest(request, w.env, verify, async () => false, w.fetchFn);
}

test('repository names are plain ASCII and unique to the model', () => {
  assert.equal(slug('Rau Hà Nội (2026)!'), 'rau-ha-noi-2026');
  assert.equal(slug('Đinh lăng'), 'dinh-lang');
  assert.equal(repoName({ id: ID, name: '***' }), 'model-0f8fad5b');
});

test('names with contact details or abuse are not published', () => {
  assert.ok(namesAllowed(['Garden herbs', 'Các loại rau', 'Lon nước']));
  assert.ok(!namesAllowed(['Ask me@example.com']));
  assert.ok(!namesAllowed(['see www.shop.vn']));
  assert.ok(!namesAllowed(['đéo biết']));
});

test('the model card carries the licence, the species and the papers to cite', () => {
  const card = modelCard({ id: ID, name: 'Garden herbs', species: ['Mint', 'Basil'], backbone: 'mobilenet_v3_large', trainable: true });
  assert.match(card, /^---\nlicense: cc-by-4.0/);
  assert.match(card, /- Basil\n- Mint/);
  assert.match(card, /doi\.org\/10\.1016\/j\.procs\.2025\.09\.382/);
  assert.match(card, /Never eat a plant/);
});

test('the sharer publishes their model: repo, LFS upload, commit, then the listing says where', async () => {
  const w = world();
  const response = await call(w);
  const json = await response.json();

  assert.equal(response.status, 201);
  assert.equal(json.url, 'https://huggingface.co/med-herb-lens/garden-herbs-0f8fad5b');
  const create = w.calls.find((c) => c.url.endsWith('/api/repos/create'));
  assert.deepEqual(JSON.parse(create.init.body), { type: 'model', name: 'garden-herbs-0f8fad5b', organization: 'med-herb-lens', visibility: 'public' });
  assert.equal(create.init.headers.authorization, 'Bearer hf_secret');
  assert.ok(w.calls.some((c) => c.url === 'https://s3.example/put' && c.method === 'PUT'));
  assert.ok(w.calls.some((c) => c.url === 'https://hub.example/verify'));
  const commit = w.calls.find((c) => c.url.endsWith('/api/models/med-herb-lens/garden-herbs-0f8fad5b/commit/main'));
  const lines = commit.init.body.trim().split('\n').map((l) => JSON.parse(l));
  assert.deepEqual(lines.map((l) => l.key), ['header', 'file', 'lfsFile']);
  assert.deepEqual(lines[2].value, { path: 'model.tflite', algo: 'sha256', oid: SHA, size: FILE.length });
  assert.equal(w.doc.huggingFace.stringValue, 'published');
  assert.equal(w.doc.huggingFaceUrl.stringValue, json.url);
});

test('a large file goes up in parts, then the upload is completed', async () => {
  const w = world({ chunked: true });
  assert.equal((await call(w)).status, 201);

  assert.ok(w.calls.some((c) => c.url === 'https://s3.example/part1') && w.calls.some((c) => c.url === 'https://s3.example/part2'));
  const completion = w.calls.find((c) => c.url === 'https://hub.example/complete');
  assert.deepEqual(JSON.parse(completion.init.body), { oid: SHA, parts: [{ partNumber: 1, etag: '"etag-1"' }, { partNumber: 2, etag: '"etag-2"' }] });
});

test('only the sharer, only when asked, and only names that pass', async () => {
  assert.equal((await call(world(), { sub: 'someone-else' })).status, 403);
  assert.equal((await call(world({ huggingFace: 'none' }))).status, 409);
  assert.equal((await call(world(), { body: {} })).status, 400);
  assert.equal((await call(world({ hfToken: null }))).status, 503);

  const rude = world({ name: 'shit plants' });
  assert.equal((await call(rude)).status, 422);
  assert.equal(rude.doc.huggingFace.stringValue, 'declined');
  assert.ok(!rude.calls.some((c) => c.url.includes('huggingface.co')));
});

test('stopping sharing removes the Hugging Face copy, the file and the listing', async () => {
  const w = world({ huggingFace: 'published' });
  w.doc.huggingFaceUrl = { stringValue: 'https://huggingface.co/med-herb-lens/garden-herbs-0f8fad5b' };

  const response = await call(w, { method: 'DELETE', path: `/models/${ID}` });

  assert.equal(response.status, 204);
  const removed = w.calls.find((c) => c.url === 'https://huggingface.co/api/repos/delete');
  assert.deepEqual(JSON.parse(removed.init.body), { type: 'model', name: 'garden-herbs-0f8fad5b', organization: 'med-herb-lens' });
  assert.equal(w.stored.size, 0);
  assert.equal(w.docExists(), false);
});
