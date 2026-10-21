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
# Each language's plural forms (CLDR cardinal rules, as Compose Multiplatform applies them). Latin has no
# CLDR rules, so it takes the one/other pattern English uses.
PLURALS = {
    "en": {"one", "other"}, "vi": {"other"}, "zh": {"other"}, "ja": {"other"}, "ko": {"other"}, "th": {"other"},
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "az": {"one", "other"}, "bn": {"one", "other"}, "bg": {"one", "other"}, "nl": {"one", "other"},
    "eo": {"one", "other"}, "de": {"one", "other"}, "el": {"one", "other"}, "hi": {"one", "other"},
    "hu": {"one", "other"}, "la": {"one", "other"}, "ln": {"one", "other"}, "mg": {"one", "other"},
    "mr": {"one", "other"}, "ne": {"one", "other"}, "fa": {"one", "other"}, "si": {"one", "other"},
    "sv": {"one", "other"}, "sw": {"one", "other"}, "tr": {"one", "other"},
    "fr": {"one", "many", "other"}, "es": {"one", "many", "other"}, "it": {"one", "many", "other"},
    "pt": {"one", "many", "other"},
    "bs": {"one", "few", "other"}, "hr": {"one", "few", "other"}, "sr": {"one", "few", "other"},
    "ro": {"one", "few", "other"},
    "cs": {"one", "few", "many", "other"}, "lt": {"one", "few", "many", "other"},
    "pl": {"one", "few", "many", "other"}, "ru": {"one", "few", "many", "other"},
    "uk": {"one", "few", "many", "other"},
}
LANGUAGES = Path(__file__).resolve().parent.parent / "core/domain/src/commonMain/kotlin/com/uri/lee/dl/domain/model/AppLanguages.kt"
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
            # Extra forms are never used, so only missing ones matter
            if lang in PLURALS and not PLURALS[lang] <= set(tr):
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
    # Every interface language has its folder, and every folder is an interface language
    codes = set(re.findall(r'AppLanguage\("([a-z]+)"', LANGUAGES.read_text()))
    folders = {f.name.removeprefix("values-") for f in RESOURCES.glob("values-*")}
    for code in sorted(codes - folders - {"en"}):
        print(f"{code}: no values-{code} folder")
        failed = True
    for code in sorted(folders - codes):
        print(f"values-{code}: not in AppLanguages")
        failed = True
    for code in sorted(codes - set(PLURALS)):
        print(f"{code}: plural forms unknown to this script")
        failed = True
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
