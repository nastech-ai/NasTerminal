"""
NasTech Guardian — Release Bot
Creates GitHub releases, uploads APK artifacts, manages changelogs.
Accepts --repo, --sha, --output CLI args and writes release_report.json.
"""

import argparse
import json
import logging
import os
import sys

log = logging.getLogger(__name__)

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False

GITHUB_API = "https://api.github.com"


def _headers(token: str) -> dict:
    return {
        "Authorization": f"token {token}",
        "Accept":        "application/vnd.github+json",
        "User-Agent":    "NasTech-Guardian-ReleaseBot/1.0",
    }


def get_latest_tag(repo: str, token: str) -> str:
    if not HAS_REQUESTS or not token:
        return ""
    try:
        resp = requests.get(
            f"{GITHUB_API}/repos/{repo}/releases/latest",
            headers=_headers(token), timeout=15
        )
        return resp.json().get("tag_name", "")
    except Exception:
        return ""


def bump_version(current: str) -> str:
    """Increment patch version of a semver tag."""
    if not current:
        return "v1.0.0"
    tag = current.lstrip("v")
    parts = tag.split(".")
    try:
        parts[-1] = str(int(parts[-1]) + 1)
        return "v" + ".".join(parts)
    except (ValueError, IndexError):
        return "v1.0.0"


def create_release(repo: str, tag: str, sha: str, token: str) -> dict:
    if not HAS_REQUESTS or not token:
        return {"status": "skipped", "reason": "requests not available or no token"}
    body = (
        f"## NasTech AI Terminal — {tag}\n\n"
        f"**Commit:** `{sha[:7]}`\n\n"
        "### Changes\n"
        "- Auto-release by NasTech Guardian\n\n"
        "_Built and released automatically by the NasTech Guardian CI pipeline._"
    )
    try:
        resp = requests.post(
            f"{GITHUB_API}/repos/{repo}/releases",
            headers=_headers(token),
            json={
                "tag_name":         tag,
                "name":             f"NasTech AI Terminal {tag}",
                "body":             body,
                "draft":            False,
                "prerelease":       False,
                "target_commitish": sha,
            },
            timeout=30,
        )
        if resp.status_code in (200, 201):
            data = resp.json()
            return {
                "status":       "pass",
                "release_tag":  tag,
                "release_url":  data.get("html_url", ""),
                "release_id":   data.get("id", 0),
                "changelog_lines": len(body.splitlines()),
            }
        return {
            "status":         "fail",
            "release_tag":    tag,
            "release_url":    "",
            "changelog_lines": 0,
            "error":          resp.json().get("message", "Unknown error"),
        }
    except Exception as e:
        return {"status": "error", "error": str(e), "release_tag": "", "release_url": ""}


def main(args=None):
    parser = argparse.ArgumentParser(description="NasTech Guardian Release Bot")
    parser.add_argument("--repo",   default="nastech-ai/NasTerminal")
    parser.add_argument("--sha",    default="")
    parser.add_argument("--output", default="release_report.json")
    opts = parser.parse_args(args)

    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")

    token = os.environ.get("GITHUB_TOKEN", "")

    current_tag = get_latest_tag(opts.repo, token)
    next_tag    = bump_version(current_tag)

    log.info("[release_bot] current=%s  next=%s", current_tag or "none", next_tag)

    if token:
        report = create_release(opts.repo, next_tag, opts.sha, token)
    else:
        log.warning("[release_bot] GITHUB_TOKEN not set — skipping release")
        report = {
            "status":          "skipped",
            "release_tag":     next_tag,
            "release_url":     "",
            "changelog_lines": 0,
            "note":            "GITHUB_TOKEN not set",
        }

    with open(opts.output, "w") as f:
        json.dump(report, f, indent=2)

    log.info("[release_bot] report written to %s  status=%s", opts.output, report["status"])
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
