#!/usr/bin/env python3
"""Checks, uploads and announces a new herb catalog or model for the app's content sync.

    tools/publish_content.py catalog path/to/species_list.csv
    tools/publish_content.py model path/to/herb_model.tflite --catalog path/to/species_list.csv

Each run:
  1. validates the file the way the app will (catalog columns; every model label in the catalog)
  2. normalises catalog text to Unicode NFC, since the app compares names as NFC
  3. uploads it to R2 under a new, never-overwritten name, e.g. models/herb_model-20261001-ab12cd34.tflite
  4. downloads it back from the public URL and checks the SHA-256
  5. prints the two Remote Config values to set

Publish the catalog before a model that needs it. Update Remote Config only after step 4 passes,
so no app is ever sent a URL that doesn't exist yet. To roll back, set Remote Config back to the
previous values: old files are never deleted.

Needs Python 3.9+ and Cloudflare's wrangler CLI (`npm i -g wrangler`, then `wrangler login`).
Settings come from flags or the environment: HERB_CONTENT_BUCKET, HERB_CONTENT_BASE_URL.
"""

import argparse
import csv
import datetime
import hashlib
import io
import os
import shutil
import subprocess
import sys
import tempfile
import unicodedata
import urllib.request
import zipfile

REQUIRED_COLUMNS = {
    "speciesKey", "canonicalName", "authorship", "family", "genus", "vietnameseName", "vernacularName",
}
CACHE_CONTROL = "public, max-age=31536000, immutable"


def fail(message):
    sys.exit("error: " + message)


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


def read_catalog_ids(text):
    reader = csv.DictReader(io.StringIO(text))
    missing = REQUIRED_COLUMNS - set(reader.fieldnames or [])
    if missing:
        fail("catalog is missing columns: %s" % sorted(missing))
    ids, bad_rows = set(), []
    for line, row in enumerate(reader, start=2):
        key = (row.get("speciesKey") or "").strip()
        if not key.isdigit() or not (row.get("canonicalName") or "").strip():
            bad_rows.append(line)
        else:
            ids.add(key)
    return ids, bad_rows


def prepare_catalog(path):
    raw = open(path, "rb").read()
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        fail("catalog is not UTF-8")
    normalized = unicodedata.normalize("NFC", text)
    changed = sum(1 for a, b in zip(text.splitlines(), normalized.splitlines()) if a != b)
    ids, bad_rows = read_catalog_ids(normalized)
    if not ids:
        fail("catalog has no species")
    if bad_rows:
        fail("malformed rows (the app would skip them): lines %s" % bad_rows[:20])
    print("catalog: %d species, %d lines normalised to NFC" % (len(ids), changed))
    return normalized.encode("utf-8"), "catalog/herbs", ".csv", "text/csv; charset=utf-8"


def prepare_model(path, catalog_path):
    data = open(path, "rb").read()
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as model:
            labels = [l.strip() for l in model.read("labels.txt").decode("utf-8").splitlines() if l.strip()]
    except (zipfile.BadZipFile, KeyError):
        fail("model has no embedded labels.txt (export it with TFLite metadata)")
    if not catalog_path:
        fail("--catalog is required for a model, to check every label has a catalog entry")
    catalog_text = unicodedata.normalize("NFC", open(catalog_path, "rb").read().decode("utf-8-sig"))
    ids, _ = read_catalog_ids(catalog_text)
    missing = [label for label in labels if label not in ids]
    if missing:
        fail("%d model labels are not in the catalog, e.g. %s. Publish a catalog that covers them first."
             % (len(missing), missing[:5]))
    print("model: %d labels, all in the catalog" % len(labels))
    return data, "models/herb_model", ".tflite", "application/octet-stream"


def wrangler_command():
    if shutil.which("wrangler"):
        return ["wrangler"]
    if shutil.which("npx"):
        return ["npx", "--yes", "wrangler"]
    fail("wrangler not found: npm i -g wrangler && wrangler login")


def upload(bucket, key, data, content_type):
    with tempfile.NamedTemporaryFile(delete=False) as tmp:
        tmp.write(data)
    try:
        subprocess.run(
            wrangler_command() + [
                "r2", "object", "put", "%s/%s" % (bucket, key),
                "--file", tmp.name,
                "--content-type", content_type,
                "--cache-control", CACHE_CONTROL,
                "--remote",
            ],
            check=True,
        )
    finally:
        os.unlink(tmp.name)


def verify_public(url, expected_sha256):
    # Cloudflare rejects urllib's default "Python-urllib" user agent with 403.
    request = urllib.request.Request(url, headers={"User-Agent": "herb-lens-publisher/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response:
        actual = sha256_hex(response.read())
    if actual != expected_sha256:
        fail("public URL returned different content (sha256 %s). Is the custom domain set up?" % actual)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("kind", choices=["catalog", "model"])
    parser.add_argument("file")
    parser.add_argument("--catalog", help="catalog CSV the model will be used with (model only)")
    parser.add_argument("--bucket", default=os.environ.get("HERB_CONTENT_BUCKET"), help="R2 bucket name")
    parser.add_argument("--base-url", default=os.environ.get("HERB_CONTENT_BASE_URL"),
                        help="public base URL of the bucket's custom domain, e.g. https://content.example.com")
    parser.add_argument("--dry-run", action="store_true", help="validate and print what would happen")
    args = parser.parse_args()

    if args.kind == "catalog":
        data, prefix, suffix, content_type = prepare_catalog(args.file)
    else:
        data, prefix, suffix, content_type = prepare_model(args.file, args.catalog)

    digest = sha256_hex(data)
    key = "%s-%s-%s%s" % (prefix, datetime.date.today().strftime("%Y%m%d"), digest[:8], suffix)
    remote_config_prefix = "herb_catalog" if args.kind == "catalog" else "herb_model"

    if args.dry_run:
        print("dry run: would upload %d bytes as %s (sha256 %s)" % (len(data), key, digest))
        return
    if not args.bucket or not args.base_url:
        fail("set --bucket and --base-url (or HERB_CONTENT_BUCKET / HERB_CONTENT_BASE_URL)")

    url = "%s/%s" % (args.base_url.rstrip("/"), key)
    upload(args.bucket, key, data, content_type)
    verify_public(url, digest)
    print("\nUploaded and verified. Now set in Firebase Remote Config, then publish the changes:")
    print("  %s_url    = %s" % (remote_config_prefix, url))
    print("  %s_sha256 = %s" % (remote_config_prefix, digest))


if __name__ == "__main__":
    main()
