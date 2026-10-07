# Med Herb Lens

Identify medicinal herbs with your phone's camera, browse 4,799 plant species with their Vietnamese
and English names, and train image classifiers of your own, all on your device. Med Herb Lens runs
on Android, iOS and the web.

> **A research preview.** Med Herb Lens is a research project. Its herb model is our own: it was
> trained by our team, runs on your device and knows 2,721 species, but we don't have enough photos
> yet for most of them, so its results are often wrong. No other company's identification service
> is used. Never eat a plant or use it as medicine because of what the app says.

## Get the app

- In your browser: [med-herb-lens.pages.dev/app](https://med-herb-lens.pages.dev/app/)
- Android: [Google Play](https://play.google.com/store/apps/details?id=com.uri.lee.dl)
- iPhone and iPad: [App Store](https://apps.apple.com/app/id6819173768)

## What it does

- **Identify.** Point the camera at a plant or pick photos; the app suggests which species it may
  be, with its confidence. *Whole view* identifies everything in sight; *Pick a plant* finds each
  plant and identifies one at a time. Identification happens on the device and works offline.
- **Browse and search.** 4,799 species from the [GBIF](https://www.gbif.org) backbone taxonomy,
  searchable by Vietnamese (with or without diacritics), English or scientific name. Each species
  shows its names, classification and full GBIF record, with reference photos from GBIF and
  iNaturalist contributors and photos shared by users.
- **Saved.** Favourites and the herbs you viewed recently.
- **Contribute.** Signed-in users share photos of herbs (with where they were taken) and suggest
  Vietnamese names. Shared photos can be reported, and a contributor's photos hidden.
- **Train your own model.** Collect photos of the plants you care about, train a classifier on the
  device and try it with the camera. Share it as a standard `.tflite` file that carries its own
  species list, so it works in any app that runs TensorFlow Lite models; imported into Med Herb
  Lens on another device, it can go on learning there.
- **Research mode.** Run continual-learning experiments on the device: choose a dataset, scenarios,
  strategies and seeds, leave the device running (even with the screen off), then save one archive
  with every result, the device's measurements and the trained models.
- **How to cite.** The papers behind the app, in Profile, ready to copy.

## Privacy

Photos are identified on your device and leave it only when you choose to share them. The app sends
anonymous usage statistics (such as which herbs are identified) for research, with no name, email,
photo or place; you can turn them off in Profile. No ads, no tracking. Details in the
[privacy policy](https://med-herb-lens.pages.dev/pages/privacy-policy.html).

## Research and how to cite

If you use Med Herb Lens, its data or its code in your work, please cite:

Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2025). Med Herb Lens: A
prototype AI app for medicinal plant identification. *Procedia Computer Science*, 270, 2603–2612.
[https://doi.org/10.1016/j.procs.2025.09.382](https://doi.org/10.1016/j.procs.2025.09.382)

Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2026). Resource-efficient
continual learning for medicinal plant identification: A periodic retraining approach for
edge-deployed agricultural IoT applications. *IoT*, 7(3), 57.
[https://doi.org/10.3390/iot7030057](https://doi.org/10.3390/iot7030057)

## Built with

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) and
  [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/): one app for Android,
  iOS and the web
- [LiteRT](https://ai.google.dev/edge/litert) (TensorFlow Lite) for on-device identification, with
  [MediaPipe](https://ai.google.dev/edge/mediapipe) image embedders as the backbones of
  user-trained models; their final layers train in shared Kotlin
- [Firebase](https://firebase.google.com): sign-in, shared photos and name suggestions, usage
  statistics, remote settings and App Check
- [Cloudflare](https://www.cloudflare.com): the website and web app (Pages), photo and model
  storage (R2) and uploads (Workers)
- [GBIF](https://www.gbif.org): the species catalog and reference photos

## License

Med Herb Lens is released under the
[Creative Commons Attribution 4.0 International licence (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/):
you may share and adapt it for any purpose, as long as you give appropriate credit (see *Research
and how to cite*), link to the licence and say what you changed.

This covers the app's own work. Others' work it includes keeps its own terms: the catalog draws on
GBIF data, reference photos remain their creators' (each shown with its licence, many for
non-commercial use only), the MediaPipe embedders are under Apache 2.0, and each library under
its own licence.

## Contact

Trien P. Tran, University of New England:
[ttran72@myune.edu.au](mailto:ttran72@myune.edu.au) or [tptrien@gmail.com](mailto:tptrien@gmail.com)

<!-- about:end (the website's About page shows everything above this line) -->

## For developers

The repository holds every platform: `androidApp`, `iosApp` (Xcode), `webApp`, the shared
`composeApp`, `core/*` and `feature/*` modules, the Firebase rules (`firebase/`), the photo-upload
Worker (`workers/photo-upload`) and the website (`website/`).

```bash
./gradlew :androidApp:installDebug              # Android, on a connected device
./gradlew :webApp:jsBrowserDevelopmentRun       # the web app at http://localhost:8080
./gradlew :webApp:site                          # the whole website, with this README as its About page
```

iOS: `cd iosApp && pod install`, then open `iosApp.xcworkspace` in Xcode.

Secrets aren't in the repository: `local.properties`, `google-services.json`,
`GoogleService-Info.plist` and `Secrets.xcconfig` come from the project's maintainers.

More in `docs/`: the [release steps](docs/release.md), [publishing the herb model and catalog](docs/content-publishing.md),
[user-trained models and Research mode](docs/user-trained-models.md) and the
[manual test script](docs/manual-test-script.md).
