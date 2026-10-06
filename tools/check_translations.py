#!/usr/bin/env python3
"""Checks the app's translations against English (core:designsystem Compose resources).

Every language must have every string, the same placeholders (%1$s, %2$d…) and the plural
forms its language needs (CLDR). Run in CI; prints what's wrong and exits 1 if anything is.

    python3 tools/check_translations.py
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RESOURCES = Path(__file__).resolve().parent.parent / "core/designsystem/src/commonMain/composeResources"
PLURALS = {
    "vi": {"other"}, "zh": {"other"},
    "fr": {"one", "many", "other"}, "es": {"one", "many", "other"},
    "ru": {"one", "few", "many", "other"},
    "ar": {"zero", "one", "two", "few", "many", "other"},
}
# Forms that may leave the number out ("your photo", Arabic duals)
WORDED = {"one", "two"}


def load(folder: Path) -> dict:
    out = {}
    for e in ET.parse(folder / "strings.xml").getroot():
        if e.tag == "string":
            out[e.get("name")] = e.text or ""
        elif e.tag == "plurals":
            out[e.get("name")] = {i.get("quantity"): i.text or "" for i in e}
    return out


def placeholders(text: str) -> list:
    return sorted(re.findall(r"%\d\$[sd]", text))


def check(lang: str, english: dict, translated: dict) -> list:
    errors = [f"missing {k}" for k in english if k not in translated]
    errors += [f"not in English: {k}" for k in translated if k not in english]
    for key, en in english.items():
        tr = translated.get(key)
        if tr is None:
            continue
        if isinstance(en, dict):
            if not isinstance(tr, dict):
                errors.append(f"{key}: should be plurals")
                continue
            if lang in PLURALS and set(tr) != PLURALS[lang]:
                errors.append(f"{key}: plural forms {sorted(tr)}, {lang} needs {sorted(PLURALS[lang])}")
            wanted = placeholders(en["other"])
            for quantity, text in tr.items():
                if quantity not in WORDED and placeholders(text) != wanted:
                    errors.append(f"{key}/{quantity}: placeholders {placeholders(text)}, English has {wanted}")
        elif placeholders(tr) != placeholders(en):
            errors.append(f"{key}: placeholders {placeholders(tr)}, English has {placeholders(en)}")
    return errors


def main() -> int:
    english = load(RESOURCES / "values")
    failed = False
    for folder in sorted(RESOURCES.glob("values-*")):
        lang = folder.name.removeprefix("values-")
        errors = check(lang, english, load(folder))
        print(f"{lang}: {'ok' if not errors else f'{len(errors)} problem(s)'}")
        for error in errors:
            print(f"  {error}")
        failed |= bool(errors)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
