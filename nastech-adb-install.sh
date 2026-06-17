#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════
#  NasTech AI Terminal — ADB Install Helper
#
#  Fixes INSTALL_FAILED_SHARED_USER_INCOMPATIBLE by uninstalling the
#  old app (which may have had android:sharedUserId) before installing
#  the new one. Must be run from a machine with adb connected to the
#  device (USB debugging enabled).
#
#  Usage:
#    ./nastech-adb-install.sh                      # auto-find APK
#    ./nastech-adb-install.sh path/to/nastech.apk  # explicit APK
# ══════════════════════════════════════════════════════════════════════

set -euo pipefail

PKG="com.termux"
R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; C='\033[1;36m'; N='\033[0m'

echo -e "${C}⬡ NasTech AI Terminal — ADB Installer${N}"
echo -e "${N}  Fixes INSTALL_FAILED_SHARED_USER_INCOMPATIBLE${N}"
echo ""

# ── Check adb is available ────────────────────────────────────────────
if ! command -v adb &>/dev/null; then
    echo -e "${R}[✗] adb not found. Install Android Platform Tools:${N}"
    echo "    https://developer.android.com/tools/releases/platform-tools"
    exit 1
fi

# ── Check device connected ────────────────────────────────────────────
DEVICES=$(adb devices | grep -v "List of" | grep "device$" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    echo -e "${R}[✗] No device connected. Enable USB Debugging and plug in your device.${N}"
    exit 1
fi
echo -e "${G}[✓] Device connected${N}"

# ── Find APK ──────────────────────────────────────────────────────────
if [ -n "${1:-}" ]; then
    APK="$1"
else
    # Auto-find the most recently built debug APK
    APK=$(find app/build/outputs/apk/debug -name "*.apk" \
          ! -name "*unsigned*" 2>/dev/null | sort -t_ -k1 | tail -1)
fi

if [ -z "${APK:-}" ] || [ ! -f "${APK:-}" ]; then
    echo -e "${R}[✗] APK not found. Build first:${N}"
    echo "    ./gradlew assembleDebug"
    echo "    Or provide the APK path: $0 path/to/your.apk"
    exit 1
fi
echo -e "${G}[✓] APK: $APK${N}"

# ── Uninstall existing package (incl. shared user group) ─────────────
echo ""
echo -e "${Y}[→] Uninstalling existing $PKG (clears shared user group)…${N}"
if adb shell pm list packages | grep -q "package:$PKG"; then
    adb uninstall "$PKG" 2>/dev/null && \
        echo -e "${G}[✓] Uninstalled $PKG${N}" || \
        echo -e "${Y}[!] Uninstall failed — may not have been installed${N}"
else
    echo -e "${Y}[!] $PKG not installed — fresh install${N}"
fi

# Also uninstall common Termux plugin apps that share the same sharedUserId,
# since they keep the shared user group alive even after our app is removed.
for PLUGIN in com.termux.api com.termux.boot com.termux.styling com.termux.widget; do
    if adb shell pm list packages | grep -q "package:$PLUGIN"; then
        echo -e "${Y}[→] Removing plugin $PLUGIN (shares sharedUserId)…${N}"
        adb uninstall "$PLUGIN" 2>/dev/null && \
            echo -e "${G}[✓] Removed $PLUGIN${N}" || true
    fi
done

# ── Install NasTech ───────────────────────────────────────────────────
echo ""
echo -e "${C}[→] Installing NasTech AI Terminal…${N}"
if adb install -r "$APK"; then
    echo ""
    echo -e "${G}╔═══════════════════════════════════════════╗${N}"
    echo -e "${G}║  ✓ NasTech AI Terminal installed!         ║${N}"
    echo -e "${G}╚═══════════════════════════════════════════╝${N}"
    echo ""
    echo -e "  Launch: adb shell am start -n $PKG/.app.TermuxActivity"
else
    echo ""
    echo -e "${R}[✗] Install failed. Try:${N}"
    echo "    adb uninstall $PKG"
    echo "    adb install $APK"
    exit 1
fi
