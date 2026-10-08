# Publishing the herb model and catalog

The app ships with a copy of the model (`androidApp/assets/herb_model.tflite`) and the species catalog
(`androidApp/assets/herb_catalog.csv`), so it works offline from its first launch. Newer versions are
hosted on Cloudflare R2 and announced through Firebase Remote Config. On every launch the app
compares the announced URL with the one it has installed and, if they differ, downloads the new
file, checks it, and replaces the old one under the same name.

## One-time setup

### 1. R2 bucket

1. Cloudflare dashboard → R2 → **Create bucket**, e.g. `herb-lens-content`.
2. Make the bucket public, using either:
    - **A custom domain (preferred).** Bucket → Settings → **Custom Domains** → connect a domain you
      manage in Cloudflare, e.g. `content.example.com`. Responses go through Cloudflare's cache.
    - **The r2.dev URL, if you don't have a domain yet.** Bucket → Settings → **Public Development
      URL** → **Allow**, then copy the `https://pub-<hash>.r2.dev` URL. Cloudflare rate-limits this
      URL and doesn't cache it, but that's acceptable here: devices download a file only when a new
      version is published, and if a download is throttled the app keeps its current copy and
      retries with backoff.

   To move from r2.dev to a custom domain later, connect the domain and set
   `HERB_CONTENT_BASE_URL` to it. Then republish and update Remote Config with the new values.
   Keep the r2.dev URL enabled until no device is still using the old URLs.
   **Which sites may load it (CORS).** The web app downloads the model and catalog from the
   bucket, so the bucket must allow each site address the app runs on: the live site, the
   `review` preview and local development, listed in [r2-cors.json](r2-cors.json). Without it, a
   browser refuses the download: the web app then falls back to the copies served with the site,
   which may be older. After changing the list:

   ```bash
   npx wrangler r2 bucket cors set herb-lens-content --file docs/r2-cors.json
   ```

3. Install and log in to wrangler on the machine you publish from:

   ```bash
   npm install -g wrangler
   ```

   ```bash
   wrangler login
   ```

4. Tell the publish script where to upload. `HERB_CONTENT_BASE_URL` is the custom domain or the
   r2.dev URL from step 2:

   ```bash
   export HERB_CONTENT_BUCKET=herb-lens-content HERB_CONTENT_BASE_URL=https://pub-394936ea0f444a64a542ff743b102f1f.r2.dev
   ```

Files are uploaded with `Cache-Control: public, max-age=31536000, immutable`. That's safe because
each version gets a new file name and existing files are never overwritten.

### 2. Remote Config

Create four string parameters in Firebase → Remote Config and leave them empty for now:

| Key | Value |
|---|---|
| `herb_model_url` | Public URL of the model file |
| `herb_model_sha256` | Its SHA-256, 64 hex characters |
| `herb_catalog_url` | Public URL of the catalog CSV |
| `herb_catalog_sha256` | Its SHA-256 |

While a URL or checksum is empty, the app keeps using its bundled copy. The old `model_url`
parameter (a `gs://` Firebase Storage path) is no longer read by new app versions. Keep it until
nobody runs an older version, then delete it along with the Firebase Storage file.

## Common names in the catalog

Each species' common names are in the catalog CSV, names separated by `; `, the preferred one
first: `vietnameseName` for Vietnamese and `vernacularName` for English. Names in other languages
go in a column per language, `vernacularName_<code>` with an ISO 639-1 code (e.g.
`vernacularName_zh`). The app reads them all, but shows, and takes suggestions in, only the
languages in `NameLanguages.ENABLED` (Vietnamese and English), until the research ethics approval
for others. Accepted user suggestions (`tools/moderate.py suggestions`) go in the column for their
language.

## Publishing a new version

Always publish the catalog first when a new model adds species. The app won't activate a model
whose labels aren't all in its catalog; it keeps the verified download and waits.

```bash
tools/publish_content.py catalog androidApp/assets/herb_catalog.csv
```

```bash
tools/publish_content.py model androidApp/assets/herb_model.tflite --catalog androidApp/assets/herb_catalog.csv
```

The script:

1. checks the file the way the app will: required catalog columns and no malformed rows; a label
   list embedded in the model, with every label present in the catalog
2. normalises the catalog to Unicode NFC (the source data mixes composed and decomposed Vietnamese)
3. uploads it under a new name, e.g. `catalog/herbs-20261001-5d62fb66.csv`
4. downloads it back from the public URL and compares the SHA-256
5. prints the two Remote Config values to set

Then paste those values into Remote Config and **Publish changes**. Add `--dry-run` to check a
file without uploading anything.

## Checking it on a device

Debug builds fetch Remote Config without the 1-hour minimum interval. After publishing, relaunch a
debug build and filter Logcat by `Content sync`: a successful run logs
`Installed MODEL from https://…`. A model that arrived before its catalog logs `Deferred`, and it
is activated automatically once the catalog is published.

## Rolling back

Set the Remote Config values back to the previous URL and checksum and publish. Apps switch back
on their next launch, since the old files are still in the bucket.

## Rules the app enforces

| Situation | What the app does |
|---|---|
| Checksum doesn't match | Rejects the file and doesn't retry until the release changes |
| Network error | Retries later with exponential backoff |
| Catalog missing a required column | Rejects it |
| Model has no embedded `labels.txt` | Rejects it |
| Model labels not all in the catalog | Keeps the verified file and activates it once a catalog covers them |
| Anything fails | Keeps the current files; the working copy is replaced only after every check passes |

## How to cite

Profile → How to cite lists the works to cite when the app is used in research. They come from the
Remote Config string parameter `how_to_cite`, so a new paper appears without a release. Leave it
empty (or unset) and the section is hidden.

The value is a JSON list, newest first. `text` is the full reference as it should be pasted into a
bibliography; `url` is optional and must be a web link (a DOI link for papers):

```json
[
  {
    "text": "Author, A. (Year). Paper title. Journal, volume(issue), pages. https://doi.org/…",
    "url": "https://doi.org/…"
  },
  {
    "text": "Author, A. (Year). Med Herb Lens (Version x.y) [Mobile app]. https://med-herb-lens.pages.dev",
    "url": "https://med-herb-lens.pages.dev"
  }
]
```

References aren't translated: they read the same in every language. A value that isn't a valid
list is ignored (the app logs a warning), and so are entries with no text.

