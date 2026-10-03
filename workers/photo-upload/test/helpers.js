// Signs test ID tokens with a locally generated RSA key, standing in for Google's.
export const PROJECT = 'oriental-herb-lens-41d17';

const keyPair = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
  true,
  ['sign', 'verify'],
);
export const publicJwk = { ...(await crypto.subtle.exportKey('jwk', keyPair.publicKey)), kid: 'test-key' };
export const getKeys = async () => [publicJwk];

const encode = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');

export async function signToken(claims = {}, header = {}) {
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    aud: PROJECT,
    iss: `https://securetoken.google.com/${PROJECT}`,
    sub: 'uid-123',
    iat: now - 10,
    auth_time: now - 10,
    exp: now + 3600,
    ...claims,
  };
  const head = encode({ alg: 'RS256', kid: 'test-key', typ: 'JWT', ...header });
  const body = encode(payload);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', keyPair.privateKey, new TextEncoder().encode(`${head}.${body}`));
  return `${head}.${body}.${Buffer.from(signature).toString('base64url')}`;
}
