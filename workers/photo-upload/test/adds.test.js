import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { TOKEN_URL, clearTokenCache } from '../src/bans.js';
import { handleRequest } from '../src/index.js';
import { verifyIdToken } from '../src/firebase-auth.js';
import { PROJECT, getKeys, signToken } from './helpers.js';

const keyPair = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify'],
);
const pkcs8 = Buffer.from(await crypto.subtle.exportKey('pkcs8', keyPair.privateKey)).toString('base64');
const verify = (token, project) => verifyIdToken(token, project, { getKeys });
const ID = '0f8fad5b-d9cb-469f-a165-70867728950e';

beforeEach(clearTokenCache);

/** A fake Firestore holding one shared model, its count and the hashes of who added it. */
function world({ listed = true, account = true } = {}) {
  const adders = new Set();
  let adds = 0;
  const commits = [];
  const fetchFn = async (url, init = {}) => {
    if (url === TOKEN_URL) return Response.json({ access_token: 'google-token', expires_in: 3600 });
    if (String(url).endsWith(`sharedModels/${ID}`)) {
      return listed ? Response.json({ fields: { name: { stringValue: 'Garden herbs' }, uploaderId: { stringValue: 'sharer' } } }) : new Response('', { status: 404 });
    }
    if (String(url).endsWith('/documents:commit')) {
      const { writes } = JSON.parse(init.body);
      commits.push(writes);
      const adder = writes[0].update.name.split('/').pop();
      if (adders.has(adder)) return Response.json({ error: { code: 400, status: 'FAILED_PRECONDITION' } }, { status: 400 });
      adders.add(adder);
      adds += Number(writes[1].transform.fieldTransforms[0].increment.integerValue);
      return Response.json({ writeResults: [{}, {}] });
    }
    throw new Error(`unexpected ${url}`);
  };
  const env = {
    FIREBASE_PROJECT_ID: PROJECT,
    FIREBASE_SERVICE_ACCOUNT: account
      ? JSON.stringify({ client_email: 'w@x.iam.gserviceaccount.com', private_key: `-----BEGIN PRIVATE KEY-----\n${pkcs8}\n-----END PRIVATE KEY-----\n` })
      : undefined,
  };
  return { env, fetchFn, commits, count: () => adds };
}

async function add(w, sub, method = 'POST') {
  const request = new Request(`https://worker.example/models/${ID}/added`, { method, headers: { authorization: `Bearer ${await signToken({ sub })}` } });
  return handleRequest(request, w.env, verify, async () => false, w.fetchFn);
}

test('each person who adds a shared model counts once', async () => {
  const w = world();
  assert.equal((await add(w, 'alice')).status, 201);
  assert.deepEqual(await (await add(w, 'alice')).json(), { counted: false });
  assert.equal((await add(w, 'bob')).status, 201);
  assert.equal(w.count(), 2);
});

test('the sharer adding their own model does not count', async () => {
  const w = world();
  assert.deepEqual(await (await add(w, 'sharer')).json(), { counted: false });
  assert.equal(w.commits.length, 0);
});

test('who added it is kept only as a hash, with the count in the same commit', async () => {
  const w = world();
  await add(w, 'alice');
  const [mark, count] = w.commits[0];
  assert.match(mark.update.name, /\/sharedModels\/0f8fad5b-d9cb-469f-a165-70867728950e\/adders\/[0-9a-f]{64}$/);
  assert.ok(!mark.update.name.includes('alice'));
  assert.deepEqual(mark.currentDocument, { exists: false });
  assert.deepEqual(count.currentDocument, { exists: true });
});

test('a model no longer listed, a wrong method or no service account is refused', async () => {
  assert.equal((await add(world({ listed: false }), 'alice')).status, 404);
  assert.equal((await add(world(), 'alice', 'DELETE')).status, 405);
  assert.equal((await add(world({ account: false }), 'alice')).status, 503);
});
