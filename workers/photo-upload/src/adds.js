// How many people added a shared model (Profile shows its sharer the total). Counted here, with the
// service account, so a count can't be inflated from the app: once per person, never the sharer's
// own. Each person is remembered as a hash of their uid and the model's id, so the count keeps no
// one's identity.
//
//   POST /models/<uuid>/added    signed in: 201 {"counted": true}, or 200 {"counted": false} if already counted

import { firestoreCommit } from './bans.js';
import { sharedModel } from './huggingface.js';

async function adderKey(uid, id) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${uid}:${id}`));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function countAdd(env, id, uid, json, fetchFn = fetch) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) return json(503, { error: 'Counting is not set up' });
  const model = await sharedModel(env, id, fetchFn);
  if (!model) return json(404, { error: 'No such shared model' });
  if (model.uploaderId === uid) return json(200, { counted: false });
  const base = `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/sharedModels/${id}`;
  // Both or neither: the person's mark only if it isn't there yet, and the count only with it
  const response = await firestoreCommit(env, [
    { update: { name: `${base}/adders/${await adderKey(uid, id)}`, fields: {} }, currentDocument: { exists: false } },
    { transform: { document: base, fieldTransforms: [{ fieldPath: 'adds', increment: { integerValue: '1' } }] }, currentDocument: { exists: true } },
  ], fetchFn);
  if (response.ok) return json(201, { counted: true });
  const failure = await response.json().catch(() => ({}));
  if (failure?.error?.status === 'FAILED_PRECONDITION' || response.status === 409) return json(200, { counted: false });
  throw new Error(`Firestore commit: HTTP ${response.status}`);
}
