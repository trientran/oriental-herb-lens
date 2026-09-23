# Publishing the herb model and catalog

The app ships with a copy of the model (`app/assets/herb_model.tflite`) and the species catalog
(`app/assets/herb_catalog.csv`), so it works offline from its first launch. Newer versions are
hosted on Cloudflare R2 and announced through Firebase Remote Config. On every launch the app
compares the announced URL with the one it has installed and, if they differ, downloads the new
file, checks it, and replaces the old one under the same name.

## One-time setup

### 1. R2 bucket

1. Cloudflare dashboard → R2 → **Create bucket**, e.g. `herb-lens-content`.
2. Bucket → Settings → **Custom Domains** → connect a domain you manage in Cloudflare, e.g.
   `content.example.com`. Don't use the `r2.dev` public URL in production: Cloudflare rate-limits
   it and intends it for development.
3. Install and log in to wrangler on the machine you publish from:

   ```bash
   npm install -g wrangler
   ```

   ```bash
   wrangler login
   ```

4. Tell the publish script where to upload:

   ```bash
   export HERB_CONTENT_BUCKET=herb-lens-content HERB_CONTENT_BASE_URL=https://content.example.com
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

## Publishing a new version

Always publish the catalog first when a new model adds species. The app won't activate a model
whose labels aren't all in its catalog; it keeps the verified download and waits.

```bash
tools/publish_content.py catalog path/to/species_list.csv
```

```bash
tools/publish_content.py model path/to/herb_model.tflite --catalog path/to/species_list.csv
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
