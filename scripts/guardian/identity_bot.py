"""
NasTech Guardian — Identity Bot
Identifies the repository's language stack, build system, and CI configuration.
Writes an identity_profile.json for downstream guardian stages.
"""

import argparse
import json
import logging
import os
import sys

log = logging.getLogger(__name__)


def detect_identity(owner: str, repo: str, sha: str) -> dict:
    """Auto-detect language stack and build system from repo layout."""
    profile = {
        "agent_id": "nastech-identity-bot",
        "owner": owner,
        "repo": repo,
        "sha": sha[:7] if sha else "",
        "language_stack": "android_java",
        "build_system": "gradle",
        "package_manager": "maven",
        "ci_system": "github-actions",
        "priority": "normal",
        "status": "active",
    }

    # Detect from filesystem if running locally
    if os.path.isfile("build.gradle") or os.path.isfile("build.gradle.kts"):
        profile["build_system"] = "gradle"
        profile["package_manager"] = "maven"
        if os.path.isfile("gradlew"):
            profile["gradle_wrapper"] = True

    if os.path.isfile("settings.gradle") or os.path.isfile("settings.gradle.kts"):
        profile["multi_module"] = True

    if os.path.isfile("requirements.txt") or os.path.isfile("setup.py"):
        if profile["build_system"] == "gradle":
            profile["language_stack"] = "android_java_python"
        else:
            profile["language_stack"] = "python"
            profile["build_system"] = "pip"
            profile["package_manager"] = "pip"

    if os.path.isfile("app/src/main/AndroidManifest.xml"):
        profile["android"] = True
        profile["language_stack"] = "android_java"

    return profile


def main(args=None):
    parser = argparse.ArgumentParser(description="NasTech Guardian Identity Bot")
    parser.add_argument("--owner",  default="nastech-ai",   help="GitHub owner/org")
    parser.add_argument("--repo",   default="NasTerminal",  help="Repository name")
    parser.add_argument("--sha",    default="",             help="Commit SHA")
    parser.add_argument("--output", default="identity_profile.json",
                        help="Path to write the identity profile JSON")
    opts = parser.parse_args(args)

    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")

    profile = detect_identity(opts.owner, opts.repo, opts.sha)

    with open(opts.output, "w") as f:
        json.dump(profile, f, indent=2)

    log.info("[identity_bot] profile written to %s", opts.output)
    log.info("[identity_bot] language_stack=%s  build_system=%s",
             profile["language_stack"], profile["build_system"])

    print(json.dumps(profile, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
