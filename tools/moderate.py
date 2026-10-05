#!/usr/bin/env python3
"""Reviews reported photos: lists reports, removes photos, bans uploaders.

Users report photos in the app (photoReports in Firestore, which only this kind of admin access
can read). App Store guideline 1.2 expects objectionable content to be acted on within 24 hours;
.github/workflows/moderation.yml opens a GitHub issue for each new report, so you get an email.

    pip install google-cloud-firestore
    export GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account.json

    tools/moderate.py reports                     open reports, newest first
    tools/moderate.py remove REPORT_ID [--ban]    delete the photo (Firestore and R2), close its reports
    tools/moderate.py dismiss REPORT_ID           close a report, keeping the photo
    tools/moderate.py ban UID / unban UID         stop / allow an account contributing
    tools/moderate.py notify --repo OWNER/REPO    (CI) open an issue per new report

Removing the file from R2 needs Cloudflare's wrangler CLI, logged in (`npx wrangler login`).
The service account needs the "Cloud Datastore User" role; keep its key out of the repository.
"""

import argparse
import datetime
import json
import shutil
import subprocess
import sys
from urllib.parse import urlparse

from google.cloud import firestore
from google.cloud.firestore_v1.field_path import FieldPath

REPORTS = "photoReports"
HERBS = "herbs"
BANNED = "bannedUsers"
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
    print("  banned %s: they can no longer share photos or suggest names" % uid)


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


def cmd_ban(db, args):
    ban(db, args.uid, args.reason or "banned by the admin")


def cmd_unban(db, args):
    db.collection(BANNED).document(args.uid).delete()
    print("Unbanned %s." % args.uid)


def cmd_notify(db, args):
    """Opens a GitHub issue for every report not announced yet, then marks it announced."""
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
    print("Opened %d issue(s)." % opened)


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
    ban_parser = sub.add_parser("ban")
    ban_parser.add_argument("uid")
    ban_parser.add_argument("--reason")
    ban_parser.set_defaults(run=cmd_ban)
    unban = sub.add_parser("unban")
    unban.add_argument("uid")
    unban.set_defaults(run=cmd_unban)
    notify = sub.add_parser("notify")
    notify.add_argument("--repo", required=True)
    notify.set_defaults(run=cmd_notify)
    args = parser.parse_args()
    args.run(firestore.Client(), args)


if __name__ == "__main__":
    main()
