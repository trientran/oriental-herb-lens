import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { TOKEN_URL, clearTokenCache, isBanned } from '../src/bans.js';

const keyPair = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
  true,
  ['sign', 'verify'],
);
const pkcs8 = Buffer.from(await crypto.subtle.exportKey('pkcs8', keyPair.privateKey)).toString('base64');
const env = {
  FIREBASE_PROJECT_ID: 'oriental-herb-lens-41d17',
  FIREBASE_SERVICE_ACCOUNT: JSON.stringify({
    client_email: 'worker@oriental-herb-lens-41d17.iam.gserviceaccount.com',
    private_key: `-----BEGIN PRIVATE KEY-----\n${pkcs8}\n-----END PRIVATE KEY-----\n`,
  }),
};

/** Google's token endpoint and Firestore, with `banned` uids existing in bannedUsers. */
function fakeGoogle(banned) {
  const calls = [];
  const fetchFn = async (url, init = {}) => {
    calls.push(url);
    if (url === TOKEN_URL) {
      const assertion = new URLSearchParams(init.body).get('assertion');
      const [head, body, signature] = assertion.split('.');
      const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', keyPair.publicKey, Buffer.from(signature, 'base64url'), new TextEncoder().encode(`${head}.${body}`));
      const claims = JSON.parse(Buffer.from(body, 'base64url'));
      assert.ok(valid);
      assert.equal(claims.scope, 'https://www.googleapis.com/auth/datastore');
      return Response.json({ access_token: 'access-1', expires_in: 3600 });
    }
    assert.equal(init.headers.authorization, 'Bearer access-1');
    const uid = decodeURIComponent(url.split('/').pop());
    return banned.includes(uid) ? Response.json({ name: uid }) : new Response('{}', { status: 404 });
  };
  return { fetchFn, calls };
}

beforeEach(clearTokenCache);

test('a banned uid is found, others are not', async () => {
  const { fetchFn, calls } = fakeGoogle(['mallory']);
  assert.equal(await isBanned('mallory', env, fetchFn), true);
  assert.equal(await isBanned('alice', env, fetchFn), false);
  assert.match(calls[1], /documents\/bannedUsers\/mallory$/);
  assert.equal(calls.filter((url) => url === TOKEN_URL).length, 1); // the access token is reused
});

test('without a service account nothing is checked', async () => {
  assert.equal(await isBanned('mallory', { FIREBASE_PROJECT_ID: 'p' }, async () => assert.fail('no fetch')), false);
});

test('a Firestore error is reported, not taken as "not banned"', async () => {
  const fetchFn = async (url) => (url === TOKEN_URL ? Response.json({ access_token: 'access-1', expires_in: 3600 }) : new Response('', { status: 500 }));
  await assert.rejects(isBanned('mallory', env, fetchFn));
});
