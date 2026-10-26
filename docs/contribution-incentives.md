# Encouraging contributions

The app thanks people for what they share by showing them its effect, privately, and points them
to where photos help most. Nothing is ranked against other people: no leaderboards, points,
streaks, reminder notifications or rewards. (The research ethics approval covers studying
contribution data.)

## What users see

| Where | What |
|---|---|
| Profile → Your contributions (signed in) | Photos shared, species photographed, names suggested, models shared, and how many times others added their models |
| Profile → Badges | Ten private milestones (`Badge`), marked "new" once; locked ones are shown greyed |
| Profile → Credit me as | An optional name shown on the photos and models they share from then on; empty means "a Med Herb Lens user" |
| Browse → Needs photos | The catalogue species the current herb model doesn't know yet (not in its `labels.txt`) |
| A herb's page | A "Needs photos" note on those species, in the apps (the web can't share photos) |
| Identify | Under a plant that wasn't recognised, a link to Needs photos (apps only) |
| Shared model page | "Shared by" its sharer's credit name, and "Times added" |

## Rules

- **Review period.** A contribution counts toward a badge only after `Badges.REVIEW_DAYS` (7) days,
  and photos and models only while still shown (photos still in `herbs/{key}.images`, models still
  in `sharedModels`). Reported and removed content therefore never earns a badge. Totals count
  everything still shown, reviewed or not.
- **Gap filler.** A photo counts as filling a gap when the herb model didn't know its species at the
  time it was shared (`neededPhotos`).
- **Model adds.** Counted by the upload Worker (`POST /models/<id>/added`), once per person and never
  the sharer's own: the Worker writes `sharedModels/{id}/adders/{sha256(uid:id)}` and increments
  `sharedModels/{id}.adds` in one commit, so the count keeps nobody's identity. Clients can't write
  `adds`.
- **Credit names** pass `SharingRules.creditProblem` (1 to 40 characters, no contact details or
  abuse), the Firestore rules (at most 40 characters on `sharedModels.creditName`) and, for Hugging
  Face model cards, the Worker's `namesAllowed`. On photos the name is the `name` field of the image
  detail JSON.

## Data

`users/{uid}/contributions/{id}`, readable and deletable by its owner only, created by the app after
each share: `kind` ("photo", "name" or "model"), `speciesKey`, `url`, `modelId`, `neededPhotos`,
`createdAt` (server time). Deleting the account deletes these entries; what was shared stays
shared. Contributions made before this version aren't in it (the app had no users then).

Code: `Badges` and `ContributionHistoryRepository` (core:domain), `FirestoreContributionHistory`
(core:data), `ContributionsCard` (feature:profile), `workers/photo-upload/src/adds.js`. Tests:
`BadgesTest`, `ProfileViewModelTest`, `BrowseViewModelTest`, `HerbDetailsViewModelTest`,
`firebase/test/rules.test.js`, `workers/photo-upload/test/adds.test.js`.

## Deploying

1. The Firestore rules (`firebase deploy --only firestore:rules`): they only add permissions, so
   older app versions keep working.
2. The upload Worker (`cd workers/photo-upload && npx wrangler deploy`), for the add counter.
3. Then the app release.
