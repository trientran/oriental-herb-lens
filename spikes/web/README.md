# Phase 6 spike: the web app (temporary)

Answers the questions in the migration plan (§09) before any web feature work. Results are
recorded in the plan; this folder goes once the decision is made.

- `identify.html`: the herb model in the browser, on the GBIF test photos (`tools/fetch_test_images.py`).
  Serve the repository root (`python3 -m http.server 8766`) and open `/spikes/web/identify.html`.
  It tries MediaPipe (which can't run this model); the working path, LiteRT.js, is in the plan.
- `compose/` (`:spikes:webCompose`): Compose Multiplatform in the browser, built as Kotlin/JS and
  as Kotlin/Wasm: loads the real catalog and searches it with the shared search index. The JS
  build also checks Firebase from the browser: App Check (Fraud Defense site key), a Firestore
  read, Google sign-in (popup) and the photo Worker (CORS; a bad species key, so nothing is stored).

      ./gradlew :spikes:webCompose:jsBrowserDevelopmentRun      # opens http://localhost:8080
      ./gradlew :spikes:webCompose:jsBrowserDistribution        # production bundle in build/dist/js
      ./gradlew :spikes:webCompose:wasmJsBrowserDistribution    # the same as Wasm
