#!/usr/bin/env python3
"""Reviews what users send: reported photos and shared models, and Vietnamese name suggestions.

Users report photos in the app (photoReports in Firestore, which only this kind of admin access
can read). App Store guideline 1.2 expects objectionable content to be acted on within 24 hours;
.github/workflows/moderation.yml opens a GitHub issue for each new report, so you get an email.
The same goes for shared models (modelReports; the models are sharedModels, with their files in
R2). It does the same for each name suggestion (nameSuggestions), which has no deadline.

    pip install google-cloud-firestore
    export GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account.json

    tools/moderate.py reports                     open reports, newest first
    tools/moderate.py remove REPORT_ID [--ban]    delete the photo (Firestore and R2), close its reports
    tools/moderate.py dismiss REPORT_ID           close a report, keeping the photo
    tools/moderate.py model-reports               open reports of shared models, newest first
    tools/moderate.py remove-model REPORT_ID [--ban]   take the model down (Firestore and R2), close its reports
    tools/moderate.py dismiss-model REPORT_ID     close a model report, keeping the model
    tools/moderate.py hf-requests                 shared models asked for Hugging Face but not on it yet
    tools/moderate.py publish-hf MODEL_ID         publish one there by hand (the Worker normally does it)
    tools/moderate.py decline-hf MODEL_ID         don't publish it (the sharer sees that in the app)
    tools/moderate.py ban UID / unban UID         stop / allow an account contributing
    tools/moderate.py suggestions                 open name suggestions, with the current names
    tools/moderate.py dismiss-suggestion ID       close a suggestion (after adding the name, or not)
    tools/moderate.py notify --repo OWNER/REPO    (CI) open an issue per new report and suggestion

To accept a suggestion, add the name to the species' vietnameseName in
androidApp/assets/herb_catalog.csv (names separated by "; ", the preferred one first), publish
the catalog (docs/content-publishing.md), then dismiss the suggestion.

Removing the file from R2 needs Cloudflare's wrangler CLI, logged in (`npx wrangler login`).
Models are published on Hugging Face by the upload Worker as they're shared (when the sharer
leaves the box ticked). publish-hf, and removing a published model, need `pip install
huggingface_hub` and HF_TOKEN, a write token for the organisation (HF_ORG, by default
med-herb-lens); keep it out of the repository.
The service account needs the "Cloud Datastore User" role; keep its key out of the repository.
"""

import argparse
import csv
import datetime
import json
import os
import shutil
import subprocess
import sys
from urllib.parse import urlparse

from google.cloud import firestore
from google.cloud.firestore_v1.field_path import FieldPath

REPORTS = "photoReports"
MODEL_REPORTS = "modelReports"
SHARED_MODELS = "sharedModels"
SUGGESTIONS = "nameSuggestions"
CATALOG = "androidApp/assets/herb_catalog.csv"
HERBS = "herbs"
BANNED = "bannedUsers"
HF_ORG = os.environ.get("HF_ORG", "med-herb-lens")
CITATIONS = [
    "Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2025). Med Herb Lens: A prototype AI "
    "app for medicinal plant identification. *Procedia Computer Science*, 270, 2603–2612. "
    "https://doi.org/10.1016/j.procs.2025.09.382",
    "Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2026). Resource-efficient continual "
    "learning for medicinal plant identification: A periodic retraining approach for edge-deployed agricultural "
    "IoT applications. *IoT*, 7(3), 57. https://doi.org/10.3390/iot7030057",
]
# Where the photo-upload Worker stores photos (workers/photo-upload/wrangler.toml)
R2_BUCKET = "herb-lens-content"
R2_PUBLIC_HOST = "pub-394936ea0f444a64a542ff743b102f1f.r2.dev"


def fail(message):
    sys.exit("error: " + message)


def report_or_fail(db, report_id):
    snapshot = db.collection(REPORTS).document(report_id).get()
    if not snapshot.exists:
        fail("no report %s" % report_id)
    return snapshot


def describe(snapshot):
    r = snapshot.to_dict()
    created = r.get("createdAt")
    when = created.strftime("%Y-%m-%d %H:%M UTC") if created else "?"
    return "%s  %s  %-17s species %s\n    photo     %s\n    uploader  %s   reporter %s" % (
        snapshot.id, when, r.get("reason"), r.get("speciesKey"), r.get("url"),
        r.get("uploaderId") or "-", r.get("reporterUid") or "signed out",
    )


def cmd_reports(db, args):
    snapshots = sorted(db.collection(REPORTS).stream(),
                       key=lambda s: s.to_dict().get("createdAt") or datetime.datetime.min.replace(tzinfo=datetime.timezone.utc),
                       reverse=True)
    if not snapshots:
        print("No open reports.")
    for snapshot in snapshots:
        print(describe(snapshot))


def wrangler():
    if shutil.which("wrangler"):
        return ["wrangler"]
    if shutil.which("npx"):
        return ["npx", "--yes", "wrangler"]
    fail("wrangler not found: npm i -g wrangler && wrangler login")


def delete_from_r2(url):
    parsed = urlparse(url)
    if parsed.hostname != R2_PUBLIC_HOST:
        print("  not on our R2 (%s); left in place" % parsed.hostname)
        return
    key = parsed.path.lstrip("/")
    subprocess.run(wrangler() + ["r2", "object", "delete", "%s/%s" % (R2_BUCKET, key), "--remote"], check=True)
    print("  deleted R2 object %s" % key)


def ban(db, uid, reason):
    db.collection(BANNED).document(uid).set({"reason": reason, "bannedAt": firestore.SERVER_TIMESTAMP})
    print("  banned %s: they can no longer share photos or models, report, or suggest names" % uid)


def cmd_remove(db, args):
    report = report_or_fail(db, args.report_id).to_dict()
    url, species = report["url"], str(report["speciesKey"])
    # The photo's key in the images map is its URL, which contains dots: use a field path
    field = FieldPath("images", url).to_api_repr()
    herb = db.collection(HERBS).document(species)
    if url in (herb.get().to_dict() or {}).get("images", {}):
        herb.update({field: firestore.DELETE_FIELD})
        print("  removed the photo from herbs/%s" % species)
    else:
        print("  the photo isn't listed on herbs/%s any more" % species)
    if not args.keep_file:
        delete_from_r2(url)
    closed = 0
    for snapshot in db.collection(REPORTS).where(filter=firestore.FieldFilter("url", "==", url)).stream():
        snapshot.reference.delete()
        closed += 1
    print("  closed %d report(s)" % closed)
    if args.ban:
        if not report.get("uploaderId"):
            fail("the report has no uploader to ban")
        ban(db, report["uploaderId"], "photo %s: %s" % (url, report.get("reason")))


def cmd_dismiss(db, args):
    report_or_fail(db, args.report_id).reference.delete()
    print("Dismissed %s; the photo stays." % args.report_id)


def model_report_or_fail(db, report_id):
    snapshot = db.collection(MODEL_REPORTS).document(report_id).get()
    if not snapshot.exists:
        fail("no model report %s" % report_id)
    return snapshot


def describe_model_report(db, snapshot):
    r = snapshot.to_dict()
    created = r.get("createdAt")
    when = created.strftime("%Y-%m-%d %H:%M UTC") if created else "?"
    model = db.collection(SHARED_MODELS).document(r.get("modelId", "-")).get()
    m = model.to_dict() if model.exists else {}
    species = m.get("species") or []
    return "%s  %s  %-20s model %s\n    name      %s\n    species   %s\n    file      %s\n    uploader  %s   reporter %s" % (
        snapshot.id, when, r.get("reason"), r.get("modelId"),
        m.get("name", "(no longer shared)"), ", ".join(species[:10]) + (" …" if len(species) > 10 else ""),
        m.get("url", "-"), r.get("uploaderId") or "-", r.get("reporterUid") or "-",
    )


def cmd_model_reports(db, args):
    snapshots = sorted(db.collection(MODEL_REPORTS).stream(),
                       key=lambda s: s.to_dict().get("createdAt") or datetime.datetime.min.replace(tzinfo=datetime.timezone.utc),
                       reverse=True)
    if not snapshots:
        print("No open model reports.")
    for snapshot in snapshots:
        print(describe_model_report(db, snapshot))


def cmd_remove_model(db, args):
    report = model_report_or_fail(db, args.report_id).to_dict()
    model_id = report["modelId"]
    listed = db.collection(SHARED_MODELS).document(model_id)
    model = listed.get()
    if model.exists:
        url = model.to_dict().get("url")
        hub_url = model.to_dict().get("huggingFaceUrl")
        if hub_url:
            unpublish_hf(hub_url)
        listed.delete()
        print("  removed sharedModels/%s" % model_id)
        if url and not args.keep_file:
            delete_from_r2(url)
    else:
        print("  the model isn't shared any more")
    closed = 0
    for snapshot in db.collection(MODEL_REPORTS).where(filter=firestore.FieldFilter("modelId", "==", model_id)).stream():
        snapshot.reference.delete()
        closed += 1
    print("  closed %d report(s)" % closed)
    if args.ban:
        ban(db, report["uploaderId"], "model %s: %s" % (model_id, report.get("reason")))


def cmd_dismiss_model(db, args):
    model_report_or_fail(db, args.report_id).reference.delete()
    print("Dismissed %s; the model stays shared." % args.report_id)


def notify_model_reports(db, repo):
    """Opens a GitHub issue for every model report not announced yet, then marks it announced."""
    opened = 0
    for snapshot in db.collection(MODEL_REPORTS).stream():
        report = snapshot.to_dict()
        if report.get("notifiedAt"):
            continue
        body = "\n".join([
            "A shared model was reported in the app. Please review it within 24 hours.",
            "",
            "```",
            describe_model_report(db, snapshot),
            "```",
            "",
            "Then, with the service account set up (see tools/moderate.py):",
            "",
            "```",
            "tools/moderate.py remove-model %s --ban   # objectionable: take it down and ban the uploader" % snapshot.id,
            "tools/moderate.py remove-model %s         # take the model down only" % snapshot.id,
            "tools/moderate.py dismiss-model %s        # nothing wrong: keep it" % snapshot.id,
            "```",
            "",
            "Close this issue once done.",
        ])
        title = "Reported model: %s (%s)" % (report.get("reason"), report.get("modelId"))
        subprocess.run(["gh", "issue", "create", "--repo", repo, "--title", title, "--body", body,
                        "--label", "moderation"], check=True)
        snapshot.reference.update({"notifiedAt": firestore.SERVER_TIMESTAMP})
        opened += 1
    return opened


def shared_model_or_fail(db, model_id):
    snapshot = db.collection(SHARED_MODELS).document(model_id).get()
    if not snapshot.exists:
        fail("no shared model %s" % model_id)
    return snapshot


def describe_shared_model(snapshot):
    m = snapshot.to_dict()
    species = m.get("species") or []
    return "%s  %s (%d species, %.1f MB, %s)\n    species   %s\n    file      %s\n    sharer    %s" % (
        snapshot.id, m.get("name"), len(species), (m.get("size") or 0) / 1048576, m.get("backbone"),
        ", ".join(species[:12]) + (" …" if len(species) > 12 else ""), m.get("url"), m.get("uploaderId"),
    )


def cmd_hf_requests(db, args):
    requested = list(db.collection(SHARED_MODELS).where(filter=firestore.FieldFilter("huggingFace", "==", "requested")).stream())
    if not requested:
        print("No models waiting for Hugging Face.")
    for snapshot in requested:
        print(describe_shared_model(snapshot))


def model_card(model_id, m):
    species = sorted(m.get("species") or [], key=str.lower)
    lines = [
        "---",
        "license: cc-by-4.0",
        "library_name: tflite",
        "pipeline_tag: image-classification",
        "tags:",
        "- med-herb-lens",
        "- plants",
        "- medicinal-plants",
        "- on-device",
        "- tflite",
        "---",
        "",
        "# %s" % m.get("name"),
        "",
        "An image classifier trained by a [Med Herb Lens](https://med-herb-lens.pages.dev/about.html) user on their "
        "own device, and shared under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).",
        "",
        "## What it identifies (%d species)" % len(species),
        "",
    ] + ["- %s" % name for name in species] + [
        "",
        "## How to use it",
        "",
        "- **In Med Herb Lens:** download `model.tflite`, then Train → Import a model.%s" % (
            " Made in Med Herb Lens, it can go on learning there: add species or photos and train again." if m.get("trainable") else ""),
        "- **Anywhere TensorFlow Lite runs:** a 224 × 224 RGB image with values from 0 to 1 in; one probability per "
        "species out. The species list is inside the file (`labels.txt` in its metadata).",
        "",
        "## How it was made",
        "",
        "A final layer trained in Med Herb Lens, on the device, on top of the `%s` MediaPipe image embedder "
        "(Apache 2.0)." % m.get("backbone"),
        "",
        "## Caution",
        "",
        "Like any model, it can be wrong, and its species names were typed by its sharer. Never eat a plant or use "
        "it as medicine because of what a model says.",
        "",
        "## Credit and citation",
        "",
        "Shared by a Med Herb Lens user (shared model `%s`). If you use it, please cite:" % model_id,
        "",
    ] + ["%s\n" % c for c in CITATIONS]
    return "\n".join(lines)


def slug(text):
    """A Hugging Face repository name: ASCII letters, digits and dashes ("Rau Hà Nội" -> "rau-ha-noi")."""
    import unicodedata
    folded = unicodedata.normalize("NFKD", text.replace("đ", "d").replace("Đ", "D")).encode("ascii", "ignore").decode()
    cleaned = "".join(c.lower() if c.isalnum() else "-" for c in folded)
    return "-".join(part for part in cleaned.split("-") if part)[:60] or "model"


def cmd_publish_hf(db, args):
    snapshot = shared_model_or_fail(db, args.model_id)
    m = snapshot.to_dict()
    token = os.environ.get("HF_TOKEN") or fail("set HF_TOKEN to a write token for the %s organisation" % HF_ORG)
    from huggingface_hub import HfApi
    import urllib.request
    api = HfApi(token=token)
    repo_id = "%s/%s-%s" % (HF_ORG, slug(m.get("name", "model")), args.model_id[:8])
    with urllib.request.urlopen(m["url"]) as response:
        model_bytes = response.read()
    api.create_repo(repo_id, repo_type="model", exist_ok=True)
    api.upload_file(path_or_fileobj=model_bytes, path_in_repo="model.tflite", repo_id=repo_id,
                    commit_message="Model shared in Med Herb Lens (%s)" % args.model_id)
    api.upload_file(path_or_fileobj=model_card(args.model_id, m).encode("utf-8"), path_in_repo="README.md",
                    repo_id=repo_id, commit_message="Model card")
    url = "https://huggingface.co/%s" % repo_id
    snapshot.reference.update({"huggingFace": "published", "huggingFaceUrl": url})
    print("Published %s; the app now links to it." % url)


def unpublish_hf(hub_url):
    token = os.environ.get("HF_TOKEN")
    if not token:
        print("  HF_TOKEN not set: delete %s by hand" % hub_url)
        return
    from huggingface_hub import HfApi
    repo_id = "/".join(urlparse(hub_url).path.strip("/").split("/")[:2])
    HfApi(token=token).delete_repo(repo_id, repo_type="model", missing_ok=True)
    print("  deleted Hugging Face repository %s" % repo_id)


def cmd_decline_hf(db, args):
    shared_model_or_fail(db, args.model_id).reference.update({"huggingFace": "declined"})
    print("Declined; %s stays shared in the app only." % args.model_id)


def notify_hf_requests(db, repo):
    """Opens a GitHub issue for every new request to publish a shared model on Hugging Face."""
    opened = 0
    for snapshot in db.collection(SHARED_MODELS).where(filter=firestore.FieldFilter("huggingFace", "==", "requested")).stream():
        if snapshot.to_dict().get("hfNotifiedAt"):
            continue
        body = "\n".join([
            "A shared model was meant to be published on Hugging Face (%s), but isn't yet: the Worker" % HF_ORG,
            "couldn't publish it (Hugging Face unreachable, or HF_TOKEN not set on the Worker).",
            "",
            "```",
            describe_shared_model(snapshot),
            "```",
            "",
            "If its name and species are fine to publish:",
            "",
            "```",
            "tools/moderate.py publish-hf %s   # publish it" % snapshot.id,
            "tools/moderate.py decline-hf %s   # keep it in the app only" % snapshot.id,
            "```",
            "",
            "Close this issue once done.",
        ])
        title = "Hugging Face request: %s" % snapshot.to_dict().get("name")
        subprocess.run(["gh", "issue", "create", "--repo", repo, "--title", title, "--body", body,
                        "--label", "hugging-face"], check=True)
        snapshot.reference.update({"hfNotifiedAt": firestore.SERVER_TIMESTAMP})
        opened += 1
    return opened


def cmd_ban(db, args):
    ban(db, args.uid, args.reason or "banned by the admin")


def cmd_unban(db, args):
    db.collection(BANNED).document(args.uid).delete()
    print("Unbanned %s." % args.uid)


def catalog_names(species_key):
    """(scientific name, current Vietnamese names) from the catalog CSV, if it has the species."""
    path = os.path.join(os.path.dirname(__file__), "..", CATALOG)
    try:
        with open(path, encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f):
                if row.get("speciesKey") == str(species_key):
                    return row.get("canonicalName") or row.get("scientificName"), row.get("vietnameseName") or ""
    except OSError:
        pass
    return None, None


def describe_suggestion(snapshot):
    s = snapshot.to_dict()
    created = s.get("createdAt")
    when = created.strftime("%Y-%m-%d %H:%M UTC") if created else "?"
    scientific, current = catalog_names(s.get("speciesKey"))
    return "%s  %s  species %s (%s)\n    suggested %s\n    current   %s\n    by        %s" % (
        snapshot.id, when, s.get("speciesKey"), scientific or "not in the catalog", s.get("viName"),
        current or "-", s.get("uid"),
    )


def cmd_suggestions(db, args):
    snapshots = sorted(db.collection(SUGGESTIONS).stream(),
                       key=lambda s: s.to_dict().get("createdAt") or datetime.datetime.min.replace(tzinfo=datetime.timezone.utc),
                       reverse=True)
    if not snapshots:
        print("No open suggestions.")
    for snapshot in snapshots:
        print(describe_suggestion(snapshot))


def cmd_dismiss_suggestion(db, args):
    snapshot = db.collection(SUGGESTIONS).document(args.suggestion_id).get()
    if not snapshot.exists:
        fail("no suggestion %s" % args.suggestion_id)
    snapshot.reference.delete()
    print("Closed suggestion %s." % args.suggestion_id)


def notify_suggestions(db, repo):
    """Opens a GitHub issue for every name suggestion not announced yet, then marks it announced."""
    opened = 0
    for snapshot in db.collection(SUGGESTIONS).stream():
        suggestion = snapshot.to_dict()
        if suggestion.get("notifiedAt"):
            continue
        key = suggestion.get("speciesKey")
        name = suggestion.get("viName")
        scientific, current = catalog_names(key)
        body = "\n".join([
            "A user suggested a Vietnamese name.",
            "",
            "- Suggested: **%s**" % name,
            "- Species: %s, %s (https://www.gbif.org/species/%s)" % (scientific or "not in the catalog", key, key),
            "- Current Vietnamese names: %s" % (current or "none"),
            "- By: `%s`" % suggestion.get("uid"),
            "",
            "If it's right, add it to the species' `vietnameseName` in `%s` (names separated by `; `, "
            "the preferred one first) and publish the catalog (docs/content-publishing.md). Either way, then:" % CATALOG,
            "",
            "```",
            "tools/moderate.py dismiss-suggestion %s" % snapshot.id,
            "```",
            "",
            "Close this issue once done.",
        ])
        title = "Name suggestion: %s for %s" % (name, scientific or "species %s" % key)
        subprocess.run(["gh", "issue", "create", "--repo", repo, "--title", title, "--body", body,
                        "--label", "name-suggestion"], check=True)
        snapshot.reference.update({"notifiedAt": firestore.SERVER_TIMESTAMP})
        opened += 1
    return opened


def cmd_notify(db, args):
    """Opens a GitHub issue for every report and suggestion not announced yet, then marks it announced."""
    opened = 0
    for snapshot in db.collection(REPORTS).stream():
        report = snapshot.to_dict()
        if report.get("notifiedAt"):
            continue
        body = "\n".join([
            "A photo was reported in the app. Please review it within 24 hours.",
            "",
            "- Reason: **%s**" % report.get("reason"),
            "- Species: %s (https://www.gbif.org/species/%s)" % (report.get("speciesKey"), report.get("speciesKey")),
            "- Photo: %s" % report.get("url"),
            "- Uploader: `%s`" % (report.get("uploaderId") or "-"),
            "",
            "Then, with the service account set up (see tools/moderate.py):",
            "",
            "```",
            "tools/moderate.py remove %s --ban   # objectionable: remove it and ban the uploader" % snapshot.id,
            "tools/moderate.py remove %s         # remove the photo only" % snapshot.id,
            "tools/moderate.py dismiss %s        # nothing wrong: keep it" % snapshot.id,
            "```",
            "",
            "Close this issue once done.",
        ])
        title = "Reported photo: %s (species %s)" % (report.get("reason"), report.get("speciesKey"))
        subprocess.run(["gh", "issue", "create", "--repo", args.repo, "--title", title, "--body", body,
                        "--label", "moderation"], check=True)
        snapshot.reference.update({"notifiedAt": firestore.SERVER_TIMESTAMP})
        opened += 1
    print("Opened %d issue(s) for reports." % opened)
    print("Opened %d issue(s) for model reports." % notify_model_reports(db, args.repo))
    print("Opened %d issue(s) for Hugging Face requests." % notify_hf_requests(db, args.repo))
    print("Opened %d issue(s) for name suggestions." % notify_suggestions(db, args.repo))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("reports").set_defaults(run=cmd_reports)
    remove = sub.add_parser("remove")
    remove.add_argument("report_id")
    remove.add_argument("--ban", action="store_true", help="also ban the uploader")
    remove.add_argument("--keep-file", action="store_true", help="leave the file on R2 (e.g. as evidence)")
    remove.set_defaults(run=cmd_remove)
    dismiss = sub.add_parser("dismiss")
    dismiss.add_argument("report_id")
    dismiss.set_defaults(run=cmd_dismiss)
    sub.add_parser("model-reports").set_defaults(run=cmd_model_reports)
    remove_model = sub.add_parser("remove-model")
    remove_model.add_argument("report_id")
    remove_model.add_argument("--ban", action="store_true", help="also ban the uploader")
    remove_model.add_argument("--keep-file", action="store_true", help="leave the file on R2 (e.g. as evidence)")
    remove_model.set_defaults(run=cmd_remove_model)
    dismiss_model = sub.add_parser("dismiss-model")
    dismiss_model.add_argument("report_id")
    dismiss_model.set_defaults(run=cmd_dismiss_model)
    sub.add_parser("hf-requests").set_defaults(run=cmd_hf_requests)
    publish_hf = sub.add_parser("publish-hf")
    publish_hf.add_argument("model_id")
    publish_hf.set_defaults(run=cmd_publish_hf)
    decline_hf = sub.add_parser("decline-hf")
    decline_hf.add_argument("model_id")
    decline_hf.set_defaults(run=cmd_decline_hf)
    ban_parser = sub.add_parser("ban")
    ban_parser.add_argument("uid")
    ban_parser.add_argument("--reason")
    ban_parser.set_defaults(run=cmd_ban)
    unban = sub.add_parser("unban")
    unban.add_argument("uid")
    unban.set_defaults(run=cmd_unban)
    sub.add_parser("suggestions").set_defaults(run=cmd_suggestions)
    dismiss_suggestion = sub.add_parser("dismiss-suggestion")
    dismiss_suggestion.add_argument("suggestion_id")
    dismiss_suggestion.set_defaults(run=cmd_dismiss_suggestion)
    notify = sub.add_parser("notify")
    notify.add_argument("--repo", required=True)
    notify.set_defaults(run=cmd_notify)
    args = parser.parse_args()
    args.run(firestore.Client(), args)


if __name__ == "__main__":
    main()
