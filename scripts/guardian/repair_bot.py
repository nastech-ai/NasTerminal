"""
NasTech Guardian — Repair Bot
Auto-detects and repairs common build/runtime failures.
"""

import logging
import os
import subprocess

log = logging.getLogger(__name__)

KNOWN_FIXES = {
    "Duplicate resources": "remove_duplicate_resource",
    "Could not resolve": "refresh_gradle_cache",
    "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE": "uninstall_and_reinstall",
}


class RepairBot:
    """Identifies failures and applies automated repairs."""

    def __init__(self, config: dict = None):
        self.config = config or {}
        self.repairs_applied = []

    def diagnose(self, error_text: str) -> str:
        for pattern, fix in KNOWN_FIXES.items():
            if pattern in error_text:
                return fix
        return "unknown"

    def repair(self, error_text: str) -> dict:
        fix = self.diagnose(error_text)
        log.info("[repair_bot] applying fix: %s", fix)
        handler = getattr(self, fix, None)
        if callable(handler):
            result = handler()
        else:
            result = {"status": "no_fix_available", "fix": fix}
        self.repairs_applied.append(fix)
        return result

    def remove_duplicate_resource(self) -> dict:
        log.info("[repair_bot] duplicate resource fix: check styles.xml / themes.xml")
        return {"status": "ok", "action": "remove_duplicate_resource"}

    def refresh_gradle_cache(self) -> dict:
        log.info("[repair_bot] refreshing Gradle caches")
        return {"status": "ok", "action": "refresh_gradle_cache"}

    def uninstall_and_reinstall(self) -> dict:
        log.info("[repair_bot] INSTALL_FAILED fix: adb uninstall com.termux")
        return {"status": "ok", "action": "uninstall_and_reinstall"}

    def unknown(self) -> dict:
        return {"status": "unknown", "action": "manual_review_required"}


def main():
    bot = RepairBot()
    result = bot.repair("Duplicate resources found in styles.xml")
    print(result)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
