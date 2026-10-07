import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { addDoc, collection, deleteDoc, deleteField, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-herb-lens',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'herbs/3035652'), {
      viName: 'Đinh lăng (old field)',
      images: { 'https://r2/photos/3035652/a.jpg': '{"uid":"alice"}' },
    });
    await setDoc(doc(db, 'bannedUsers/mallory'), {});
    await setDoc(doc(db, 'users/alice'), { favorite: [1], history: [2], isAdmin: false });
    await setDoc(doc(db, 'config/mobile'), { mustUpdateAndroid: true });
  });
});

const as = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();
const photo = (n) => `https://r2/photos/3035652/${n}.jpg`;
const addPhotos = (db, path, entries) => setDoc(doc(db, path), { images: entries }, { merge: true });

test('anyone can read a species’ photos', async () => {
  await assertSucceeds(getDoc(doc(as(null), 'herbs/3035652')));
});

test('a signed-in user can add photos to a species', async () => {
  await assertSucceeds(addPhotos(as('bob'), 'herbs/3035652', { [photo('b')]: '{"uid":"bob"}' }));
});

test('the first photos of a species create its document', async () => {
  await assertSucceeds(addPhotos(as('bob'), 'herbs/2766278', { [photo('c')]: '{"uid":"bob"}' }));
});

test('photos can’t be removed, changed, or sent without signing in', async () => {
  const bob = as('bob');
  await assertFails(updateDoc(doc(bob, 'herbs/3035652'), { [`images.${'x'}`]: deleteField(), images: {} }));
  await assertFails(addPhotos(bob, 'herbs/3035652', { [photo('a')]: '{"uid":"bob"}' }));
  await assertFails(addPhotos(as(null), 'herbs/3035652', { [photo('b')]: '{"uid":"anon"}' }));
});

test('no more than 20 photos per write', async () => {
  const many = Object.fromEntries(Array.from({ length: 21 }, (_, i) => [photo(`m${i}`), '{"uid":"bob"}']));
  await assertFails(addPhotos(as('bob'), 'herbs/3035652', many));
});

test('only the images field can be written', async () => {
  const bob = as('bob');
  await assertFails(setDoc(doc(bob, 'herbs/3035652'), { viName: 'Vandalised' }, { merge: true }));
  await assertFails(setDoc(doc(bob, 'herbs/123'), { images: { [photo('d')]: 'x' }, viName: 'x' }));
  await assertFails(addPhotos(bob, 'herbs/not-a-key', { [photo('e')]: 'x' }));
});

test('banned users can’t contribute', async () => {
  const mallory = as('mallory');
  await assertFails(addPhotos(mallory, 'herbs/3035652', { [photo('z')]: '{"uid":"mallory"}' }));
  await assertFails(addDoc(collection(mallory, 'nameSuggestions'), {
    speciesKey: 3035652, viName: 'x', uid: 'mallory', createdAt: serverTimestamp(),
  }));
});

test('name suggestions: well-formed creates only, never read back', async () => {
  const bob = as('bob');
  const ok = { speciesKey: 3035652, viName: 'Cây gỏi cá', uid: 'bob', createdAt: serverTimestamp() };
  const created = await assertSucceeds(addDoc(collection(bob, 'nameSuggestions'), ok));
  await assertFails(getDoc(created));
  await assertFails(addDoc(collection(bob, 'nameSuggestions'), { ...ok, uid: 'alice' }));
  await assertFails(addDoc(collection(bob, 'nameSuggestions'), { ...ok, viName: '' }));
  await assertFails(addDoc(collection(bob, 'nameSuggestions'), { ...ok, viName: 'x'.repeat(101) }));
  await assertFails(addDoc(collection(bob, 'nameSuggestions'), { ...ok, speciesKey: '3035652' }));
  await assertFails(addDoc(collection(bob, 'nameSuggestions'), { ...ok, extra: true }));
  await assertFails(addDoc(collection(bob, 'nameSuggestions'), { ...ok, createdAt: new Date(0) }));
  await assertFails(addDoc(collection(as(null), 'nameSuggestions'), ok));
});

test('users read only their own document', async () => {
  await assertSucceeds(getDoc(doc(as('alice'), 'users/alice')));
  await assertFails(getDoc(doc(as('bob'), 'users/alice')));
});

const withEmail = (uid, email) => env.authenticatedContext(uid, { email }).firestore();

test('users keep their own profile: uid, name and their sign-in email only', async () => {
  const alice = withEmail('alice', 'alice@example.org');
  await assertSucceeds(setDoc(doc(alice, 'users/alice'), { uid: 'alice', name: 'Alice', email: 'alice@example.org' }, { merge: true }));
  await assertSucceeds(getDoc(doc(alice, 'users/alice')));
  // An Apple user shares neither name nor email
  await assertSucceeds(setDoc(doc(as('carol'), 'users/carol'), { uid: 'carol' }, { merge: true }));

  await assertFails(setDoc(doc(alice, 'users/alice'), { email: 'someone@else.org' }, { merge: true }));
  await assertFails(setDoc(doc(alice, 'users/alice'), { favorite: [9] }, { merge: true }));
  await assertFails(setDoc(doc(alice, 'users/alice'), { isAdmin: true }, { merge: true }));
  await assertFails(setDoc(doc(alice, 'users/bob'), { uid: 'bob' }, { merge: true }));
  await assertFails(setDoc(doc(as('dave'), 'users/dave'), { uid: 'mallory' }));
});

test('users delete only their own document, with their account', async () => {
  await assertFails(deleteDoc(doc(as('bob'), 'users/alice')));
  await assertFails(deleteDoc(doc(as(null), 'users/alice')));
  await assertSucceeds(deleteDoc(doc(as('alice'), 'users/alice')));
});

test('signed-in users can report a photo; only well-formed reports, never read back', async () => {
  const report = (reporterUid) => ({
    speciesKey: 3035652, url: photo('a'), uploaderId: 'bob', reason: 'SEXUAL_OR_VIOLENT', reporterUid, createdAt: serverTimestamp(),
  });
  await assertFails(addDoc(collection(as(null), 'photoReports'), report(null)));
  const created = await assertSucceeds(addDoc(collection(as('carol'), 'photoReports'), report('carol')));
  await assertFails(getDoc(created));
  await assertFails(addDoc(collection(as('carol'), 'photoReports'), report(null)));
  await assertFails(addDoc(collection(as('carol'), 'photoReports'), report('dave')));
  await assertFails(addDoc(collection(as('mallory'), 'photoReports'), report('mallory')));
  await assertFails(addDoc(collection(as('carol'), 'photoReports'), { ...report('carol'), reason: 'BORED' }));
  await assertFails(addDoc(collection(as('carol'), 'photoReports'), { ...report('carol'), extra: 1 }));
});

test('contributors share models as themselves; anyone browses; only the uploader removes', async () => {
  const id = '0f8fad5b-d9cb-469f-a165-70867728950e';
  const model = (uploaderId, extra = {}) => ({
    name: 'Garden herbs', species: ['Mint', 'Basil'], backbone: 'mobilenet_v3_large', trainable: true,
    url: `https://pub-abc.r2.dev/models/${id}.tflite`, size: 12000000, uploaderId, license: 'CC-BY-4.0',
    createdAt: serverTimestamp(), ...extra,
  });
  const ref = (who) => doc(as(who), `sharedModels/${id}`);

  await assertFails(setDoc(ref(null), model(null)));
  await assertFails(setDoc(ref('carol'), model('dave')));
  await assertFails(setDoc(ref('mallory'), model('mallory')));
  await assertFails(setDoc(ref('carol'), model('carol', { url: 'https://evil.example/x.tflite' })));
  await assertFails(setDoc(ref('carol'), model('carol', { size: 30000000 })));
  await assertFails(setDoc(ref('carol'), model('carol', { species: ['Only one'] })));
  await assertFails(setDoc(ref('carol'), model('carol', { license: 'proprietary' })));
  await assertFails(setDoc(ref('carol'), model('carol', { extra: 1 })));
  await assertFails(setDoc(doc(as('carol'), 'sharedModels/not-a-uuid'), model('carol')));
  await assertFails(setDoc(ref('carol'), model('carol', { huggingFace: 'published' })));
  await assertSucceeds(setDoc(ref('carol'), model('carol', { huggingFace: 'requested' })));

  await assertSucceeds(getDoc(ref(null)));
  await assertSucceeds(getDocs(collection(as(null), 'sharedModels')));
  await assertFails(updateDoc(ref('carol'), { name: 'Renamed' }));
  await assertFails(deleteDoc(ref('dave')));
  await assertSucceeds(deleteDoc(ref('carol')));
});

test('signed-in users can report a shared model, never read back', async () => {
  const report = (reporterUid) => ({ modelId: 'm1', uploaderId: 'bob', reason: 'OFFENSIVE', reporterUid, createdAt: serverTimestamp() });
  await assertFails(addDoc(collection(as(null), 'modelReports'), report(null)));
  const created = await assertSucceeds(addDoc(collection(as('carol'), 'modelReports'), report('carol')));
  await assertFails(getDoc(created));
  await assertFails(addDoc(collection(as('carol'), 'modelReports'), report('dave')));
  await assertFails(addDoc(collection(as('carol'), 'modelReports'), { ...report('carol'), reason: 'BORED' }));
});

test('old app versions can still read config/mobile, nobody can write it', async () => {
  await assertSucceeds(getDoc(doc(as(null), 'config/mobile')));
  await assertFails(setDoc(doc(as('bob'), 'config/mobile'), { mustUpdateAndroid: false }));
});

test('everything else is closed', async () => {
  await assertFails(getDoc(doc(as('bob'), 'bannedUsers/mallory')));
  await assertFails(setDoc(doc(as('bob'), 'deletions/x'), { a: 1 }));
  await assertFails(getDoc(doc(as('bob'), 'uploads/x')));
});
