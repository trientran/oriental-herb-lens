#!/usr/bin/env python3
"""Makes the website's and the web app's icons from the Android app icon, so all three match.

    pip install pillow
    tools/make_favicons.py

Writes favicon.ico (16, 32 and 48 px), icon-192.png, icon-512.png and apple-touch-icon.png (180 px,
on white, as iOS shows no transparency) into website/ and the web app's resources.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"
TARGETS = [ROOT / "website", ROOT / "webApp/src/jsMain/resources"]


def main():
    icon = Image.open(SOURCE).convert("RGBA")
    for target in TARGETS:
        icon.save(target / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
        for size in (192, 512):
            icon.resize((size, size), Image.LANCZOS).save(target / ("icon-%d.png" % size), optimize=True)
        touch = Image.new("RGBA", icon.size, "white")
        touch.alpha_composite(icon)
        touch.convert("RGB").resize((180, 180), Image.LANCZOS).save(target / "apple-touch-icon.png", optimize=True)
        print("icons written to %s" % target.relative_to(ROOT))


if __name__ == "__main__":
    main()
