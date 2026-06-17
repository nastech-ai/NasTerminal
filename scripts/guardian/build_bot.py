"""
NasTech Guardian — Build Bot
Triggers and monitors Gradle / Android build pipelines.
Reads identity_profile.json and writes build_report.json.
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
    return {"build_system": "gradle"}


def run_gradle(task: str = "assembleDebug", project_root: str = ".") -> dict:
    gradlew = os.path.join(project_root, "gradlew")
    if not os.path.isfile(gradlew):
        return {
            "status": "fail",
            "error_count": 1,
            "apk_produced": False,
            "failure_reason": "gradlew not found",
            "returncode": -1,
        }

    env = os.environ.copy()
    env.setdefault("TERMUX_PACKAGE_VARIANT", "apt-android-7")

    try:
        result = subprocess.run(
            [gradlew, task, "--no-daemon", "--stacktrace"],
            cwd=project_root,
            capture_output=True, text=True, timeout=600,
            env=env,
        )
        success = result.returncode == 0

        errors = [
            line for line in (result.stderr + result.stdout).splitlines()
            if any(kw in line for kw in
                   ["error:", "Error:", "BUILD FAILED", "Exception", "FAILED"])
        ]

        apk_dir = os.path.join(project_root, "app", "build", "outputs", "apk")
        apk_produced = False
        if os.path.isdir(apk_dir):
            for root, _, files in os.walk(apk_dir):
                if any(f.endswith(".apk") for f in files):
                    apk_produced = True
                    break

        return {
            "status": "pass" if success else "fail",
            "error_count": len(errors),
            "apk_produced": apk_produced,
            "failure_reason": errors[0] if errors else "",
            "returncode": result.returncode,
        }
    except subprocess.TimeoutExpired:
        return {
            "status": "fail",
            "error_count": 1,
            "apk_produced": False,
            "failure_reason": "Build timed out after 600s",
            "returncode": -1,
        }
    except Exception as e:
        return {
            "status": "fail",
            "error_count": 1,
            "apk_produced": False,
            "failure_reason": str(e),
            "returncode": -1,
        }


def main(args=None):
    parser = argparse.ArgumentParser(description="NasTech Guardian Build Bot")
    parser.add_argument("--profile", default="identity_profile.json",
                        help="Path to identity_profile.json")
    parser.add_argument("--output",  default="build_report.json",
                        help="Path to write build_report.json")
    parser.add_argument("--task",    default="assembleDebug",
                        help="Gradle task to run")
    opts = parser.parse_args(args)

    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")

    load_profile(opts.profile)

    log.info("[build_bot] running gradle task: %s", opts.task)
    report = run_gradle(opts.task)

    with open(opts.output, "w") as f:
        json.dump(report, f, indent=2)

    log.info("[build_bot] report written to %s  status=%s",
             opts.output, report["status"])
    print(json.dumps(report, indent=2))

    # Exit 0 even on build fail — let the workflow decide the action
    return 0


if __name__ == "__main__":
    sys.exit(main())
