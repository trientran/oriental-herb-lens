import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  addedPhotos, knownSpeciesAt, modelRecord, nameRecord, photoRecordId, photoRecords, speciesKeysOf,
  storedZipEntry, uploaderOf, writeRecords,
} from '../contributions.js';

// A zip of uncompressed entries, as TFLite metadata appends to a model, after `prefix` bytes of "model"
function modelWith(entries, prefix = 1000) {
  const parts = [Buffer.alloc(prefix, 7)];
  const central = [];
  let offset = prefix;
  for (const [name, text] of Object.entries(entries)) {
    const nameBytes = Buffer.from(name);
    const data = Buffer.from(text);
    const header = Buffer.alloc(30);
    header.writeUInt32LE(0x04034b50, 0);
    header.writeUInt32LE(data.length, 18);
    header.writeUInt32LE(data.length, 22);
    header.writeUInt16LE(nameBytes.length, 26);
    parts.push(header, nameBytes, data);
    const entry = Buffer.alloc(46);
    entry.writeUInt32LE(0x02014b50, 0);
    entry.writeUInt32LE(data.length, 20);
    entry.writeUInt32LE(data.length, 24);
    entry.writeUInt16LE(nameBytes.length, 28);
    entry.writeUInt32LE(offset, 42);
    central.push(entry, nameBytes);
    offset += 30 + nameBytes.length + data.length;
  }
  const directory = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(Object.keys(entries).length, 8);
  end.writeUInt16LE(Object.keys(entries).length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...parts, directory, end]);
}

test('a photo uploader is read from JSON details and from older bare uids', () => {
  assert.equal(uploaderOf('{"uid":"u1","lat":1,"lng":2,"name":"Lan"}'), 'u1');
  assert.equal(uploaderOf('u2'), 'u2');
  assert.equal(uploaderOf('{not json'), null);
  assert.equal(uploaderOf('{"lat":1}'), null);
  assert.equal(uploaderOf(''), null);
  assert.equal(uploaderOf(7), null);
});

test('only photos added by this write are recorded', () => {
  const before = { 'https://a/1.jpg': 'u1' };
  const after = { 'https://a/1.jpg': 'u1', 'https://a/2.jpg': '{"uid":"u2"}', 'https://a/3.jpg': '' };
  assert.deepEqual(addedPhotos(before, after), [{ url: 'https://a/2.jpg', uid: 'u2' }]);
  assert.deepEqual(addedPhotos(undefined, { 'https://a/1.jpg': 'u1' }), [{ url: 'https://a/1.jpg', uid: 'u1' }]);
  assert.deepEqual(addedPhotos(after, undefined), []);
});

test('a photo is marked as needed when the model does not know its species', () => {
  const photos = [{ url: 'https://a/1.jpg', uid: 'u1' }];
  const [[uid, id, data]] = photoRecords('42', photos, new Set([7]));
  assert.equal(uid, 'u1');
  assert.equal(id, photoRecordId('https://a/1.jpg'));
  assert.deepEqual(data, { kind: 'photo', speciesKey: 42, url: 'https://a/1.jpg', modelId: null, neededPhotos: true });
  assert.equal(photoRecords('7', photos, new Set([7]))[0][2].neededPhotos, false);
  // Labels unknown: nothing is marked
  assert.equal(photoRecords('42', photos, new Set())[0][2].neededPhotos, false);
  assert.deepEqual(photoRecords('not-a-key', photos, new Set()), []);
});

test('record ids are fixed by what was shared', () => {
  assert.equal(photoRecordId('https://a/1.jpg'), photoRecordId('https://a/1.jpg'));
  assert.notEqual(photoRecordId('https://a/1.jpg'), photoRecordId('https://a/2.jpg'));
  assert.match(photoRecordId('https://a/1.jpg'), /^photo_[0-9a-f]{40}$/);
  assert.deepEqual(nameRecord('s1', { uid: 'u1', speciesKey: 42, language: 'en', name: 'Lotus' }),
    ['u1', 'name_s1', { kind: 'name', speciesKey: 42, url: null, modelId: null, neededPhotos: false }]);
  assert.equal(nameRecord('s1', { speciesKey: 42 }), null);
  assert.deepEqual(modelRecord('m1', { uploaderId: 'u1' }),
    ['u1', 'model_m1', { kind: 'model', speciesKey: null, url: null, modelId: 'm1', neededPhotos: false }]);
  assert.equal(modelRecord('m1', {}), null);
});

test('labels are read from the end of a model file', () => {
  const model = modelWith({ 'other.txt': 'x', 'labels.txt': '42\n7\nbackground\n' });
  assert.equal(storedZipEntry(model, 0, 'labels.txt'), '42\n7\nbackground\n');
  // Only the tail, starting part-way into the file
  assert.equal(storedZipEntry(model.subarray(900), 900, 'labels.txt'), '42\n7\nbackground\n');
  assert.equal(storedZipEntry(model, 0, 'missing.txt'), null);
  assert.equal(storedZipEntry(Buffer.alloc(100), 0, 'labels.txt'), null);
  assert.deepEqual(speciesKeysOf('42\n 7 \nbackground\n\n'), new Set([42, 7]));
});

test('the published model is read with a range request', async () => {
  const model = modelWith({ 'labels.txt': '42\n7\n' }, 5000);
  const fetchTail = async (url, init) => {
    assert.equal(init.headers.Range, 'bytes=-1048576');
    const start = Math.max(0, model.length - 1048576);
    return new Response(model.subarray(start), {
      status: 206,
      headers: { 'content-range': `bytes ${start}-${model.length - 1}/${model.length}` },
    });
  };
  assert.deepEqual(await knownSpeciesAt('https://r2/model.tflite', fetchTail), new Set([42, 7]));
  const whole = async () => new Response(model, { status: 200 });
  assert.deepEqual(await knownSpeciesAt('https://r2/model.tflite', whole), new Set([42, 7]));
  await assert.rejects(knownSpeciesAt('https://r2/x', async () => new Response('', { status: 404 })));
});

test('records are written once, and only for accounts that exist', async () => {
  const stored = new Map();
  const firestore = {
    doc: (path) => ({
      create: async (data) => {
        if (stored.has(path)) throw Object.assign(new Error('exists'), { code: 6 });
        stored.set(path, data);
      },
    }),
  };
  const auth = { getUsers: async (ids) => ({ users: ids.filter(({ uid }) => uid !== 'gone') }) };
  const records = [
    ['u1', 'name_a', { kind: 'name' }],
    ['gone', 'name_b', { kind: 'name' }],
  ];
  assert.equal(await writeRecords(firestore, auth, records, () => 'now'), 1);
  assert.deepEqual([...stored.keys()], ['users/u1/contributions/name_a']);
  assert.deepEqual(stored.get('users/u1/contributions/name_a'), { kind: 'name', createdAt: 'now' });
  // Again, as a retried trigger or the backfill would
  assert.equal(await writeRecords(firestore, auth, records, () => 'later'), 0);
  assert.equal(stored.get('users/u1/contributions/name_a').createdAt, 'now');
});
