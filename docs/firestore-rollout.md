# Firestore rollout

> **Status (5 Oct 2026):** the rules (step 4) were deployed early, before the new version
> ships, and the release waits until after the last phase (plan decision D15). Until then the
> version on Google Play can't save favourites or upload photos. If that matters, restore the
> previous rules from Firestore console → Rules → history, and deploy `firebase/firestore.rules`
> again right before the release (`docs/release.md`, first release checklist). Steps 1–3 and 5
> are still to do, at release time.

From Phase 2 on, the app reads Firestore only for a species' user photos (`herbs/{speciesKey}`)
and each user's own document once, to copy old favourites and history to the device. It writes
photo URLs, Vietnamese name suggestions, photo reports, and each contributor's uid, name and
email on their own `users/{uid}` document. `firebase/firestore.rules` locks everything
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
- the old `favorite` and `history` fields of `users/*` documents (they live on devices now;
  keep the documents, which hold each contributor's uid, name and email)
- the `deletions` and `uploads` collections
- the `config/mobile` label maps (`recognized*`, `toBeRecognized*`)
- the Remote Config parameter `model_url` and the old model file in Firebase Storage

Export photo URLs before deleting anything you might need: `tools/export_photo_urls.py`.

## Before photos are served at scale

The bucket is public through its `r2.dev` URL, which Cloudflare rate-limits. Connect a custom
domain, set `PUBLIC_BASE_URL` in `workers/photo-upload/wrangler.toml` to it, and redeploy the
Worker. Photos uploaded earlier keep their r2.dev URLs, so leave that URL enabled.
