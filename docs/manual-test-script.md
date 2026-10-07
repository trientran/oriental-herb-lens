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
   Vietnamese and English name, and family and genus. Full details opens a sheet with the rest of
   GBIF's record (scientific name, first described as, rank, status, where published, kingdom to
   genus, every name, the GBIF key); "View on GBIF" at its foot opens the species on gbif.org.
4. Tap the heart, then again. Saved → Favourites updates without a restart.
5. The species appears at the top of History.
6. Photos: user photos first, then GBIF photos (online only); swipe through them. Each shows its
   credit (creator and licence; "via iNaturalist and GBIF" for GBIF photos).
7. Tap a photo: full screen, pinch to zoom, double-tap to reset, swipe to the next. "Source" opens
   the photo's page.
8. Airplane mode: cached user photos still show, with no error.

## 5. Identify with the camera

Test photos: `tools/fetch_test_images.py --push` puts photos of species the model knows on an
emulator. On a phone, use real plants or photos of them on another screen.

1. Identify opens the camera straight away (allow the camera the first time). Denying shows an
   explanation with Allow camera; after a second denial, Open settings.
   On a fresh install, a "research preview" note explains first that results are often wrong;
   after I understand it doesn't show again, but "Learn more" under the results reopens it.
2. Whole view: point at a herb. The most likely herbs appear in the panel at the bottom with their
   confidence and update as you move. Nothing below the minimum confidence (Profile; 30 % unless
   changed) is shown.
3. Pick a plant: boxes appear around the plants in view; the biggest is chosen and identified.
   Tap another box: its results replace the first.
4. The mode is kept after leaving and reopening the app.
5. Tap a result: the species opens full screen; back returns to the camera.
6. Rotate to landscape or use a tablet: results show in a panel beside the camera.

## 6. Identify one photo

1. Photos → pick one photo. It replaces the camera; Camera returns to it.
2. Whole view: one list of results for the whole photo.
3. Pick a plant: each recognised plant gets a box; the most confident is selected. A photo with
   several plants or a busy background usually scores higher here than in Whole view.
4. A photo with no herb says so and, in Pick a plant, suggests Whole view.

## 7. Identify several photos

1. Photos → pick three or more. Each photo gets its own row, filled in as it's identified.
2. A photo with no herb says so; an unreadable one says it couldn't be read.
3. Tap a result to open the species; back returns to the list.

## 8. Camera permission and devices without a camera

1. Revoke the camera permission in system settings, then open Identify: the explanation shows,
   and Photos still works.

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
2. Move the minimum confidence slider. Kill and relaunch: it's kept, and Identify uses it.
4. Full herb list, Share, Contact, About and Privacy policy open the right page or app.
5. With `how_to_cite` published (see `docs/content-publishing.md`), How to cite lists each work; Copy puts the reference on the clipboard and Open opens its link. With the key empty, the section is gone.

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
