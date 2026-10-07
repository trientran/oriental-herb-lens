# Releasing Med Herb Lens

`.github/workflows/release.yml` builds signed releases and hands them to the stores, the way a
Bitrise release workflow does:

- **Android:** a signed App Bundle, uploaded to Google Play's **internal testing** track as a
  **draft** release. In Play Console you add release notes, then roll it out or promote it.
- **iOS:** an App Store archive, uploaded to App Store Connect. It appears in **TestFlight** once
  Apple has processed it (usually 10–30 minutes); from there you add testers or submit it for review.
- **Web:** the whole site (home page, privacy policy, terms, and the web app at `/app/`),
  published to Cloudflare Pages at `https://med-herb-lens.pages.dev`. It's live as soon as the
  job ends.

## When

Android, iOS and the web are released together after the last migration phase, once the
supervisor has signed off a thorough test (plan decision D15, phase R). Everything below is
ready meanwhile; the secrets are set.

## First release checklist

1. **Test** with the supervisor on Android, iPhone, iPad and the web. Settle with the ethics
   approval whether usage statistics are opt-in or opt-out (Profile → Privacy; on by default).
2. **Firestore rules:** if the previous rules were restored meanwhile (so the old app keeps
   working), deploy `firebase/firestore.rules` again:
   `cd firebase && npx firebase deploy --only firestore:rules --project oriental-herb-lens-41d17`
3. **Build:** GitHub → Actions → **Release → Run workflow**, version `2.0` (both platforms).
4. **Android:**
   - Play Console → Testing → Internal testing: finish the draft (release notes), roll it out
     to testers and test the store build.
   - Promote it to production.
   - Once it's live, set `mustUpdateAndroid` to `true` in Firestore `config/mobile`, so older
     versions ask their users to update.
5. **iOS:**
   - When the build appears in App Store Connect → TestFlight (10–30 minutes), install it with
     the TestFlight app and test it.
   - Fill in the App Store listing:
     - App Privacy answers (the table at the end of this file);
     - screenshots: iPhone 6.9″ and iPad 13″;
     - description, keywords, support URL `https://med-herb-lens.pages.dev`;
     - age rating 18+, to match the terms;
     - review notes (below).
   - Submit the build for review.
6. **Web:** the same workflow publishes the site (choose `all` or `web`). Check
   `https://med-herb-lens.pages.dev` links to the app and that `/app/` loads, signs in and
   identifies a photo.
7. **Google Analytics:** data retention 14 months, Google signals off; turn on the BigQuery export
   to keep the raw data longer.
8. **Google Play Data safety:** match the App Privacy table below (shared models are user content,
   shared publicly).
9. **Console safety nets:** a Google Cloud budget alert; API keys restricted to the apps and the
   website (docs/security.md, section 8).

## Running a release

1. Merge what should ship into the branch you release from (usually `master`).
2. GitHub → **Actions → Release → Run workflow**. Choose the branch, enter the version shown to
   users (e.g. `2.0`), and pick `all` or a single platform.
3. Android: Play Console → Med Herb Lens → **Testing → Internal testing**: the draft release is
   there. Add release notes, save, and roll it out (or promote it to production).
4. iOS: App Store Connect → Med Herb Lens → **TestFlight**: the build appears when processed.
5. Web: live at `https://med-herb-lens.pages.dev` when the job ends. Cloudflare Pages keeps every
   deployment: roll back under Workers & Pages → med-herb-lens → Deployments.

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

### Web

| Secret | Value |
|---|---|
| `CLOUDFLARE_API_TOKEN` | A Cloudflare API token with **Account → Cloudflare Pages → Edit** |
| `CLOUDFLARE_ACCOUNT_ID` | The account ID (Workers & Pages overview, right-hand side) |

Create the token in the Cloudflare dashboard → My Profile → API Tokens → Create Token → Custom
token, with only that permission, for your account.

### Moderation

| Secret | Value |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | The JSON key of a Google Cloud service account with the **Cloud Datastore User** role, pasted as is |

`.github/workflows/moderation.yml` runs every hour and opens a GitHub issue (label `moderation`)
for each photo reported in the app, so GitHub emails you. Act on it within 24 hours with
`tools/moderate.py` (remove the photo, ban the uploader, or dismiss), then close the issue.
It also opens an issue (label `name-suggestion`) for each Vietnamese name users suggest: add
the ones that are right to the catalog CSV and publish it, then `tools/moderate.py
dismiss-suggestion ID` and close the issue. GitHub pauses scheduled workflows after 60 days
without commits (it emails first); re-enable it in the Actions tab.
Create the key in Google Cloud console → IAM & Admin → Service accounts (project
`oriental-herb-lens-41d17`) → Create → role Cloud Datastore User → Keys → Add key → JSON. Keep a
copy for running `tools/moderate.py` locally (`GOOGLE_APPLICATION_CREDENTIALS`).

Like the release workflow, it appears in the Actions tab once it's on the default branch.

**The same key lets the photo-upload Worker refuse banned accounts.** Give it to the Worker once
(the Worker skips the check until it has it):

    cd workers/photo-upload
    npx wrangler secret put FIREBASE_SERVICE_ACCOUNT < /path/to/service-account.json

**Where to keep the key file locally:** outside the repository, e.g.
`~/.config/herb-lens/service-account.json` (it's a JSON file, not a single value, so it doesn't
belong in `local.properties`). Point the tools at it with
`export GOOGLE_APPLICATION_CREDENTIALS=~/.config/herb-lens/service-account.json`.

## Done ahead of the release

- Privacy policy and terms published at `https://med-herb-lens.pages.dev` (`website/`), and
  entered in Play Console, App Store Connect and the Google sign-in branding (domain verified).
- Secrets for both stores and for moderation are set in GitHub; the Worker has its service
  account.

## App Store review notes

Paste into App Store Connect → the version → App Review Information → Notes:

> Med Herb Lens identifies medicinal herbs with an on-device model; browsing and identification
> need no account. Signing in (Google or Sign in with Apple) is only needed to share photos or
> models, report, or suggest names.
>
> Account deletion: Profile → Delete account (the user signs in again to confirm).
>
> User-generated content (guideline 1.2): photos are checked on the device and only uploaded
> if a plant is found; any shared photo can be reported or its contributor hidden (open a photo
> on a species page → ⋮); reports reach the administrator within the hour and are acted on
> within 24 hours; offending photos are removed and their uploaders banned. The same applies to
> image classifiers users train and share (Train tab → a trained model → Share with everyone): names
> are checked on the device and the server before sharing; any shared model can be reported or its
> sharer hidden (Train → Models shared by others → a model); reported models are removed within
> 24 hours, together with their public copy on Hugging Face. Users must be 18+.

## App Store privacy answers (App Store Connect → App Privacy)

Keep these in step with the app; they can be edited at any time. Tracking: **No** for everything
(no advertising identifier, no ad personalisation, nothing shared with data brokers).

| Data type | Linked to the user | Purposes |
|---|---|---|
| Contact Info → Name | Yes | App Functionality |
| Contact Info → Email Address | Yes | App Functionality |
| Identifiers → User ID | Yes | App Functionality |
| User Content → Photos or Videos | Yes | App Functionality, Other Purposes (research, model training) |
| Location → Precise Location (place of each shared photo) | Yes | App Functionality, Other Purposes (research) |
| User Content → Other User Content (name suggestions, reports, shared models) | Yes | App Functionality, Other Purposes (research) |
| Identifiers → Device ID (Analytics app instance ID) | No | Analytics |
| Usage Data → Product Interaction | No | Analytics |
| Location → Coarse Location (Analytics, from the IP address) | No | Analytics |
| Diagnostics → Other Diagnostic Data (app version, device model) | No | Analytics |

Not declared: camera images used for identification (never leave the device), favourites and
history (stay on the device), crash data (Crashlytics is Android only).

In Google Analytics (Admin → Data collection and modification → Data retention), set event data
retention to 14 months, as the privacy policy says, and leave Google signals off.
