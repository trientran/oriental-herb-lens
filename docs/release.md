# Releasing Med Herb Lens

`.github/workflows/release.yml` builds signed releases and hands them to the stores, the way a
Bitrise release workflow does:

- **Android:** a signed App Bundle, uploaded to Google Play's **internal testing** track as a
  **draft** release. In Play Console you add release notes, then roll it out or promote it.
- **iOS:** an App Store archive, uploaded to App Store Connect. It appears in **TestFlight** once
  Apple has processed it (usually 10–30 minutes); from there you add testers or submit it for review.

## Running a release

1. Merge what should ship into the branch you release from (usually `master`).
2. GitHub → **Actions → Release → Run workflow**. Choose the branch, enter the version shown to
   users (e.g. `1.2`), and pick Android, iOS or both.
3. Android: Play Console → Med Herb Lens → **Testing → Internal testing**: the draft release is
   there. Add release notes, save, and roll it out (or promote it to production).
4. iOS: App Store Connect → Med Herb Lens → **TestFlight**: the build appears when processed.

The build number (Android `versionCode`, iOS `CFBundleVersion`) is the workflow's run number times
10, so every release has a higher one than the last.

## One-time setup: GitHub secrets

Add these under GitHub → the repository → **Settings → Secrets and variables → Actions**. Never
commit any of these files.

### Shared

| Secret | Value |
|---|---|
| `PHOTO_UPLOAD_URL` | The photo-upload Worker, as in `local.properties` |

### Android

| Secret | Value |
|---|---|
| `GOOGLE_SERVICES_JSON` | Already set for CI: `base64 -i androidApp/google-services.json` |
| `MAPBOX_DOWNLOADS_TOKEN` | Already set for CI |
| `RELEASE_KEYSTORE_BASE64` | The upload keystore: `base64 -i path/to/upload.jks` |
| `RELEASE_STORE_PASSWORD` | Its password |
| `RELEASE_KEY_ALIAS` | The key's alias |
| `RELEASE_KEY_PASSWORD` | The key's password |
| `PLAY_SERVICE_ACCOUNT_JSON` | The JSON key of a Google Cloud service account with access to the app in Play Console (below), pasted as is |

The keystore must be the one the app is already signed with on Google Play (the **upload key** if
the app uses Play App Signing). These are the same values as `RELEASE_*` in your `local.properties`.

**Service account for Google Play:**
1. Google Cloud console (the project linked to Play Console, or any project) → **IAM & Admin →
   Service accounts → Create service account**, e.g. `play-release`. No roles needed.
2. Open it → **Keys → Add key → Create new key → JSON**. The downloaded file is the secret.
3. Play Console → **Users and permissions → Invite new users**: the service account's email. Under
   **App permissions** add Med Herb Lens with **Release apps to testing tracks** (and **Release to
   production** if you want to promote from the workflow later). Send the invitation.

### iOS

| Secret | Value |
|---|---|
| `GOOGLE_SERVICE_INFO_PLIST` | `base64 -i iosApp/iosApp/GoogleService-Info.plist` |
| `APP_STORE_CONNECT_KEY_ID` | The API key's Key ID |
| `APP_STORE_CONNECT_ISSUER_ID` | The Issuer ID shown above the keys list |
| `APP_STORE_CONNECT_KEY_P8` | The contents of the downloaded `AuthKey_XXXX.p8` file, pasted as is |

**App Store Connect API key:** App Store Connect → **Users and Access → Integrations → App Store
Connect API → Team Keys → +**. Name it e.g. `GitHub release`, access **Admin** (Xcode needs it to
create the distribution certificate and profile in the cloud, so no certificates are stored in
GitHub). Download the `.p8` file; Apple lets you download it only once.

Signing is automatic: Xcode uses the key to sign with a cloud-managed distribution certificate for
team `LY5X89S79S`. Nothing else is needed on the Apple side.

### Moderation

| Secret | Value |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | The JSON key of a Google Cloud service account with the **Cloud Datastore User** role, pasted as is |

`.github/workflows/moderation.yml` runs every hour and opens a GitHub issue (label `moderation`)
for each photo reported in the app, so GitHub emails you. Act on it within 24 hours with
`tools/moderate.py` (remove the photo, ban the uploader, or dismiss), then close the issue.
Create the key in Google Cloud console → IAM & Admin → Service accounts (project
`oriental-herb-lens-41d17`) → Create → role Cloud Datastore User → Keys → Add key → JSON. Keep a
copy for running `tools/moderate.py` locally (`GOOGLE_APPLICATION_CREDENTIALS`).

Like the release workflow, it appears in the Actions tab once it's on the default branch.

## Before the first store release

- Privacy policy and terms (`website/pages/`) published at the URLs the app links to
  (`LegalLinks` in core:designsystem) and entered in Play Console and App Store Connect.
- App Store Connect: App Privacy answers, screenshots, description, age rating (18+), and
  notes for review: account deletion (Profile → Delete account), and the content safeguards
  of guideline 1.2: photos are checked for a plant on the device before upload, any shared
  photo can be reported or its contributor hidden (photo viewer → ⋮), reports reach the
  admin within the hour, and offenders are banned.
- Firestore: after the Android release is live, set `mustUpdateAndroid` in `config/mobile` so old
  versions update (the new rules are already deployed; see `docs/firestore-rollout.md`).
