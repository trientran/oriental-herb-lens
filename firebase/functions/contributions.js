// What a contributor shared, noted on their own record (users/{uid}/contributions) by the server,
// so shares from every app version count. Each record's id comes from what was shared, so a
// trigger that runs twice, or the backfill after it, writes it once.

import { createHash } from 'node:crypto';

/** The uploader of a photo from its `images` value: JSON `{"uid", ...}`, or the bare uid of older uploads. */
export function uploaderOf(detail) {
  if (typeof detail !== 'string') return null;
  const text = detail.trim();
  if (text.startsWith('{')) {
    try {
      const uid = JSON.parse(text).uid;
      return typeof uid === 'string' && uid ? uid : null;
    } catch {
      return null;
    }
  }
  return text || null;
}

/** Photos in `after` that weren't in `before` (two `images` maps), with their uploaders. */
export function addedPhotos(before, after) {
  const old = before ?? {};
  return Object.entries(after ?? {})
    .filter(([url]) => !(url in old))
    .map(([url, detail]) => ({ url, uid: uploaderOf(detail) }))
    .filter((photo) => photo.uid);
}

export function photoRecordId(url) {
  return 'photo_' + createHash('sha256').update(url).digest('hex').slice(0, 40);
}

/** The record documents, as [uid, id, data] without createdAt; the caller adds the time. */
export function photoRecords(speciesKey, photos, known) {
  const key = Number(speciesKey);
  if (!Number.isSafeInteger(key)) return [];
  // Unknown when the model's labels can't be read: then no photo is marked as filling a gap
  const needed = known.size > 0 && !known.has(key);
  return photos.map(({ url, uid }) => [
    uid, photoRecordId(url), { kind: 'photo', speciesKey: key, url, modelId: null, neededPhotos: needed },
  ]);
}

export function nameRecord(id, suggestion) {
  const uid = suggestion?.uid;
  const key = Number(suggestion?.speciesKey);
  if (typeof uid !== 'string' || !uid || !Number.isSafeInteger(key)) return null;
  return [uid, `name_${id}`, { kind: 'name', speciesKey: key, url: null, modelId: null, neededPhotos: false }];
}

export function modelRecord(id, model) {
  const uid = model?.uploaderId;
  if (typeof uid !== 'string' || !uid) return null;
  return [uid, `model_${id}`, { kind: 'model', speciesKey: null, url: null, modelId: id, neededPhotos: false }];
}

/**
 * Reads an uncompressed ("stored") entry, such as labels.txt, from the zip archive TFLite metadata
 * appends to a model. `tail` is the end of the file, starting at byte `tailOffset`. Null when the
 * entry isn't there, is compressed, or starts before `tail`. The app's storedZipEntry, in JS.
 */
export function storedZipEntry(tail, tailOffset, name) {
  const u16 = (at) => tail.readUInt16LE(at);
  const u32 = (at) => tail.readUInt32LE(at);
  const local = (absolute) => {
    const at = absolute - tailOffset;
    return at >= 0 && at < tail.length ? at : null;
  };
  if (tail.length < 22) return null;
  let end = -1;
  for (let at = tail.length - 22; at >= Math.max(0, tail.length - 65_557); at--) {
    if (u32(at) === 0x06054b50) { end = at; break; }
  }
  if (end < 0) return null;
  let entry = local(u32(end + 16));
  if (entry === null) return null;
  for (let i = 0; i < u16(end + 10); i++) {
    if (entry + 46 > tail.length) return null;
    const nameLength = u16(entry + 28);
    const entryName = tail.toString('utf8', entry + 46, entry + 46 + nameLength);
    if (entryName.replace(/^\/+/, '') === name) {
      if (u16(entry + 10) !== 0) return null;
      const size = u32(entry + 20);
      const header = local(u32(entry + 42));
      if (header === null) return null;
      const start = header + 30 + u16(header + 26) + u16(header + 28);
      if (start + size > tail.length) return null;
      return tail.toString('utf8', start, start + size);
    }
    entry += 46 + nameLength + u16(entry + 30) + u16(entry + 32);
  }
  return null;
}

/** The species keys in a model's labels.txt: one per line, other lines ignored. */
export function speciesKeysOf(labels) {
  const keys = new Set();
  for (const line of (labels ?? '').split('\n')) {
    const text = line.trim();
    if (/^[0-9]{1,15}$/.test(text)) keys.add(Number(text));
  }
  return keys;
}

/** How much of the model's end to read for its labels, as the app does. */
export const LABELS_TAIL_BYTES = 1024 * 1024;

/** The species the published herb model knows, read from the end of the file at `url`. */
export async function knownSpeciesAt(url, fetchImpl = fetch) {
  const response = await fetchImpl(url, { headers: { Range: `bytes=-${LABELS_TAIL_BYTES}` } });
  if (!response.ok) throw new Error(`Model labels: HTTP ${response.status}`);
  const tail = Buffer.from(await response.arrayBuffer());
  // 206 says where the part starts; a server that ignores Range sends the whole file (200)
  const range = /bytes (\d+)-/.exec(response.headers.get('content-range') ?? '');
  const offset = response.status === 206 && range ? Number(range[1]) : 0;
  return speciesKeysOf(storedZipEntry(tail, offset, 'labels.txt'));
}

/** The uids among `uids` that still have an account: a deleted account gets no record back. */
export async function existingAccounts(auth, uids) {
  const unique = [...new Set(uids)];
  const found = new Set();
  for (let i = 0; i < unique.length; i += 100) {
    const { users } = await auth.getUsers(unique.slice(i, i + 100).map((uid) => ({ uid })));
    users.forEach((user) => found.add(user.uid));
  }
  return found;
}

const ALREADY_EXISTS = 6;

/**
 * Writes each record once, for accounts that exist: a retried trigger, or the backfill, finds it
 * there. `createdAt(record)` gives each record's time. Returns how many were new.
 */
export async function writeRecords(firestore, auth, records, createdAt) {
  const accounts = await existingAccounts(auth, records.map(([uid]) => uid));
  let written = 0;
  await Promise.all(records.filter(([uid]) => accounts.has(uid)).map(async (record) => {
    const [uid, id, data] = record;
    try {
      await firestore.doc(`users/${uid}/contributions/${id}`).create({ ...data, createdAt: createdAt(record) });
      written++;
    } catch (e) {
      if (e.code !== ALREADY_EXISTS) throw e;
    }
  }));
  return written;
}
