#!/usr/bin/env python3
"""Classifies every photo in a folder with model.tflite, reading photos as the app does, and
writes one CSV row per photo: the most likely species and its probability, then the runners-up.

    python predict.py PHOTOS_FOLDER [--model model.tflite] [--out predictions.csv] [--top 3]

Photos are read like the app reads them: turned upright from their EXIF orientation, the centre
square cut out, resized to the model's input size (bilinear) and scaled to RGB values in [0, 1].
Phones and browsers decode and resize photos slightly differently, so a probability can differ in
the last digits from the app's; the predicted species almost never does.
"""
import argparse
import csv
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
IMAGE_TYPES = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif", ".tif", ".tiff", ".heic"}


def interpreter_class():
    """LiteRT's interpreter, from whichever package is installed."""
    try:
        from ai_edge_litert.interpreter import Interpreter
        return Interpreter
    except ImportError:
        pass
    try:
        from tflite_runtime.interpreter import Interpreter
        return Interpreter
    except ImportError:
        pass
    try:
        import tensorflow as tf
        return tf.lite.Interpreter
    except ImportError:
        sys.exit("Install LiteRT first: pip install -r requirements.txt")


def read_photo(path, size):
    """The photo as the app gives it to the model: shape (1, size, size, 3), float32 in [0, 1]."""
    with Image.open(path) as image:
        image = ImageOps.exif_transpose(image).convert("RGB")
    width, height = image.size
    side = min(width, height)
    left, top = (width - side) // 2, (height - side) // 2
    image = image.crop((left, top, left + side, top + side)).resize((size, size), Image.BILINEAR)
    return (np.asarray(image, dtype=np.float32) / 255.0)[np.newaxis]


class Model:
    """A model exported by the app (or any image classifier with one image input and one output)."""

    def __init__(self, path):
        path = Path(path)
        self.interpreter = interpreter_class()(model_path=str(path))
        self.interpreter.allocate_tensors()
        self.input = self.interpreter.get_input_details()[0]
        self.output = self.interpreter.get_output_details()[0]
        shape = list(self.input["shape"])
        self.channels_first = shape[1] == 3 and shape[3] != 3
        self.size = int(shape[2] if self.channels_first else shape[1])
        labels = path.with_name("labels.txt")
        if labels.exists():
            self.labels = [line.strip() for line in labels.read_text(encoding="utf-8").splitlines() if line.strip()]
        else:
            self.labels = [f"class {i}" for i in range(int(self.output["shape"][-1]))]

    def probabilities(self, photo):
        x = read_photo(photo, self.size)
        if self.channels_first:
            x = x.transpose(0, 3, 1, 2)
        self.interpreter.set_tensor(self.input["index"], x)
        self.interpreter.invoke()
        return self.interpreter.get_tensor(self.output["index"])[0].astype(np.float64)


def photos(folder):
    """Every photo under [folder], in a stable order."""
    folder = Path(folder)
    return sorted(p for p in folder.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_TYPES and not p.name.startswith("."))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", help="folder of photos (subfolders are searched too)")
    parser.add_argument("--model", default=str(HERE / "model.tflite"), help="the model (default: model.tflite next to this script)")
    parser.add_argument("--out", default="predictions.csv", help="CSV to write (default: predictions.csv)")
    parser.add_argument("--top", type=int, default=3, help="species to list per photo (default: 3)")
    args = parser.parse_args()

    model = Model(args.model)
    found = photos(args.folder)
    if not found:
        sys.exit(f"No photos in {args.folder}")
    top = max(1, min(args.top, len(model.labels)))
    header = ["file"]
    for rank in range(1, top + 1):
        header += [f"label_{rank}", f"probability_{rank}"]
    with open(args.out, "w", newline="", encoding="utf-8") as out:
        writer = csv.writer(out)
        writer.writerow(header)
        for photo in found:
            p = model.probabilities(photo)
            order = np.argsort(-p, kind="stable")[:top]
            row = [photo.relative_to(args.folder).as_posix()]
            for i in order:
                row += [model.labels[i], f"{p[i]:.6f}"]
            writer.writerow(row)
    print(f"Classified {len(found)} photos; wrote {args.out}")


if __name__ == "__main__":
    main()
