"""
NasTech Guardian — Notify Bot
Sends build/health/alert notifications via Telegram and GitHub.
"""

import logging
import os

import requests

log = logging.getLogger(__name__)

TELEGRAM_TOKEN  = os.environ.get("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT   = os.environ.get("TELEGRAM_CHAT_ID", "")
TELEGRAM_API    = "https://api.telegram.org"


class NotifyBot:
    """Delivers notifications over Telegram and GitHub commit statuses."""

    def __init__(self, config: dict = None):
        self.config  = config or {}
        self.token   = self.config.get("telegram_token", TELEGRAM_TOKEN)
        self.chat_id = self.config.get("telegram_chat",  TELEGRAM_CHAT)

    # ── Telegram ──────────────────────────────────────────────────────────

    def send_telegram(self, message: str, parse_mode: str = "Markdown") -> dict:
        if not self.token or not self.chat_id:
            log.warning("[notify_bot] Telegram not configured — skipping")
            return {"status": "skipped"}
        url  = f"{TELEGRAM_API}/bot{self.token}/sendMessage"
        data = {"chat_id": self.chat_id, "text": message, "parse_mode": parse_mode}
        try:
            resp = requests.post(url, json=data, timeout=15)
            return resp.json()
        except Exception as e:
            return {"status": "error", "error": str(e)}

    def build_success(self, tag: str, apk_url: str = "") -> dict:
        msg = f"✅ *NasTech AI Terminal* `{tag}` built successfully!"
        if apk_url:
            msg += f"\n[Download APK]({apk_url})"
        return self.send_telegram(msg)

    def build_failure(self, tag: str, error: str = "") -> dict:
        msg = f"❌ *NasTech AI Terminal* `{tag}` build FAILED"
        if error:
            msg += f"\n```\n{error[:300]}\n```"
        return self.send_telegram(msg)

    def health_alert(self, issue: str) -> dict:
        msg = f"⚠️ *NasTech Health Alert*\n{issue}"
        return self.send_telegram(msg)


def main():
    bot = NotifyBot()
    result = bot.send_telegram("⬡ NasTech Guardian notify_bot loaded successfully")
    log.info("[notify_bot] %s", result)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
