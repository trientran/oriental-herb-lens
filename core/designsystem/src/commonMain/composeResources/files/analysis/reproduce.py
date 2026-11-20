#!/usr/bin/env python3
"""Recalculates the result screen's report from the exported embeddings, without the app.

    python reproduce.py [--out reproduced]

The app set every photo aside for training, checking or testing (examples.csv, "share") and
tested the model on the test photos it never learnt from. This script applies the exported model
(head.json) to the same photos' embeddings and compares accuracy, each species' recall and the
confusion matrix with what the app reported (manifest.json).

For a model made with "Calculate" (streaming linear discriminant analysis), it also calculates
the model afresh from the training and checking photos' embeddings and compares its weights and
results: the whole training is reproduced, not only the testing. Models trained in rounds
(gradient descent, with replay) depend on the order of random batches, so for those the exported
model is tested, not retrained.

Needs only numpy. Exits with status 1 if anything doesn't match.
"""
import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent


def load():
    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
    head = json.loads((HERE / "head.json").read_text(encoding="utf-8"))
    with open(HERE / "examples.csv", newline="", encoding="utf-8") as f:
        examples = list(csv.DictReader(f))
    embeddings = np.load(HERE / "embeddings.npy")
    if len(examples) != len(embeddings):
        sys.exit(f"examples.csv has {len(examples)} rows but embeddings.npy {len(embeddings)}")
    return manifest, head, examples, embeddings


def normalised(x):
    """Each row scaled to length 1, in float32 as the app does (rows of zeros are left as they are)."""
    x = x.astype(np.float32)
    length = np.sqrt((x * x).sum(axis=1, keepdims=True, dtype=np.float32))
    return np.where(length == 0, x, x / np.where(length == 0, 1, length)).astype(np.float32)


def unpack(head):
    """head.json's weights as matrices: (hidden weights, hidden bias) or None, output weights, output bias."""
    d, h, c = head["dimensions"], head["hidden"], len(head["labels"])
    p = np.asarray(head["params"], dtype=np.float32)
    hidden = None
    at = 0
    if h > 0:
        hidden = (p[: h * d].reshape(h, d), p[h * d: h * d + h])
        at = h * d + h
    features = h if h > 0 else d
    weights = p[at: at + c * features].reshape(c, features)
    bias = p[at + c * features:]
    return hidden, weights, bias


def probabilities(head, x):
    hidden, weights, bias = unpack(head)
    z = normalised(x)
    if hidden is not None:
        z = np.maximum(z @ hidden[0].T + hidden[1], 0).astype(np.float32)
    logits = (z @ weights.T + bias).astype(np.float64)
    logits -= logits.max(axis=1, keepdims=True)
    e = np.exp(logits)
    return e / e.sum(axis=1, keepdims=True)


def evaluate(head, x, y, classes):
    predicted = probabilities(head, x).argmax(axis=1)
    confusion = np.zeros((classes, classes), dtype=int)
    for actual, guess in zip(y, predicted):
        confusion[actual][guess] += 1
    recall = [confusion[c][c] / confusion[c].sum() if confusion[c].sum() else None for c in range(classes)]
    return np.trace(confusion) / len(y), recall, confusion, predicted


def slda(x, y, classes, shrinkage):
    """Streaming LDA's classifier from scratch (Hayes & Kanan, 2020), as the app calculates it."""
    z = normalised(x).astype(np.float64)
    d = z.shape[1]
    counts = np.bincount(y, minlength=classes)
    sums = np.zeros((classes, d))
    np.add.at(sums, y, z)
    scatter = z.T @ z
    between = sum(np.outer(sums[k], sums[k]) / counts[k] for k in range(classes) if counts[k])
    covariance = (1 - shrinkage) * (scatter - between) / len(z) + shrinkage * np.eye(d)
    weights = np.zeros((classes, d))
    bias = np.full(classes, -1e9)
    for k in range(classes):
        if counts[k]:
            mean = sums[k] / counts[k]
            weights[k] = np.linalg.solve(covariance, mean)
            bias[k] = -0.5 * mean @ weights[k]
    params = np.concatenate([weights.ravel(), bias]).astype(np.float32)
    return {"dimensions": d, "hidden": 0, "labels": None, "params": params}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default="reproduced", help="folder for the per-photo predictions (default: reproduced)")
    args = parser.parse_args()

    manifest, head, examples, embeddings = load()
    labels = manifest["labels"]
    classes = len(labels)
    report = manifest.get("report")
    share = np.array([e["share"] for e in examples])
    original = np.array([e["original"] == "true" for e in examples])
    y = np.array([int(e["label_index"]) for e in examples], dtype=int)
    calculated = manifest["update"] == "calculate"

    train = share == "train"
    validation = (share == "validation") & original
    test = (share == "test") & original
    # The photos the app tested on: the test photos, or, with too few photos to set any aside, what it learnt from
    learn = train | validation if calculated else train
    if test.any():
        tested, what = test, "test photos (never learnt from)"
    elif calculated or not validation.any():
        tested, what = learn, "the photos it learnt from (too few to set any aside)"
    else:
        tested, what = validation, "the checking photos (too few to set any aside for testing)"

    print(f"{manifest['name']}: {classes} species, backbone {manifest['backbone']}, made with '{manifest['update']}'")
    print(f"{len(examples)} embeddings: {train.sum()} for training, {validation.sum()} checking, {test.sum()} testing")
    ok = True

    accuracy, recall, confusion, predicted = evaluate(head, embeddings[tested], y[tested], classes)
    print(f"\nExported model on {what}: accuracy {accuracy:.4f} ({np.trace(confusion)} of {tested.sum()})")
    if report is not None:
        same_accuracy = abs(accuracy - report["accuracy"]) < 1e-6
        same_count = int(tested.sum()) == report["test_photos"]
        same_confusion = not report["confusion"] or np.array_equal(confusion, np.array(report["confusion"]))
        print(f"  App reported:   accuracy {report['accuracy']:.4f} on {report['test_photos']} photos")
        print(f"  Accuracy {'matches' if same_accuracy else 'DIFFERS'}; photo count {'matches' if same_count else 'DIFFERS'}; "
              f"confusion matrix {'matches' if same_confusion else 'DIFFERS'}")
        for c, label in enumerate(labels):
            app = report["per_class_recall"].get(label)
            mine = recall[c]
            if (app is None) != (mine is None) or (app is not None and abs(app - mine) > 1e-6):
                print(f"  Recall of {label} DIFFERS: {mine} here, {app} in the app")
                ok = False
        ok = ok and same_accuracy and same_count and same_confusion

    if calculated:
        fresh = slda(embeddings[learn], y[learn], classes, manifest.get("slda_shrinkage", 1e-4))
        _, exported_w, exported_b = unpack(head)
        _, fresh_w, fresh_b = unpack({**fresh, "labels": labels})
        scale = max(np.abs(exported_w).max(), 1e-12)
        weight_gap = np.abs(fresh_w - exported_w).max() / scale
        fresh_accuracy, _, fresh_confusion, fresh_predicted = evaluate({**fresh, "labels": labels}, embeddings[tested], y[tested], classes)
        same_predictions = np.array_equal(fresh_predicted, predicted)
        print(f"\nRecalculated from {learn.sum()} training and checking embeddings (streaming LDA, shrinkage {manifest.get('slda_shrinkage', 1e-4)}):")
        print(f"  Largest weight difference from the exported model: {weight_gap:.2e} of the largest weight")
        print(f"  Accuracy {fresh_accuracy:.4f}; predictions {'identical to' if same_predictions else 'DIFFERENT from'} the exported model's")
        ok = ok and weight_gap < 1e-3 and same_predictions

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows = np.flatnonzero(tested)
    with open(out / "test_predictions.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["row", "photo", "actual", "predicted", "correct"])
        for row, guess in zip(rows, predicted):
            e = examples[row]
            writer.writerow([row, e["photo"], e["label"], labels[guess], e["label"] == labels[guess]])
    print(f"\nWrote {out / 'test_predictions.csv'}")
    print("Reproduced: everything matches." if ok else "NOT reproduced: see the differences above.")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
