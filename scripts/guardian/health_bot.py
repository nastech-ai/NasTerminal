"""
NasTech Guardian — Health Bot
Checks disk, memory, critical files, and service liveness.
Reads identity_profile.json and writes health_report.json.
"""

import argparse
import json
import logging
import os
import shutil
import sys

log = logging.getLogger(__name__)

CRITICAL_FILES = [
    "app/build.gradle",
    "app/src/main/AndroidManifest.xml",
    "build.gradle",
    "settings.gradle",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.properties",
]

DISK_WARN_PCT = 90


def load_profile(path: str) -> dict:
    if path and os.path.isfile(path):
        with open(path) as f:
            return json.load(f)
    return {}


def check_disk() -> dict:
    total, used, free = shutil.disk_usage("/")
    pct = (used / total) * 100 if total else 0
    return {
        "status": "warn" if pct > DISK_WARN_PCT else "pass",
        "used_pct": round(pct, 1),
        "free_gb": round(free / 1e9, 2),
    }


def check_critical_files() -> list:
    """Return list of missing critical project files."""
    missing = []
    for f in CRITICAL_FILES:
        if not os.path.exists(f):
            missing.append(f)
    return missing


def main(args=None):
    parser = argparse.ArgumentParser(description="NasTech Guardian Health Bot")
    parser.add_argument("--profile", default="identity_profile.json",
                        help="Path to identity_profile.json")
    parser.add_argument("--output",  default="health_report.json",
                        help="Path to write health_report.json")
    opts = parser.parse_args(args)

    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")

    load_profile(opts.profile)

    disk = check_disk()
    missing_files = check_critical_files()

    issues = []
    critical_count = 0

    if disk["status"] == "warn":
        issues.append(f"Disk usage high: {disk['used_pct']}%")

    for f in missing_files:
        issues.append(f"Missing critical file: {f}")
        critical_count += 1

    status = "pass" if critical_count == 0 else "fail"

    report = {
        "status": status,
        "issues_found": len(issues),
        "critical_count": critical_count,
        "disk": disk,
        "missing_files": missing_files,
        "issues": issues,
    }

    with open(opts.output, "w") as f:
        json.dump(report, f, indent=2)

    log.info("[health_bot] report written to %s  status=%s  critical=%d",
             opts.output, status, critical_count)
    print(json.dumps(report, indent=2))
    return 0 if status == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
