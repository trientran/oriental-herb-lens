#!/usr/bin/env python3
"""Exports every species' user photo URLs, for building training datasets.

Replaces the app's admin screen export, which can't run once the Firestore security rules are
deployed (they don't let clients list the `herbs` collection). This reads Firestore with a
service account, which the rules don't apply to.

    pip install google-cloud-firestore
    export GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account.json
    tools/export_photo_urls.py --catalog androidApp/assets/herb_catalog.csv

Writes herbs_<timestamp>.zip containing, like the admin export:
  <speciesKey>.csv   one photo URL per line
  herbs.csv          speciesKey,vietnameseName for every species with photos

Create the service account in Google Cloud console → IAM → Service accounts, with the
"Cloud Datastore User" role, and keep its key file out of the repository.
"""

import argparse
import csv
import io
import time
import unicodedata
import zipfile


def preferred_vietnamese_names(catalog_path):
    """speciesKey -> first Vietnamese name in the catalog (names are separated by ; or ,)."""
    names = {}
    if not catalog_path:
        return names
    with open(catalog_path, encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            raw = unicodedata.normalize("NFC", row.get("vietnameseName") or "")
            depth, current, first = 0, "", None
            for ch in raw:  # split on ; or , outside parentheses, like the app
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth = max(0, depth - 1)
                if ch in ";," and depth == 0:
                    if current.strip():
                        first = current.strip()
                        break
                    current = ""
                    continue
                current += ch
            names[row["speciesKey"].strip()] = first or current.strip()
    return names


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--catalog", help="catalog CSV, to name species in herbs.csv")
    parser.add_argument("--project", default="oriental-herb-lens-41d17")
    parser.add_argument("--out", default=None, help="output zip path")
    args = parser.parse_args()

    from google.cloud import firestore  # imported here so --help works without the package

    db = firestore.Client(project=args.project)
    names = preferred_vietnamese_names(args.catalog)
    out = args.out or "herbs_%d.zip" % int(time.time())
    species_count = photo_count = 0

    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as archive:
        index = io.StringIO()
        writer = csv.writer(index, lineterminator="\n")
        for snapshot in db.collection("herbs").stream():
            images = (snapshot.to_dict() or {}).get("images") or {}
            if not images:
                continue
            archive.writestr("%s.csv" % snapshot.id, "\n".join(images.keys()))
            writer.writerow([snapshot.id, names.get(snapshot.id, "")])
            species_count += 1
            photo_count += len(images)
        archive.writestr("herbs.csv", index.getvalue())

    print("Wrote %s: %d photos of %d species" % (out, photo_count, species_count))


if __name__ == "__main__":
    main()
