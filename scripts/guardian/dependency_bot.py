"""
NasTech Guardian — Dependency Bot
Scans Python and Gradle dependencies for issues.
Reads identity_profile.json and writes dependency_report.json.
"""

import argparse
import json
import logging
import os
import subprocess
import sys

log = logging.getLogger(__name__)


def load_profile(path: str) -> dict:
    if path and os.path.isfile(path):
        with open(path) as f:
            return json.load(f)
    return {"build_system": "gradle", "language_stack": "android_java"}


def audit_python_deps() -> list:
    """Return list of outdated/missing Python packages."""
    missing = []
    for req_file in ["scripts/telegram_bot/requirements.txt",
                     "scripts/guardian/requirements.txt",
                     "requirements.txt"]:
        if not os.path.isfile(req_file):
            continue
        with open(req_file) as f:
            for line in f:
                pkg = line.strip().split("==")[0].split(">=")[0].split("<=")[0]
                if pkg and not pkg.startswith("#"):
                    try:
                        result = subprocess.run(
                            [sys.executable, "-c", f"import {pkg.replace('-','_')}"],
                            capture_output=True, timeout=5
                        )
                        if result.returncode != 0:
                            missing.append(pkg)
                    except Exception:
                        pass
    return missing


def audit_gradle_deps(project_root: str = ".") -> dict:
    """Run gradle dependencies and capture output."""
    gradlew = os.path.join(project_root, "gradlew")
    if not os.path.isfile(gradlew):
        return {"status": "skipped", "reason": "gradlew not found"}
    try:
        result = subprocess.run(
            [gradlew, ":app:dependencies", "--configuration",
             "releaseRuntimeClasspath", "--no-daemon"],
            cwd=project_root,
            capture_output=True, text=True, timeout=300
        )
        conflicts = []
        for line in result.stdout.splitlines():
            if " -> " in line and "(*)" not in line:
                conflicts.append(line.strip())
        return {
            "status": "pass" if result.returncode == 0 else "fail",
            "conflicts": conflicts[:20],
            "returncode": result.returncode,
        }
    except Exception as e:
        return {"status": "error", "error": str(e)}


def main(args=None):
    parser = argparse.ArgumentParser(description="NasTech Guardian Dependency Bot")
    parser.add_argument("--profile", default="identity_profile.json",
                        help="Path to identity_profile.json")
    parser.add_argument("--output",  default="dependency_report.json",
                        help="Path to write dependency_report.json")
    opts = parser.parse_args(args)

    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")

    profile = load_profile(opts.profile)
    build_system = profile.get("build_system", "gradle")

    report = {
        "status": "pass",
        "build_system": build_system,
        "missing": [],
        "conflicts": [],
        "broken": [],
        "note": "",
    }

    if "python" in build_system or "pip" in build_system:
        missing = audit_python_deps()
        report["missing"] = missing
        if missing:
            report["status"] = "fail"
            report["note"] = f"{len(missing)} missing Python packages"

    if "gradle" in build_system or "android" in profile.get("language_stack", ""):
        gradle_result = audit_gradle_deps()
        report["conflicts"] = gradle_result.get("conflicts", [])
        if gradle_result.get("status") == "fail":
            report["status"] = "fail"
            report["note"] = "Gradle dependency resolution failed"

    with open(opts.output, "w") as f:
        json.dump(report, f, indent=2)

    log.info("[dependency_bot] report written to %s  status=%s",
             opts.output, report["status"])
    print(json.dumps(report, indent=2))
    return 0 if report["status"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
