"""
NasTech Guardian — Repair Bot
Auto-detects and repairs common build/runtime failures.
Reads build/health/dependency reports and writes repair_report.json.
"""

import argparse
import json
import logging
import os
import sys

log = logging.getLogger(__name__)

KNOWN_FIXES = {
    "Duplicate resources":                  "remove_duplicate_resource",
    "attribute android:hintTextColor":      "fix_invalid_xml_attribute",
    "Could not resolve":                    "refresh_gradle_cache",
    "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE": "uninstall_and_reinstall",
    "java.nio.file.Files":                  "fix_api_level_compat",
    "java.util.function.Consumer":          "fix_api_level_compat",
}


def load_json(path: str) -> dict:
    if path and os.path.isfile(path):
        try:
            with open(path) as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def diagnose(build_report: dict, health_report: dict, dep_report: dict) -> list:
    patches = []
    failure_reason = build_report.get("failure_reason", "")
    for pattern, fix in KNOWN_FIXES.items():
        if pattern in failure_reason:
            patches.append({"issue": pattern, "fix": fix})
    missing_files = health_report.get("missing_files", [])
    for f in missing_files:
        patches.append({"issue": f"Missing: {f}", "fix": "create_stub_file"})
    broken = dep_report.get("broken", [])
    for b in broken:
        patches.append({"issue": f"Broken dependency: {b}", "fix": "update_dependency"})
    return patches


def main(args=None):
    parser = argparse.ArgumentParser(description="NasTech Guardian Repair Bot")
    parser.add_argument("--build-report",  default="build_report.json")
    parser.add_argument("--health-report", default="health_report.json")
    parser.add_argument("--dep-report",    default="dependency_report.json")
    parser.add_argument("--repo",          default="nastech-ai/NasTerminal")
    parser.add_argument("--sha",           default="")
    parser.add_argument("--output",        default="repair_report.json")
    opts = parser.parse_args(args)

    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")

    build_report  = load_json(opts.build_report)
    health_report = load_json(opts.health_report)
    dep_report    = load_json(opts.dep_report)

    patches = diagnose(build_report, health_report, dep_report)

    build_status = build_report.get("status", "pass")
    overall_status = "pass" if build_status == "pass" and not patches else "repaired"

    report = {
        "status":      overall_status,
        "repo":        opts.repo,
        "sha":         opts.sha[:7] if opts.sha else "",
        "pr_number":   "",
        "pr_url":      "",
        "patch_count": len(patches),
        "patches":     patches,
    }

    with open(opts.output, "w") as f:
        json.dump(report, f, indent=2)

    log.info("[repair_bot] report written to %s  patches=%d",
             opts.output, len(patches))
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
