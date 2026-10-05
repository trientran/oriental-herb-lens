import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { addDoc, collection, deleteDoc, deleteField, doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';

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

test('users read only their own document, and write none', async () => {
  await assertSucceeds(getDoc(doc(as('alice'), 'users/alice')));
  await assertFails(getDoc(doc(as('bob'), 'users/alice')));
  await assertFails(setDoc(doc(as('alice'), 'users/alice'), { favorite: [9] }, { merge: true }));
});

test('users delete only their own document, with their account', async () => {
  await assertFails(deleteDoc(doc(as('bob'), 'users/alice')));
  await assertFails(deleteDoc(doc(as(null), 'users/alice')));
  await assertSucceeds(deleteDoc(doc(as('alice'), 'users/alice')));
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
