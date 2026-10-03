# Firestore rollout

From Phase 2 on, the app reads Firestore only for a species' user photos (`herbs/{speciesKey}`)
and each user's own document once, to copy old favourites and history to the device. It writes
only photo URLs and Vietnamese name suggestions. `firebase/firestore.rules` locks everything
else down, but **installed older versions still write names, reviews and favourites**, so the
order of the steps matters.

## 1. Release the new version

Publish the Phase 2 build. Wait until most users have it (Play Console → Statistics → by app
version).

## 2. Force older versions to update

Older versions read `config/mobile`; newer ones read Remote Config.

1. Remote Config: set `min_supported_version_android` to the new build's version code, publish.
2. Firestore console: in `config/mobile`, set `mustUpdateAndroid` to `true`.

Both show a dialog that sends users to the Play Store and can't be dismissed.

## 3. Move bans to documents

Bans used to be a `bannedUsers` array in `config/mobile`, checked by the app. They're now
enforced by the rules: for each banned uid, create an empty document `bannedUsers/{uid}`.
Unbanning is deleting that document.

## 4. Deploy the rules

Test first (needs Java and Node; the first run downloads the Firestore emulator):

```bash
cd firebase && npm ci && npm test
```

Then deploy:

```bash
cd firebase && npx firebase deploy --only firestore:rules --project oriental-herb-lens-41d17
```

Firestore console → Rules shows the deployed version and its history, for rollback.

## 5. Clean up later

Once no device runs an old version (or the app has been out for a few months), delete:

- the old fields on `herbs/*` documents (names, overview, dosing, side effects, interactions,
  reviews), keeping `images`
- `users/*` documents (favourites and history live on devices now)
- the `deletions` and `uploads` collections
- the `config/mobile` label maps (`recognized*`, `toBeRecognized*`)
- the Remote Config parameter `model_url` and the old model file in Firebase Storage

Export photo URLs before deleting anything you might need: `tools/export_photo_urls.py`.

## Before photos are served at scale

The bucket is public through its `r2.dev` URL, which Cloudflare rate-limits. Connect a custom
domain, set `PUBLIC_BASE_URL` in `workers/photo-upload/wrangler.toml` to it, and redeploy the
Worker. Photos uploaded earlier keep their r2.dev URLs, so leave that URL enabled.
