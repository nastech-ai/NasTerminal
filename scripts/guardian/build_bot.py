"""
NasTech Guardian — Build Bot
Triggers, monitors, and reports on Gradle / CI build pipelines.
"""

import logging
import os
import subprocess

log = logging.getLogger(__name__)

GRADLE_TASKS = ["assembleDebug", "assembleRelease", "test"]


class BuildBot:
    """Manages Android / Gradle build lifecycle."""

    def __init__(self, config: dict = None):
        self.config = config or {}
        self.project_root = self.config.get("project_root", ".")

    def build(self, task: str = "assembleDebug") -> dict:
        log.info("[build_bot] running gradle task: %s", task)
        gradlew = os.path.join(self.project_root, "gradlew")
        try:
            result = subprocess.run(
                [gradlew, task, "--no-daemon"],
                cwd=self.project_root,
                capture_output=True, text=True, timeout=600
            )
            return {
                "status": "success" if result.returncode == 0 else "failure",
                "returncode": result.returncode,
                "stdout": result.stdout[-3000:],
                "stderr": result.stderr[-1000:],
            }
        except Exception as e:
            return {"status": "error", "error": str(e)}

    def get_apk_paths(self) -> list:
        apk_dir = os.path.join(self.project_root, "app", "build", "outputs", "apk")
        paths = []
        if os.path.isdir(apk_dir):
            for root, _, files in os.walk(apk_dir):
                for f in files:
                    if f.endswith(".apk"):
                        paths.append(os.path.join(root, f))
        return paths


def main():
    bot = BuildBot()
    print(bot.get_apk_paths())


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
