# Manual test script

Run this on a real device before and after each migration phase. It covers the flows unit tests
can't reach yet: camera, ML Kit and Firestore listeners. Record results in the log at the
bottom so a later run can be compared with an earlier one.

**Setup:** a debug build, a signed-in test account, network on unless a step says otherwise, and
three herb photos on the device (at least one of Đinh lăng, Lá lốt or Huyết dụ).

Firestore enforces App Check, so each new debug install prints a line in Logcat like
`Enter this debug secret into the allow list in the Firebase Console`. Add that secret under
App Check → Apps → Manage debug tokens, or every Firestore read fails with `PERMISSION_DENIED`
and the herb lists stay empty.

## 1. Launch and navigation

1. Fresh install, launch. The app opens on Browse with no sign-in prompt; the keyboard stays down.
   Sign in later from Profile → Sign in (our own screen, Google only).
2. Bottom bar: Identify, Browse, Saved, Profile. Switching tabs keeps each tab's scroll position.
3. Rotate to landscape (or use a tablet): a navigation rail replaces the bottom bar, and Browse
   shows the list and the selected species side by side.
4. Dark theme and Vietnamese (Settings → Apps → Med Herb Lens → Language on Android 13+): every
   screen follows; lists sort by Vietnamese name in Vietnamese.

## 2. Browse and saved

1. Browse lists every species in the catalog (about 4,800), sorted by Vietnamese name with
   unnamed species last (by scientific name when the app language isn't Vietnamese). Scroll far
   down: more load, with no duplicates. Works in airplane mode.
2. Saved → Favourites and History show what was saved earlier, most recent first. Both show a
   short explanation when empty.

## 3. Text and voice search

Search runs on the device, so check it in airplane mode too.

1. Each query finds the expected species first:

   | Query | Expected first result |
   |---|---|
   | `dinh lang`, `Đinh lăng`, `lang dinh`, `dinh lanh` | Đinh lăng · *Polyscias fruticosa* |
   | `bach bo` | Bách bộ · *Stemona tuberosa* |
   | `okra` | *Abelmoschus esculentus* |
   | `fruticosa` | both *Polyscias* and *Cordyline fruticosa* near the top |

2. The matched part of the name is bold and green; scientific names are italic.
3. Microphone: say "đinh lăng". The spoken text appears in the search box with results.
4. A query with no match explains that nothing matched.

## 4. Species details

1. Open a species from Browse, a search result and Saved. On a phone, back returns to the list.
2. No dosing, caution or medicinal text anywhere.
3. The screen shows the Vietnamese name, the scientific name in italics with its authorship, every
   Vietnamese and English name, family and genus, and "View on GBIF" opens the species on gbif.org.
4. Tap the heart, then again. Saved → Favourites updates without a restart.
5. The species appears at the top of History.
6. Photos: user photos first, then GBIF photos (online only); swipe through them. Each shows its
   credit (creator and licence; "via iNaturalist and GBIF" for GBIF photos).
7. Tap a photo: full screen, pinch to zoom, double-tap to reset, swipe to the next. "Source" opens
   the photo's page.
8. Airplane mode: cached user photos still show, with no error.

## 5. Live camera

1. Identify → Camera. Grant the camera permission.
2. Point at a known herb. The recognised name and confidence appear and update live.
3. Point at a blank wall. No confident result is shown.
4. Change the confidence level in the camera screen, leave and come back. The value is kept.

## 6. Object camera (multi-object mode)

1. In the camera screen, switch to object mode.
2. Hold on a herb. The reticle confirms the object, then results appear in the bottom sheet.
3. Camera settings → turn auto labeling off. Detection now waits for a manual tap.

## 7. Single image

1. Identify → One photo, pick a photo.
2. With objects mode on: dots appear on detected objects; tapping one shows its results.
3. With objects mode off: one result list for the whole image.
4. Leave and return. Both the objects-mode switch and the confidence level are kept.

## 8. Multiple images

1. Identify → Several photos, pick three photos.
2. Each photo gets its own result row; a photo with no match shows an empty result.

## 9. Contribute photos

Test photos: `tools/fetch_test_images.py --push` copies GBIF photos of species the model knows to
the emulator's Pictures/HerbLens folder. Don't upload those: they belong to their photographers.
Upload photos you took yourself.

1. Signed out: on a species, Add photos opens the contribute screen with a sign-in card; Sign in
   opens our sign-in screen; Continue with Google shows the account chooser; after choosing, you
   return to the contribute screen, signed in. Backing out of the chooser shows no error.
2. Add photos opens the system photo picker; pick two. Both appear; the × removes one.
3. Use my location: allow location; the address appears and the map shows the point.
4. Choose on map: a full-screen map opens with a pin in the middle. Drag and pinch the map until
   the pin is on the place, then Use this place: the address and a small map appear. The page
   scrolls normally over that small map. Remove location clears it.
5. Upload: the progress reaches the total, then the thank-you screen; Done returns to the species,
   where the photos now appear first, and "Where photos were taken" shows the place on a map.
6. Airplane mode, then Upload: an error appears and Upload can be tried again.

## 10. Suggest a Vietnamese name

1. On a species, tap "Suggest a Vietnamese name". A sheet opens with the current name.
2. Send is disabled until the text differs from the current name.
3. Send. The sheet thanks you; the displayed name doesn't change (the admin reviews suggestions).
4. Signed out, the sheet offers to sign in instead.

## 11. Profile and settings

1. Profile shows sign in (signed out) or sign out (signed in); sign out, then sign in again.
2. Move the minimum confidence slider and turn "Find plants in a photo" off. Kill and relaunch:
   both are kept, and the scan screens use them.
3. Camera settings opens the camera preferences (preview size, confirmation times); changes are kept.
4. Full herb list, Share, Contact, About and Privacy policy open the right page or app.

## 12. Offline

1. Airplane mode, relaunch.
2. Single image and live camera still recognise herbs (on-device model).
3. Herbs viewed earlier still open (Firestore cache).

## 13. Content update (model and catalog)

Follow docs/content-publishing.md to publish a catalog or model, then:

1. Relaunch a debug build twice (the first launch fetches Remote Config and downloads).
2. Logcat shows `Installed CATALOG from …` / `Installed MODEL from …`.
3. Scan results use the new model; names come from the new catalog.
4. Set Remote Config back to the previous values: the next launch switches back.

## Results log

| Date | Build / commit | Device | Failed steps | Notes |
|---|---|---|---|---|
| | | | | |
