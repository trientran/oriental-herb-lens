// One-off: notes every photo, name suggestion and shared model shared before the functions were
// deployed on its contributor's record. Safe to run again: records already there are left alone.
//
//   export GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account.json
//   node backfill.js [--dry-run] [--model-url https://.../herb_model.tflite]
//
// Without --model-url, the herb model is the one Remote Config announces (herb_model_url).

import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { getRemoteConfig } from 'firebase-admin/remote-config';
import { addedPhotos, knownSpeciesAt, modelRecord, nameRecord, photoRecords, writeRecords } from './contributions.js';

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const modelUrlArg = args[args.indexOf('--model-url') + 1];

initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT ?? 'oriental-herb-lens-41d17' });
const db = getFirestore();
const auth = getAuth();

const modelUrl = args.includes('--model-url')
  ? modelUrlArg
  : (await getRemoteConfig().getTemplate()).parameters.herb_model_url?.defaultValue?.value;
const known = modelUrl ? await knownSpeciesAt(modelUrl) : new Set();
console.log(`Herb model knows ${known.size} species${modelUrl ? '' : ' (no model URL: no photo marked as needed)'}`);

// A record's time: the share's own where it has one, else (photos) when the species was first given one
const times = new Map();
const records = [];

for (const doc of (await db.collection('herbs').get()).docs) {
  const photos = addedPhotos({}, doc.get('images'));
  for (const record of photoRecords(doc.id, photos, known)) {
    times.set(record[1], doc.createTime);
    records.push(record);
  }
}
for (const doc of (await db.collection('nameSuggestions').get()).docs) {
  const record = nameRecord(doc.id, doc.data());
  if (record) { times.set(record[1], doc.get('createdAt') ?? doc.createTime); records.push(record); }
}
for (const doc of (await db.collection('sharedModels').get()).docs) {
  const record = modelRecord(doc.id, doc.data());
  if (record) { times.set(record[1], doc.get('createdAt') ?? doc.createTime); records.push(record); }
}

const count = (kind) => records.filter(([, , data]) => data.kind === kind).length;
console.log(`Found ${count('photo')} photos, ${count('name')} name suggestions, ${count('model')} shared models`);

if (dryRun) {
  console.log('Dry run: nothing written');
} else {
  const time = ([, id]) => times.get(id) ?? Timestamp.now();
  let written = 0;
  for (let i = 0; i < records.length; i += 200) {
    written += await writeRecords(db, auth, records.slice(i, i + 200), time);
  }
  console.log(`Wrote ${written} new records (the rest were there already, or their accounts are gone)`);
}
