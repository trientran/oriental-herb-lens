import assert from 'node:assert/strict';
import { test } from 'node:test';
import { AuthError, verifyIdToken } from '../src/firebase-auth.js';
import { PROJECT, getKeys, signToken } from './helpers.js';

const verify = (token) => verifyIdToken(token, PROJECT, { getKeys });

test('a valid token yields the uid', async () => {
  assert.equal(await verify(await signToken()), 'uid-123');
});

test('rejects tokens for another project, expired or from the future', async () => {
  const now = Math.floor(Date.now() / 1000);
  for (const claims of [
    { aud: 'other-project' },
    { iss: 'https://securetoken.google.com/other-project' },
    { exp: now - 1 },
    { iat: now + 3600 },
    { auth_time: now + 3600 },
    { sub: '' },
  ]) {
    await assert.rejects(verify(await signToken(claims)), AuthError, JSON.stringify(claims));
  }
});

test('rejects a tampered payload', async () => {
  const [head, , signature] = (await signToken()).split('.');
  const forged = Buffer.from(JSON.stringify({ aud: PROJECT, iss: `https://securetoken.google.com/${PROJECT}`, sub: 'admin', iat: 0, exp: 9e9 })).toString('base64url');
  await assert.rejects(verify(`${head}.${forged}.${signature}`), AuthError);
});

test('rejects unknown keys, other algorithms and garbage', async () => {
  await assert.rejects(verify(await signToken({}, { kid: 'nope' })), AuthError);
  await assert.rejects(verify(await signToken({}, { alg: 'HS256' })), AuthError);
  await assert.rejects(verify('not.a.token'), AuthError);
  await assert.rejects(verify(undefined), AuthError);
});
