// Publishes shared models in the project's Hugging Face organisation, straight after sharing, and
// takes them down with the model. The write token is the Worker secret HF_TOKEN (never in an app):
//   npx wrangler secret put HF_TOKEN
// The steps follow huggingface_hub (create the repo, upload the file through the Git LFS batch API,
// then commit it with a model card).

import { firestoreRequest } from './bans.js';

const HUB = 'https://huggingface.co';
const LFS_HEADERS = { accept: 'application/vnd.git-lfs+json', 'content-type': 'application/vnd.git-lfs+json' };

export class HubError extends Error {}

const CITATIONS = [
  'Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2025). Med Herb Lens: A prototype AI app for '
    + 'medicinal plant identification. *Procedia Computer Science*, 270, 2603–2612. https://doi.org/10.1016/j.procs.2025.09.382',
  'Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2026). Resource-efficient continual learning for '
    + 'medicinal plant identification: A periodic retraining approach for edge-deployed agricultural IoT applications. '
    + '*IoT*, 7(3), 57. https://doi.org/10.3390/iot7030057',
];

/** A repository name: ASCII letters, digits and dashes ("Rau Hà Nội" -> "rau-ha-noi"). */
export function slug(text) {
  const folded = text.replace(/đ/g, 'd').replace(/Đ/g, 'D').normalize('NFKD').replace(/[^\x00-\x7f]/g, '');
  return folded.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 60) || 'model';
}

export function repoName(model) {
  return `${slug(model.name)}-${model.id.slice(0, 8)}`;
}

/** The same checks as the app's SharingRules, again on the server: names are published as typed. */
const BLOCKED = new Set(['fuck', 'fucking', 'fucker', 'shit', 'cunt', 'bitch', 'porn', 'porno', 'nazi', 'địt', 'lồn', 'cặc', 'đụ', 'đéo', 'buồi', 'đĩ']);
export function namesAllowed(texts) {
  return texts.every((text) => !/[^\s@]+@[^\s@]+\.[^\s@]+/.test(text)
    && !/(https?:\/\/|www\.)|\b[\w-]+\.(com|net|org|io|vn|edu|gov|info|me)\b/i.test(text)
    && !/\+?\d[\d .()-]{6,}\d/.test(text)
    && !text.toLowerCase().split(/[^\p{L}\p{N}]+/u).some((word) => BLOCKED.has(word)));
}

export function modelCard(model) {
  const species = [...model.species].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
  return [
    '---', 'license: cc-by-4.0', 'library_name: tflite', 'pipeline_tag: image-classification', 'tags:',
    '- med-herb-lens', '- plants', '- medicinal-plants', '- on-device', '- tflite', '---', '',
    `# ${model.name}`, '',
    'An image classifier trained by a [Med Herb Lens](https://med-herb-lens.pages.dev/about.html) user on their own '
      + 'device, and shared under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).', '',
    `## What it identifies (${species.length} species)`, '',
    ...species.map((name) => `- ${name}`), '',
    '## How to use it', '',
    `- **In Med Herb Lens:** download \`model.tflite\`, then Train → Import a model.${model.trainable
      ? ' Made in Med Herb Lens, it can go on learning there: add species or photos and train again.' : ''}`,
    '- **Anywhere TensorFlow Lite runs:** a 224 × 224 RGB image with values from 0 to 1 in; one probability per species '
      + 'out. The species list is inside the file (`labels.txt` in its metadata).', '',
    '## How it was made', '',
    `A final layer trained in Med Herb Lens, on the device, on top of the \`${model.backbone}\` MediaPipe image embedder (Apache 2.0).`, '',
    '## Caution', '',
    'Like any model, it can be wrong, and its species names were typed by its sharer. Never eat a plant or use it as '
      + 'medicine because of what a model says.', '',
    '## Credit and citation', '',
    `Shared by a Med Herb Lens user (shared model \`${model.id}\`). If you use it, please cite:`, '',
    ...CITATIONS.flatMap((c) => [c, '']),
  ].join('\n');
}

async function hub(fetchFn, url, init, what) {
  const response = await fetchFn(url, init);
  if (!response.ok) throw new HubError(`${what}: HTTP ${response.status} ${(await response.text()).slice(0, 200)}`);
  return response;
}

const base64 = (bytes) => {
  let text = '';
  for (let i = 0; i < bytes.length; i += 0x8000) text += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(text);
};

/** Creates (or reuses) the repository, uploads [bytes] as model.tflite with the model card, and returns its URL. */
export async function publish(env, model, bytes, sha256, fetchFn = fetch) {
  const org = env.HF_ORG || 'med-herb-lens';
  const name = repoName(model);
  const repo = `${org}/${name}`;
  const auth = { authorization: `Bearer ${env.HF_TOKEN}` };

  const created = await fetchFn(`${HUB}/api/repos/create`, {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify({ type: 'model', name, organization: org, visibility: 'public' }),
  });
  if (!created.ok && created.status !== 409) throw new HubError(`create repo: HTTP ${created.status} ${(await created.text()).slice(0, 200)}`);

  // The file goes through Git LFS: ask where to put it, put it there (in parts if asked), verify
  const batch = await (await hub(fetchFn, `${HUB}/${repo}.git/info/lfs/objects/batch`, {
    method: 'POST',
    headers: { ...auth, ...LFS_HEADERS },
    body: JSON.stringify({ operation: 'upload', transfers: ['basic', 'multipart'], objects: [{ oid: sha256, size: bytes.length }], hash_algo: 'sha256', ref: { name: 'main' } }),
  }, 'LFS batch')).json();
  const actions = batch.objects?.[0]?.actions;
  if (actions?.upload) {
    const { href, header = {} } = actions.upload;
    if (header.chunk_size) {
      const size = Number(header.chunk_size);
      const parts = Object.keys(header).filter((k) => /^\d+$/.test(k)).sort((a, b) => Number(a) - Number(b));
      const etags = [];
      for (const [i, part] of parts.entries()) {
        const response = await hub(fetchFn, header[part], { method: 'PUT', body: bytes.subarray(i * size, (i + 1) * size) }, `part ${part}`);
        etags.push({ partNumber: i + 1, etag: response.headers.get('etag') });
      }
      await hub(fetchFn, href, { method: 'POST', headers: LFS_HEADERS, body: JSON.stringify({ oid: sha256, parts: etags }) }, 'LFS completion');
    } else {
      await hub(fetchFn, href, { method: 'PUT', body: bytes }, 'LFS upload');
    }
    if (actions.verify) {
      await hub(fetchFn, actions.verify.href, {
        method: 'POST', headers: { ...auth, ...LFS_HEADERS }, body: JSON.stringify({ oid: sha256, size: bytes.length }),
      }, 'LFS verify');
    }
  }

  const lines = [
    { key: 'header', value: { summary: `Model shared in Med Herb Lens (${model.id})`, description: '' } },
    { key: 'file', value: { content: base64(new TextEncoder().encode(modelCard(model))), path: 'README.md', encoding: 'base64' } },
    { key: 'lfsFile', value: { path: 'model.tflite', algo: 'sha256', oid: sha256, size: bytes.length } },
  ];
  await hub(fetchFn, `${HUB}/api/models/${repo}/commit/main`, {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/x-ndjson' },
    body: lines.map((line) => JSON.stringify(line)).join('\n') + '\n',
  }, 'commit');
  return `${HUB}/${repo}`;
}

/** Deletes the repository at [url] (one of ours); nothing when it's already gone. */
export async function unpublish(env, url, fetchFn = fetch) {
  const [organization, name] = new URL(url).pathname.split('/').filter(Boolean);
  const response = await fetchFn(`${HUB}/api/repos/delete`, {
    method: 'DELETE',
    headers: { authorization: `Bearer ${env.HF_TOKEN}`, 'content-type': 'application/json' },
    body: JSON.stringify({ type: 'model', name, organization }),
  });
  if (!response.ok && response.status !== 404) throw new HubError(`delete repo: HTTP ${response.status}`);
}

// Firestore REST values: { stringValue }, { booleanValue }, { arrayValue: { values } } ...
const str = (fields, key) => fields?.[key]?.stringValue;

/** The sharedModels entry as a plain object, or null when there's none. */
export async function sharedModel(env, id, fetchFn = fetch) {
  const response = await firestoreRequest(env, `sharedModels/${id}`, {}, fetchFn);
  if (!response || response.status === 404) return null;
  if (!response.ok) throw new Error(`Firestore read: HTTP ${response.status}`);
  const { fields } = await response.json();
  return {
    id,
    name: str(fields, 'name') ?? '',
    species: (fields.species?.arrayValue?.values ?? []).map((v) => v.stringValue ?? ''),
    backbone: str(fields, 'backbone') ?? '',
    trainable: fields.trainable?.booleanValue === true,
    uploaderId: str(fields, 'uploaderId'),
    huggingFace: str(fields, 'huggingFace') ?? 'none',
    huggingFaceUrl: str(fields, 'huggingFaceUrl'),
  };
}

export async function markPublished(env, id, url, fetchFn = fetch) {
  const fields = { huggingFace: { stringValue: url ? 'published' : 'declined' } };
  if (url) fields.huggingFaceUrl = { stringValue: url };
  const mask = Object.keys(fields).map((f) => `updateMask.fieldPaths=${f}`).join('&');
  const response = await firestoreRequest(env, `sharedModels/${id}?${mask}`, {
    method: 'PATCH', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ fields }),
  }, fetchFn);
  if (!response?.ok) throw new Error(`Firestore update: HTTP ${response?.status}`);
}

export async function deleteSharedModel(env, id, fetchFn = fetch) {
  const response = await firestoreRequest(env, `sharedModels/${id}`, { method: 'DELETE' }, fetchFn);
  if (response && !response.ok && response.status !== 404) throw new Error(`Firestore delete: HTTP ${response.status}`);
}
