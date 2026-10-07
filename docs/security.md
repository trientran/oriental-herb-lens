# Security and privacy measures

What Med Herb Lens does to protect its users, their data and its services, and where each measure
lives in this repository. Written for the record (and the thesis); keep it current when a measure
changes. Status as of 7 October 2026. Items marked **(console)** are settings in the Firebase,
Google Cloud or Cloudflare consoles, which the repository can't show; check them there.

## Principles

- **Data minimisation.** Browsing and identifying need no account. Photos are identified on the
  device; nothing leaves it unless the user chooses to share. Usage statistics are anonymous.
- **Least privilege.** Clients can read and write only what the security rules allow, field by
  field. Storage credentials never ship in an app.
- **Defence in depth.** Each shared item passes the app's checks, a server that verifies the
  user, storage rules that check it again, and human moderation after.
- **Tested rules.** The Firestore rules, the upload Worker and the app's checks have automated
  tests that run on every pull request.

## 1. Identity and accounts

- **Sign-in:** Firebase Authentication with Google, and with Apple on iPhone and iPad. The app
  stores no passwords. Sign in with Apple uses a random nonce whose SHA-256 goes with the request,
  so a token can't be replayed (`iosApp/iosApp/SignInBridges.swift`).
- **Who needs an account:** only sharing photos or models, reporting, and suggesting names.
- **Account deletion** in the app (Profile → Delete account), after a fresh sign-in as Firebase
  requires. It removes the user's Firestore record, then the account
  (`FirebaseAuthRepository.deleteAccount`); on iOS, the Apple token is revoked as Apple requires.

## 2. App integrity (App Check)

Firebase App Check makes Firebase accept requests only from the genuine apps:

| Platform | Provider |
|---|---|
| Android | Play Integrity (`BaseApplication.kt`) |
| iOS | App Attest (`HerbLensApp.swift`) |
| Web | reCAPTCHA Enterprise (`WebFirebase.kt`) |

Debug builds use the debug provider, with tokens registered by hand. Firestore enforces App Check
**(console)** (`docs/manual-test-script.md`).

## 3. Database (Firestore security rules)

`firebase/firestore.rules`, tested with the emulator in CI (`firebase/test/rules.test.js`):

- **Closed by default:** anything not allowed explicitly is refused.
- **Every write is validated:** only the expected fields (`keys().hasOnly`), their types and
  sizes, and server timestamps.
- **Ownership:** contributions carry the user's own uid; a shared model can be removed only by its
  sharer.
- **Append-only photos:** a write may only add photos (at most 20 at a time, 2,000 per species),
  never change or remove others'.
- **Reports and suggestions are write-only:** clients can create them, never read them back. Only
  signed-in users can report.
- **Bans:** `bannedUsers/{uid}`, created by the administrator, stops an account contributing,
  checked by the rules on every write.
- **Shared models:** the entry's id must be a UUID, its URL must point at that id's file, its
  size at most 25 MB and its licence CC BY 4.0.

## 4. Uploads (Cloudflare Worker)

`workers/photo-upload`, tested in CI. The apps upload photos and shared models through it, so no
storage credentials are in the apps.

- **Verifies the user** on every request: the Firebase ID token's RS256 signature against
  Google's published keys, its issuer and audience (this project), expiry and sign-in time
  (`src/firebase-auth.js`).
- **Checks bans** with a service account (`src/bans.js`); banned accounts get 403.
- **Validates content:** JPEG photos up to 5 MB and TensorFlow Lite models up to 25 MB, checked by
  their first bytes, not just their declared type.
- **Rate limits** (Workers rate limiting): 120 requests a minute per network address, checked
  before any token work, and 30 uploads a minute per account. The apps wait and carry on when
  told to slow down (`sendWithinRateLimit`).
- **Unguessable names:** files are stored under random UUIDs, with the uploader's uid as
  metadata; only the uploader can delete a shared model's file.
- **CORS** allows only the website and local development.
- **Storage:** Cloudflare R2, which charges nothing for downloads, so heavy traffic can't run up
  a bandwidth bill.

## 5. User-generated content and moderation

To meet App Store guideline 1.2 and Google Play's user-generated content policy:

- **Before sharing:** an on-device check that a photo shows a plant (`PlantCheck`); for models,
  accepted sharing terms and name checks that refuse contact details and obvious abuse
  (`SharingRules`).
- **Reporting** any shared photo or model, with a reason; it's hidden for the reporter at once.
- **Hiding** everything a person shared (photos and models), on the device.
- **Review within 24 hours:** a GitHub Actions workflow runs every hour and opens an issue for
  each new report, so the administrator gets an email (`.github/workflows/moderation.yml`).
  `tools/moderate.py` removes content (from Firestore and R2) and bans accounts.
- **Terms** that forbid objectionable content and explain removal and bans
  (`website/pages/terms-of-service.html`).
- **Hugging Face:** a shared model is published there only if its sharer leaves the box ticked,
  by the upload Worker, which checks the request comes from the sharer and checks the names again
  on the server. The write token is a Worker secret (`HF_TOKEN`), never in an app; clients can
  only ask for publishing, not mark a model published (Firestore rules). Taking a model down
  (by its sharer or the administrator) deletes its Hugging Face repository too.

## 6. Privacy

- **On-device identification:** the herb model and user-trained models run on the device; no
  third-party identification service is used.
- **Shared photos** are reduced in size. Their location is read only when the user taps *Use my
  location*, and the user can place the pin elsewhere.
- **Shared models** carry names and a numerical summary of photos, not the photos.
- **Usage statistics** (Google Analytics for Firebase): anonymous, with no user ID, advertising ID
  or ad personalisation. The user can turn them off in Profile, and debug builds are labelled so
  they can be filtered out. Research mode sends only the size of a run, never its results.
- **Published policy:** `website/pages/privacy-policy.html`.

## 7. Secrets and the supply chain

- **Secrets stay out of the repository** (`.gitignore`): `local.properties`,
  `google-services.json`, `GoogleService-Info.plist`, `Secrets.xcconfig`, keystores and service
  account keys. CI gets them from encrypted GitHub secrets, and the release build signs with a
  keystore decoded only at build time (`.github/workflows/release.yml`).
- **Release builds** of Android are minified and shrunk with R8.
- **Dependencies:** Dependabot alerts are watched. The npm packages pulled in by the Kotlin/JS
  build are pinned to patched versions (`build.gradle.kts`), and every lockfile is committed.
- **CI on every pull request:** unit, screenshot and lint checks, the iOS build and tests, and the
  rules and Worker tests. A ruleset stops `develop` and `master` being deleted.
- **Build tools** come from known sources: the Gradle daemon's JDK from JetBrains' own download
  site, at a fixed build (`.github/actions/daemon-jdk`).

## 8. Operations and cost control

- **Remote switches** (Remote Config): `service_suspended` closes the app with a notice, and
  `min_supported_version_*` forces an update, e.g. after a security fix.
- **Content updates** (the herb model and catalog) are verified by their SHA-256 before use, and
  the working copy is replaced only after every check passes (`docs/content-publishing.md`).
- **Crash reports** (Android): Firebase Crashlytics.
- **(console)** Recommended, to be confirmed:
  - API keys restricted to the apps and the website, and to the APIs each one needs;
  - a budget alert in Google Cloud Billing;
  - App Check enforced for Firestore and Authentication.

## Known limits and next steps

- The rate limits count per Cloudflare location, so they're approximate; there's no daily cap per
  account.
- Name checks are a first line only: a short list of words in English and Vietnamese. Reports and
  the administrator catch the rest.
- A shared model's photo summaries (embeddings) can't be turned back into the photos in any
  practical way, but they aren't formally private: users are told, in the terms, what a shared
  model contains.
- No certificate pinning: the apps rely on the platforms' TLS checks.
