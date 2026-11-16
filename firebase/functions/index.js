// Cloud Functions: notes each photo, name suggestion and shared model on its contributor's own
// record, whichever app version shared it (docs/contribution-incentives.md).

import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { getRemoteConfig } from 'firebase-admin/remote-config';
import { setGlobalOptions } from 'firebase-functions/v2';
import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { logger } from 'firebase-functions';
import { addedPhotos, knownSpeciesAt, modelRecord, nameRecord, photoRecords, writeRecords } from './contributions.js';

initializeApp();
// Firestore triggers run where the database is
setGlobalOptions({ region: 'asia-southeast2', maxInstances: 5, memory: '256MiB' });

const db = getFirestore();
const auth = getAuth();
const save = (records) => writeRecords(db, auth, records, () => FieldValue.serverTimestamp());

// The published herb model's species, read again when Remote Config points at a new file
let known = { url: null, keys: new Set(), at: 0 };
const KNOWN_FOR_MS = 60 * 60 * 1000;

async function knownSpecies() {
  try {
    if (Date.now() - known.at < KNOWN_FOR_MS) return known.keys;
    const template = await getRemoteConfig().getTemplate();
    const url = template.parameters.herb_model_url?.defaultValue?.value;
    if (!url) return new Set();
    const keys = url === known.url ? known.keys : await knownSpeciesAt(url);
    known = { url, keys, at: Date.now() };
    return keys;
  } catch (e) {
    logger.warn('Herb model labels unavailable', e);
    return known.keys;
  }
}

export const recordPhotos = onDocumentWritten('herbs/{speciesKey}', async (event) => {
  const photos = addedPhotos(event.data?.before?.data()?.images, event.data?.after?.data()?.images);
  if (photos.length === 0) return;
  const records = photoRecords(event.params.speciesKey, photos, await knownSpecies());
  await save(records);
});

export const recordNameSuggestion = onDocumentCreated('nameSuggestions/{id}', async (event) => {
  const record = nameRecord(event.params.id, event.data?.data());
  if (record) await save([record]);
});

export const recordSharedModel = onDocumentCreated('sharedModels/{id}', async (event) => {
  const record = modelRecord(event.params.id, event.data?.data());
  if (record) await save([record]);
});
