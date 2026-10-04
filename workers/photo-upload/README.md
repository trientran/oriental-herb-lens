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

## Recommended: rate limiting

In the Cloudflare dashboard → Security → WAF → Rate limiting rules, add a rule for this Worker's
hostname, e.g. 20 requests per minute per IP, to blunt abuse by a signed-in account.

## Test

```bash
npm test
```

Uses Node's built-in test runner; no dependencies to install.
