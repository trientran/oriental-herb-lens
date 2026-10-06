# User-trained models (Phase 7)

People train their own image classifier, in any field, on their own photos (plan §08). A
general-purpose **backbone** turns each photo into an **embedding** (a list of numbers that sums
it up); only a small final layer is trained, on the device, from those embeddings. The herb model
stays on ML Kit; only this feature uses LiteRT.

| Part | Where |
|---|---|
| Final-layer trainer (softmax, Adam, L2, early stopping) | `core:training`, pure Kotlin: the same code on every platform |
| `ImageEmbedder` | `core:domain` |
| Backbone on Android | LiteRT 1.4.2, Interpreter API (`core:ml` androidMain) |
| Backbone on iOS | LiteRT through `TensorFlowLiteSwift` 2.17.0 (`LiteRTEmbedder` in `MLKitBridge.swift`) |
| Backbone on the web | LiteRT.js (`core:ml` jsMain) |

## Spike benchmark (debug builds)

Profile → **Training benchmark (Phase 7)** embeds a labelled photo set with each backbone, trains
on 16 photos per class (and on 5) and tests on the other 8. Results show on screen and in the log.

**Backbones:** MediaPipe image embedders (Apache 2.0), 224 × 224 RGB input in [0, 1]:

    https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite
    https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/latest/mobilenet_v3_large.tflite

Put them in `test-images/backbones/`.

**Photos:** 24 GBIF photos for each of 8 herb species (most CC BY-NC: local testing only, never
shared or committed):

    tools/fetch_test_images.py --per-species 24 --out test-images/training

**Web:** serve `test-images` with CORS on port 8767, run the dev server, open Profile:

    cd test-images && python3 -c "import http.server as h
    class H(h.SimpleHTTPRequestHandler):
        def end_headers(self): self.send_header('Access-Control-Allow-Origin', '*'); super().end_headers()
    h.ThreadingHTTPServer(('127.0.0.1', 8767), H).serve_forever()"

**Android:** copy into the app's folder, then install a debug build:

    adb push test-images/training/. /sdcard/Android/data/com.uri.lee.dl/files/training/
    adb push test-images/backbones/. /sdcard/Android/data/com.uri.lee.dl/files/backbones/

**iOS:** install a debug build, then copy into its Documents folder:

    xcrun devicectl device copy to --device <id> --domain-type appDataContainer \
        --domain-identifier com.uri.lee.dl --source test-images/training --destination Documents/training

(and the same for `backbones`).

## Results (6 Oct 2026)

8 classes; test set: 8 photos per class (64). Embedding time per photo after a warm-up.

| Device | Backbone | Per photo | Train, 16/class | Accuracy | Train, 5/class | Accuracy |
|---|---|---|---|---|---|---|
| Laptop browser (Mac, Chromium, Wasm) | small (1,024-d) | 2.6 ms | 0.39 s | 66 % | 0.13 s | 61 % |
| | large (1,280-d) | 6.7 ms | 0.32 s | 69 % | 0.06 s | 61 % |
| Redmi Note 12 (Snapdragon 685, Android 15) | small | 24 ms | 1.6 s | 66 % | 0.5 s | 61 % |
| | large | 44 ms | 1.3 s | 69 % | 0.25 s | 63 % |
| iPhone 17 Pro Max (debug build) | small | 7.9 ms | 0.87 s | 66 % | 0.28 s | 61 % |
| | large | 10 ms | 1.2 s | 72 % | 0.16 s | 56 % |

Accuracy is nearly the same everywhere (the same trainer; each platform resizes photos a little
differently). It's modest here because these are 8 similar herbs from mixed-quality photos, a
hard case for a general-purpose backbone; with 64 test photos, a few points either way is noise.
Training on the iPhone is slower than in the browser because debug builds of Kotlin/Native aren't
optimised. No older iPhone was available; the slowest device, the Redmi Note 12, is still fast enough.

## Decisions from the spike

- **Backbone: mobilenet_v3_large** (10.9 MB, 1,280-d). Fast enough on a mid-range Android phone
  (44 ms a photo: 100 photos in under 5 s) and a little more accurate with enough photos.
  Downloaded on first use through the content pipeline (R2, Remote Config, checksum), so the apps
  don't grow; the web fetches it from R2.
- **Training stays in shared Kotlin** (`core:training`): at most 1.6 s for 128 photos on the
  slowest device, so no TensorFlow training API is needed on any platform.
- **LiteRT from Kotlin:** Android calls the Interpreter API directly; iOS through a small Swift
  bridge (`TensorFlowLiteSwift` links alongside ML Kit without conflicts); the web through
  LiteRT.js, like the herb model.
- **Model pack:** a small JSON file: the backbone's name and SHA-256, the class names, the
  weights and biases, and the accuracy figures. A pack only works with the backbone it was trained on.
