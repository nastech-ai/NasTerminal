"""
NasTech Guardian — Notify Bot
Sends build/health/alert notifications via Telegram and GitHub Summaries.
Accepts --repo, --sha, --run-url, --output CLI args and writes notify_report.json.
"""

import argparse
import json
import logging
import os
import sys
import urllib.request

log = logging.getLogger(__name__)

TELEGRAM_API = "https://api.telegram.org"


def _telegram_send(token: str, chat_id: str, message: str) -> dict:
    if not token or not chat_id:
        log.warning("[notify_bot] Telegram not configured — skipping")
        return {"status": "skipped"}
    url  = f"{TELEGRAM_API}/bot{token}/sendMessage"
    data = json.dumps({
        "chat_id":                  chat_id,
        "text":                     message,
        "parse_mode":               "HTML",
        "disable_web_page_preview": True,
    }).encode()
    try:
        req = urllib.request.Request(
            url, data=data,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=15) as r:
            return {"status": "ok", "http_status": r.status}
    except Exception as e:
        log.warning("[notify_bot] Telegram error: %s", e)
        return {"status": "error", "error": str(e)}


def build_message(repo: str, sha: str, run_url: str, stage_results: dict) -> str:
    def icon(s: str) -> str:
        return {
            "success": "✅", "pass": "✅",
            "failure": "❌", "fail": "❌",
            "skipped": "⏭️", "cancelled": "🚫",
            "error":   "🔴",
        }.get(str(s).lower(), "⚠️")

    overall = "✅ PASSED" if all(
        str(v).lower() in ("success", "pass", "skipped", "cancelled", "")
        for v in stage_results.values()
    ) else "❌ FAILED"

    rows = "\n".join(
        f"  {icon(v)} {k}: {v}"
        for k, v in stage_results.items()
    )

    return (
        f"🛡️ <b>NasTech Guardian — {overall}</b>\n\n"
        f"<b>Repo:</b> {repo}\n"
        f"<b>SHA:</b>  <code>{sha[:7] if sha else 'unknown'}</code>\n\n"
        f"{rows}\n\n"
        f"<a href='{run_url}'>📋 View Run</a>\n\n"
        f"<i>NasTech Guardian · Auto-notification</i>"
    )


def main(args=None):
    parser = argparse.ArgumentParser(description="NasTech Guardian Notify Bot")
    parser.add_argument("--repo",    default="nastech-ai/NasTerminal")
    parser.add_argument("--sha",     default="")
    parser.add_argument("--run-url", default="")
    parser.add_argument("--output",  default="notify_report.json")
    opts = parser.parse_args(args)

    logging.basicConfig(level=logging.INFO,
                        format="%(levelname)s:%(name)s:%(message)s")

    token   = os.environ.get("TELEGRAM_BOT_TOKEN", "")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "")

    stage_results = {
        "Verify":     os.environ.get("VERIFY_STATUS",  ""),
        "Identity":   os.environ.get("IDENTITY_STATUS",""),
        "Dependency": os.environ.get("DEP_STATUS",     ""),
        "Health":     os.environ.get("HEALTH_STATUS",  ""),
        "Build":      os.environ.get("BUILD_STATUS",   ""),
        "Repair":     os.environ.get("REPAIR_STATUS",  ""),
        "Release":    os.environ.get("RELEASE_STATUS", ""),
    }

    message = build_message(opts.repo, opts.sha, opts.run_url, stage_results)
    tg_result = _telegram_send(token, chat_id, message)

    overall_pass = all(
        str(v).lower() in ("success", "pass", "skipped", "cancelled", "")
        for v in stage_results.values()
    )

    report = {
        "final_state": "COMPLETE" if overall_pass else "FAILED",
        "repo":        opts.repo,
        "sha":         opts.sha[:7] if opts.sha else "",
        "telegram":    tg_result,
        "stages":      stage_results,
    }

    with open(opts.output, "w") as f:
        json.dump(report, f, indent=2)

    log.info("[notify_bot] report written to %s  final_state=%s",
             opts.output, report["final_state"])
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
