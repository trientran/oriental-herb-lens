#!/usr/bin/env python3
"""Downloads a few GBIF photos of herbs the model knows, for trying the scan screens by hand.

    tools/fetch_test_images.py                 # default species, 3 photos each
    tools/fetch_test_images.py --per-species 5 --push   # also copy them to a running emulator
    tools/fetch_test_images.py --per-species 24 --out test-images/training   # a labelled set (Phase 7)

Photos land in test-images/ (or --out; git-ignored: most are CC BY-NC, and they're for local testing only)
with credits.csv naming each creator and licence. --push copies them to the device's
Pictures/HerbLens folder through adb, where the photo picker finds them.
"""

import argparse
import csv
import json
import os
import subprocess
import urllib.request

# GBIF species key → name, all labels of the bundled model
DEFAULT_SPECIES = {
    3035652: "Polyscias fruticosa (Đinh lăng)",
    2927192: "Mentha arvensis (Bạc hà)",
    3152707: "Abelmoschus esculentus (Mướp tây)",
    2766278: "Cordyline fruticosa (Huyết dụ)",
    3034128: "Centella asiatica (Rau má)",
    5384931: "Houttuynia cordata (Diếp cá)",
    3120946: "Artemisia vulgaris (Ngải cứu)",
    2705275: "Cymbopogon citratus (Sả)",
}
OUT = "test-images"
HEADERS = {"User-Agent": "herb-lens-test-images/1.0"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as response:
        return response.read()


def photos(species_key, count):
    page = json.loads(get(
        "https://api.gbif.org/v1/occurrence/search?mediaType=StillImage&limit=50&taxon_key=%d" % species_key))
    found = []
    for occurrence in page.get("results", []):
        for media in occurrence.get("media", []):
            url = media.get("identifier") or ""
            if media.get("type") == "StillImage" and url.split("?")[0].lower().endswith(("/original.jpg", "/original.jpeg")):
                # The medium size (~500 px) is enough to test, and much smaller
                found.append((url.replace("/original.", "/medium."), media, occurrence["key"]))
                break
        if len(found) == count:
            break
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--per-species", type=int, default=3)
    parser.add_argument("--out", default=OUT, help="folder for the photos and credits.csv")
    parser.add_argument("--push", action="store_true", help="copy to the connected emulator with adb")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    credits = []
    for key, name in DEFAULT_SPECIES.items():
        for i, (url, media, occurrence) in enumerate(photos(key, args.per_species), start=1):
            file = "%d_%d.jpg" % (key, i)
            with open(os.path.join(args.out, file), "wb") as out:
                out.write(get(url))
            credits.append([file, key, name, media.get("creator") or media.get("rightsHolder") or "",
                            media.get("license") or "", "https://www.gbif.org/occurrence/%d" % occurrence])
            print("%s  %s" % (file, name))
    with open(os.path.join(args.out, "credits.csv"), "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["file", "speciesKey", "species", "creator", "license", "source"])
        writer.writerows(credits)

    if args.push:
        target = "/sdcard/Pictures/HerbLens"
        subprocess.run(["adb", "-s", args.serial, "shell", "mkdir", "-p", target], check=True)
        for row in credits:
            subprocess.run(["adb", "-s", args.serial, "push", os.path.join(args.out, row[0]), target], check=True,
                           stdout=subprocess.DEVNULL)
            # Tell the media store, so the photo picker lists them straight away
            subprocess.run(["adb", "-s", args.serial, "shell", "am", "broadcast", "-a",
                            "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file://%s/%s" % (target, row[0])],
                           check=True, stdout=subprocess.DEVNULL)
        print("Copied %d photos to %s on %s" % (len(credits), target, args.serial))


if __name__ == "__main__":
    main()
