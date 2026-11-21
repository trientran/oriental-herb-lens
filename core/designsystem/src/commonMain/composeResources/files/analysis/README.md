# A model trained in Med Herb Lens, with scripts to use and check it

This folder was exported from the app's Train tab ("Export with Python scripts"). It holds the
trained image classifier, the embedding of every photo it was trained and tested on, and three
Python scripts that work without the app.

## What's here

| File | What it is |
|---|---|
| `model.tflite` | The classifier: photo in, one probability per species out. Standard LiteRT (TensorFlow Lite), with the labels inside. |
| `labels.txt` | The species, one per line, in the model's output order. |
| `head.json` | The trained layers on top of the frozen backbone (weights as numbers). |
| `manifest.json` | The backbone, the training settings, how photos are read, and the report the app showed. |
| `embeddings.npy` | Each photo's embedding from the backbone (float32, one row per photo; `numpy.load`). |
| `examples.csv` | One line per row of `embeddings.npy`: the photo's number, species, and whether the app used it for training, checking or testing (`share`). `original` is false for the extra cropped copies some settings add. |
| `predict.py` | Classifies a folder of photos. |
| `evaluate.py` | Measures the model on labelled photos. |
| `reproduce.py` | Recalculates the app's report from the embeddings. |

The photos themselves are not included: they stay on the device.

## Set up

Python 3.9 to 3.12:

    python -m venv .venv
    source .venv/bin/activate        # on Windows: .venv\Scripts\activate
    pip install -r requirements.txt

## Use

Check the app's results (needs only numpy):

    python reproduce.py

It applies the exported model to the test photos' embeddings and compares accuracy, each
species' recall and the confusion matrix with the app's report. For a model made with
"Calculate" (streaming linear discriminant analysis, the default) it also calculates the model
again from the training photos' embeddings and checks that it is the same model.

Classify new photos:

    python predict.py path/to/photos --out predictions.csv

Measure the model on your own labelled photos, in one subfolder per species named exactly as in
`labels.txt`:

    python evaluate.py path/to/labelled_photos --out results

## How photos are read

As in the app: turned upright from the photo's EXIF orientation, the centre square cut out,
resized to 224 × 224 (bilinear) and scaled to RGB values in [0, 1]. The backbone is part of
`model.tflite`. Phones and browsers decode and resize photos slightly differently, so
probabilities can differ in the last digits from the app's, and very rarely a close call can go
the other way.
