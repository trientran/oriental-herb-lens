# Photo-upload Worker

Stores photos that signed-in users take of a species in the `herb-lens-content` R2 bucket, so the
app never holds R2 credentials.

```
POST /photos?speciesKey=<GBIF species key>
Authorization: Bearer <Firebase ID token>
Content-Type: image/jpeg

201 {"url": "https://<public bucket URL>/photos/<speciesKey>/<uuid>.jpg"}
```

The Worker checks the ID token against Google's signing keys (issuer and audience must be the
`oriental-herb-lens-41d17` project), accepts JPEG only, up to 5 MB, and stores the file with the
uploader's uid as metadata. The app then adds the URL to `herbs/{speciesKey}.images` in
Firestore; the security rules decide who may attach photos, including bans.

It also stores models shared from the Train tab:

```
POST /models
Authorization: Bearer <Firebase ID token>
<.tflite bytes>

201 {"id": "<uuid>", "url": "https://<public bucket URL>/models/<uuid>.tflite", "size": <bytes>}

DELETE /models/<uuid>          (its uploader only) 204
```

Only TensorFlow Lite files (identifier `TFL3`) up to 25 MB; the app then lists the model in
`sharedModels/{id}`, whose rules check the entry. The CORS headers allow POST and DELETE.

## Deploy

From this directory, logged in with `wrangler login`:

```bash
npx wrangler deploy
```

Wrangler prints the Worker's URL, e.g. `https://herb-lens-photo-upload.<account>.workers.dev`. Put
it in the project's `local.properties` (and as a CI secret if CI ever builds a release):

```
PHOTO_UPLOAD_URL=https://herb-lens-photo-upload.<account>.workers.dev
```

`wrangler.toml` sets the bucket binding and the public base URL returned to the app. When the
bucket moves to a custom domain, change `PUBLIC_BASE_URL` and deploy again; photos uploaded before
keep their old URLs, so keep the r2.dev URL enabled.

## Web app access (CORS)

The web app reads photos with `fetch`, so the bucket must allow its origins, and the Worker
answers its uploads for the origins in `ALLOWED_ORIGINS` (`wrangler.toml`). Keep both lists in
step. Apply the bucket's rules once, and again whenever `r2-cors.json` changes:

```bash
npx wrangler r2 bucket cors set herb-lens-content --file r2-cors.json
```

## Rate limiting

Built in (`[[ratelimits]]` in `wrangler.toml`, Workers rate limiting, free): each address gets
120 requests a minute before its token is checked, and each account 30 uploads a minute (one
contribution sends at most 20). Over that, the Worker answers 429 with `Retry-After: 60`. The
counts are kept per Cloudflare location, so they're approximate. Change the numbers there and
deploy again.

## Test

```bash
npm test
```

Uses Node's built-in test runner; no dependencies to install.
