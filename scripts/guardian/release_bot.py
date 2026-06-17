"""
NasTech Guardian — Release Bot
Creates GitHub releases, uploads APK artifacts, manages changelogs.
"""

import logging
import os
import json

import requests

log = logging.getLogger(__name__)

GITHUB_API = "https://api.github.com"
REPO       = os.environ.get("GITHUB_REPOSITORY", "nastech-ai/NasTerminal")
TOKEN      = os.environ.get("GITHUB_TOKEN", "")


class ReleaseBot:
    """Creates and manages GitHub Releases for NasTech AI Terminal."""

    def __init__(self, config: dict = None):
        self.config = config or {}
        self.token  = self.config.get("token", TOKEN)
        self.repo   = self.config.get("repo",  REPO)
        self.headers = {
            "Authorization": f"token {self.token}",
            "Accept": "application/vnd.github+json",
        }

    def create_release(self, tag: str, name: str, body: str,
                       draft: bool = False, prerelease: bool = False) -> dict:
        log.info("[release_bot] creating release %s", tag)
        url  = f"{GITHUB_API}/repos/{self.repo}/releases"
        data = {"tag_name": tag, "name": name, "body": body,
                "draft": draft, "prerelease": prerelease}
        try:
            resp = requests.post(url, headers=self.headers, json=data, timeout=30)
            return resp.json()
        except Exception as e:
            return {"error": str(e)}

    def upload_apk(self, release_id: int, apk_path: str) -> dict:
        log.info("[release_bot] uploading APK: %s", apk_path)
        if not os.path.isfile(apk_path):
            return {"error": f"APK not found: {apk_path}"}
        upload_url = (
            f"{GITHUB_API}/repos/{self.repo}/releases/{release_id}/assets"
            f"?name={os.path.basename(apk_path)}"
        )
        try:
            with open(apk_path, "rb") as f:
                resp = requests.post(
                    upload_url, headers={**self.headers, "Content-Type": "application/vnd.android.package-archive"},
                    data=f, timeout=120
                )
            return resp.json()
        except Exception as e:
            return {"error": str(e)}

    def list_releases(self) -> list:
        url = f"{GITHUB_API}/repos/{self.repo}/releases?per_page=10"
        try:
            resp = requests.get(url, headers=self.headers, timeout=15)
            return resp.json()
        except Exception as e:
            return [{"error": str(e)}]


def main():
    bot = ReleaseBot()
    releases = bot.list_releases()
    for r in releases[:3]:
        print(f"  {r.get('tag_name', '?')} — {r.get('name', '?')}")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
