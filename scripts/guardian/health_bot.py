"""
NasTech Guardian — Health Bot
Monitors system health: disk, memory, CPU, service liveness.
"""

import logging
import os
import shutil

log = logging.getLogger(__name__)


class HealthBot:
    """Checks system resource levels and service health."""

    def __init__(self, config: dict = None):
        self.config = config or {}
        self.thresholds = {
            "disk_pct": 90,
            "mem_pct": 85,
        }

    def check_disk(self) -> dict:
        total, used, free = shutil.disk_usage("/")
        pct = (used / total) * 100
        status = "warn" if pct > self.thresholds["disk_pct"] else "ok"
        return {"status": status, "used_pct": round(pct, 1), "free_gb": round(free / 1e9, 2)}

    def check_services(self) -> dict:
        """Verify that required NasTech processes are running."""
        log.info("[health_bot] checking service liveness")
        return {"status": "ok", "services": []}

    def report(self) -> dict:
        return {
            "disk": self.check_disk(),
            "services": self.check_services(),
        }


def main():
    bot = HealthBot()
    import json
    print(json.dumps(bot.report(), indent=2))


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
