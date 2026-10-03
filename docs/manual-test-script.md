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

## 1. Launch and sign-in

1. Fresh install, launch. Expect the sign-in screen; sign in with Google.
2. Main screen loads with the three tabs (all herbs, favourites, history).
3. Kill and relaunch. Expect no sign-in prompt.

## 2. Home tabs

1. All herbs: scroll past the first 10 items. More load (paging) with no duplicates.
2. Favourites and history show what the test account saved earlier.
3. Switch the device language between Vietnamese and English, relaunch. The list sort order and
   names follow the language (Vietnamese name vs Latin name).

## 3. Text and voice search

Search runs on the device, so check it in airplane mode too.

1. Each query finds the expected species first:

   | Query | Expected first result |
   |---|---|
   | `dinh lang`, `Đinh lăng`, `lang dinh`, `dinh lanh` | Đinh lăng · *Polyscias fruticosa* |
   | `bach bo` | Bách bộ · *Stemona tuberosa* |
   | `okra` | *Abelmoschus esculentus* |
   | `fruticosa` | both *Polyscias* and *Cordyline fruticosa* near the top |

2. The matched part of the name is bold; scientific names are italic.
3. Microphone: say "đinh lăng". The spoken text appears in the search box with results.

## 4. Herb details

1. Open a herb from each of search, all herbs and favourites.
2. Visit every bottom tab: overview, dosing, caution, images.
3. Like, then unlike. The favourites tab on the main screen updates without a restart.
4. The herb appears at the top of history.
5. Images tab: open an image full-screen; the location map shows where it was taken, if known.

## 5. Live camera

1. Main screen → camera. Grant the camera permission.
2. Point at a known herb. The recognised name and confidence appear and update live.
3. Point at a blank wall. No confident result is shown.
4. Change the confidence level in the camera screen, leave and come back. The value is kept.

## 6. Object camera (multi-object mode)

1. In the camera screen, switch to object mode.
2. Hold on a herb. The reticle confirms the object, then results appear in the bottom sheet.
3. Settings → turn auto search off. Detection now waits for a manual tap.

## 7. Single image

1. Main screen → single image, pick a photo.
2. With objects mode on: dots appear on detected objects; tapping one shows its results.
3. With objects mode off: one result list for the whole image.
4. Leave and return. Both the objects-mode switch and the confidence level are kept.

## 8. Multiple images

1. Main screen → multiple images, pick three photos.
2. Each photo gets its own result row; a photo with no match shows an empty result.

## 9. Upload images

1. From a herb's images tab, add two photos, pick a location, upload.
2. The progress count reaches 2, then the completion message appears.
3. The new images show in the images tab.

## 10. Suggest a Vietnamese name

1. On a test herb's overview, tap the Vietnamese name and enter a new one.
2. The update button is disabled until the text differs from the current name.
3. Submit. The screen closes and the new name shows on the details screen.
4. Latin name, English name, overview, dosing and caution are read-only; empty ones show
   "Not available yet".

## 11. Settings

1. Open Lens Settings. Change the preview size and the confirmation times.
2. Kill and relaunch. Every value is kept.

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
