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

## Study protocol (decided 6 Oct 2026)

For the paper "Training on the edge: On-device continual learning for invasive plant
classification in a North American context". The app runs every configuration itself (Research
mode) and exports the CSVs; nothing is measured by hand. Datasets and trained models go to Zenodo.

**Dataset:** about 7,000 photos of 20 invasive species, one folder per species.

**Splits:** stratified per species, random for each seed: 70 % train, 15 % validation (early
stopping), 15 % test. The test set is never trained on.

**Seeds:** 10 per configuration (seeds 1–10). Each seed fixes the split, the order species arrive
in and the training order. `kotlin.random.Random(seed)` gives the same sequence on every
platform, so a seed means the same split and order on every device.

**Scenarios:**
- **Class-incremental:** 5 steps of 4 species, in a random order for each seed.
- **Data-incremental:** all 20 species from the start; 5 steps, each adding 20 % of the training photos.

**Strategies:** joint retraining on all stored embeddings (upper bound); naive fine-tuning on new
data only (lower bound); replay with a buffer of 5, 10, 20 and 50 embeddings per species; class
prototypes (nearest class mean, no training).

**Backbones:** mobilenet_v3_large (main); mobilenet_v3_small (efficiency comparison).

**Devices:** Redmi Note 12 (mid-range Android), iPhone 17 Pro Max, a laptop browser (Chrome,
macOS). Embeddings are computed once per backbone and device, then reused by every seed and
strategy; their cost is reported separately.

**Measured by the app, per step:** accuracy, top-3 accuracy, balanced accuracy, macro and
per-species precision, recall and F1, Cohen's κ, log loss, calibration error (ECE), the confusion
matrix; accuracy on old and new species; average incremental accuracy, forgetting and backward
transfer; training time and epochs; peak memory; CPU time; energy (charge counter, Android only);
thermal state. Per device: embedding and prediction time (median, 90th and 99th percentile),
model and embedding-store sizes, device and app details.

**Analysis:** mean ± SD and 95 % confidence intervals over the 10 seeds; strategies compared with
the Friedman test and Wilcoxon signed-rank post-hoc tests (paired by seed, Holm correction), with
effect sizes; devices compared on time, memory and energy, and accuracy checked for equivalence
(TOST), since the same seed should give the same result up to floating-point and image-resizing
differences.

## How it runs and what it saves

- **Backbone:** LiteRT on every platform: the LiteRT Interpreter on Android, TensorFlowLiteSwift on
  iOS, LiteRT.js (WebAssembly) on the web. Embedding uses the same .tflite file everywhere.
- **Training:** pure Kotlin (`core:training`), compiled for each platform, so the arithmetic is the
  same on every device.
- **Saved model:** a standard `.tflite`. The backbone's graph is kept byte for byte, and the head
  is added after the embedding as L2_NORMALIZATION, FULLY_CONNECTED (+ReLU with a hidden layer)
  and SOFTMAX layers. One file goes from a 224 × 224 RGB image (float, 0–1) to one probability per
  class, with `labels.txt` giving the class order. It runs in LiteRT/TensorFlow Lite anywhere.
  Checked with LiteRT.js: the same answer as the Kotlin head on 8/8 photos, probabilities within
  1e-6.
- **Resuming training:** the head is also saved as JSON (`ModelPack`) so the app can continue
  training it later without reading weights back out of the .tflite.

### Head with a hidden layer (6 Oct 2026, laptop Chrome, 8 species, 16 photos each)

| Head | mobilenet_v3_large | mobilenet_v3_small |
|---|---|---|
| Softmax layer only | 72 % | 64 % |
| 100-unit ReLU layer + softmax (Teachable Machine's design) | 72 % | 63 % |

The hidden layer doesn't help with this little data. The softmax-only head stays the default; the
hidden layer is a setting and an ablation in the study.

## Research mode (debug builds)

Profile → **Research mode (Phase 7)** runs the study protocol on the device it's on and saves
everything in one zip. Nothing is measured or typed by hand.

1. **Backbones** go where the benchmark's do: `Android/data/com.uri.lee.dl/files/backbones/` (adb),
   `Documents/backbones/` (iOS, devicectl), or the local test-photo server on port 8767 (web).
2. **Dataset:** one folder per species, photos inside (a wrapping folder is fine; other files,
   hidden files and `__MACOSX` are skipped).
   - Android: **Choose folder** (starts in Download) or **Choose zip**.
   - iOS: **Choose folder** in Files; to use a zip, tap it in Files first to unpack it.
   - Web (Chrome): **Choose folder**.
3. Pick backbones, scenarios, strategies, seeds (1–10) and, optionally, the 100-unit hidden layer.
   **Start**. The screen stays on; don't switch apps (iOS suspends apps in the background).
4. When it's done, **Save results**: Android asks where to save, iOS opens the share sheet (Save
   to Files, AirDrop), the web downloads the zip.

The zip (`herblens-research-<platform>-<time>.zip`) holds:

- `README.txt`: device, dataset (photos per class), the plan and the run's log.
- `results/runs.csv`, `steps.csv`, `per_class.csv`, `confusion.csv`, `task_accuracy.csv`,
  `epochs.csv`: one row per observation, each naming device, platform, app version, backbone,
  scenario, strategy, seed and training settings, so files from several devices stack directly.
  `steps.csv` holds the device readings: training time, memory before/after, CPU time, battery
  charge used (Android), battery level, charging state, thermal state.
- `results/embedding.csv`: backbone load time and per-photo embedding time (median, mean, p90).
- `models/<backbone>/model.tflite`: a standalone classifier trained on every photo, with its
  `labels.txt` inside and next to it; `head.json` for continuing training in the app.

For energy figures on Android, run unplugged: the charge counter only falls while discharging.
