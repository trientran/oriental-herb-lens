#!/usr/bin/env python3
"""Measures model.tflite on labelled photos: one subfolder per species, named exactly as in
labels.txt. Prints accuracy, balanced accuracy and each species' precision, recall and F1, and
writes them with the confusion matrix and every photo's prediction to an output folder.

    python evaluate.py LABELLED_FOLDER [--model model.tflite] [--out results]

Photos are read as the app reads them (see predict.py). Subfolders whose names aren't species of
the model are skipped, with a warning.
"""
import argparse
import csv
import sys
from pathlib import Path

import numpy as np

from predict import HERE, Model, photos


def scores(confusion):
    """Per class (precision, recall, F1, support) from a [actual][predicted] matrix; None where undefined."""
    out = []
    for c in range(len(confusion)):
        tp = confusion[c][c]
        predicted = confusion[:, c].sum()
        actual = confusion[c].sum()
        precision = tp / predicted if predicted else None
        recall = tp / actual if actual else None
        if precision is None or recall is None:
            f1 = 0.0 if actual else None
        else:
            f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        out.append((precision, recall, f1, int(actual)))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", help="folder with one subfolder of photos per species")
    parser.add_argument("--model", default=str(HERE / "model.tflite"), help="the model (default: model.tflite next to this script)")
    parser.add_argument("--out", default="results", help="folder for the result files (default: results)")
    args = parser.parse_args()

    model = Model(args.model)
    index = {label: i for i, label in enumerate(model.labels)}
    root = Path(args.folder)
    rows = []
    for sub in sorted(p for p in root.iterdir() if p.is_dir()):
        if sub.name not in index:
            print(f"Skipping {sub.name}: not a species of this model", file=sys.stderr)
            continue
        for photo in photos(sub):
            rows.append((photo, index[sub.name]))
    if not rows:
        sys.exit(f"No photos in subfolders of {args.folder} named as in labels.txt")

    classes = len(model.labels)
    confusion = np.zeros((classes, classes), dtype=int)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    with open(out / "predictions.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["file", "actual", "predicted", "probability", "correct"])
        for photo, actual in rows:
            p = model.probabilities(photo)
            predicted = int(np.argmax(p))
            confusion[actual][predicted] += 1
            writer.writerow([photo.relative_to(root).as_posix(), model.labels[actual], model.labels[predicted],
                             f"{p[predicted]:.6f}", predicted == actual])

    per_class = scores(confusion)
    n = int(confusion.sum())
    accuracy = np.trace(confusion) / n
    present = [r for _, r, _, s in per_class if s > 0]
    balanced = float(np.mean(present))
    with open(out / "per_class.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["species", "precision", "recall", "f1", "photos"])
        for label, (precision, recall, f1, support) in zip(model.labels, per_class):
            writer.writerow([label] + ["" if v is None else f"{v:.6f}" for v in (precision, recall, f1)] + [support])
    with open(out / "confusion.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["actual \\ predicted"] + model.labels)
        for label, row in zip(model.labels, confusion):
            writer.writerow([label] + list(row))

    print(f"{n} photos, {len(present)} species")
    print(f"Accuracy: {accuracy:.4f} ({np.trace(confusion)} of {n})")
    print(f"Balanced accuracy: {balanced:.4f}")
    for label, (_, recall, _, support) in zip(model.labels, per_class):
        if support:
            print(f"  {label}: {recall:.4f} of {support}")
    print(f"Wrote predictions.csv, per_class.csv and confusion.csv to {out}")


if __name__ == "__main__":
    main()
