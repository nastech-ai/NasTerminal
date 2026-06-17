package com.termux.app.nastech;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NasTech AI Terminal — Core Manager
 *
 * On first boot, drops a marker file that nastech_init.sh picks up to run the official
 * NasTech Agent installer (auto-detects Termux) in the background.
 * The "$ nastech install" command calls the same script on demand.
 *
 * All AI logic lives in ai_coordinator.py (NasTech Agent). This class is Android/Termux
 * plumbing only — no AI code is duplicated here.
 */
public class NasTechManager {

    // NasTech Agent one-line installer (auto-detects Termux vs Linux vs macOS)
    private static final String AGENT_INSTALLER_URL =
        "https://raw.githubusercontent.com/nastech-ai/NasTech-Agent/main/scripts/install.sh";

    private static final String PREFS            = "nastech_prefs";
    private static final String KEY_INITIALIZED  = "nastech_initialized";
    private static final String KEY_BIOMETRIC    = "biometric_lock";
    private static final String KEY_GROQ_KEY     = "groq_api_key";
    private static final String KEY_GEMINI_KEY   = "gemini_api_key";
    private static final String KEY_OR_KEY       = "openrouter_api_key";

    // Marker file name written into ~/.nastech/ on very first init
    private static final String FIRST_BOOT_MARKER    = ".first_boot_pending";
    // Written by installer when it completes successfully
    private static final String INSTALLED_MARKER     = ".agent_installed";
    // SharedPrefs key: show install overlay once on first boot
    private static final String KEY_SHOW_OVERLAY     = "nastech_show_install_overlay";

    private static Context sAppContext;

    // ── Public init ───────────────────────────────────────────────────────────

    public static void init(Context context) {
        sAppContext = context.getApplicationContext();
        SharedPreferences prefs = sAppContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        boolean firstRun = !prefs.getBoolean(KEY_INITIALIZED, false);
        if (firstRun) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_BIOMETRIC, false)
                .putBoolean(KEY_SHOW_OVERLAY, true)   // show install overlay once
                .apply();
        }

        String home = System.getenv("HOME") != null
            ? System.getenv("HOME") : context.getFilesDir().getParent();

        File nasTechDir = new File(home, ".nastech");
        if (!nasTechDir.exists()) nasTechDir.mkdirs();

        // 1. Deploy ai_coordinator.py from APK assets → ~/.nastech/
        copyAsset(context, "ai_coordinator.py", new File(nasTechDir, "ai_coordinator.py"));

        // 2. Write thin nastech_ai.py wrapper that delegates to ai_coordinator.py
        writeAIWrapper(nasTechDir);

        // 3. Write speak script (TTS — Termux-specific, not in agent)
        writeSpeakScript(nasTechDir);

        // 4. Write the NasTech Agent installer script (calls remote URL)
        writeInstallerScript(nasTechDir);

        // 5. Drop first-boot marker so the shell auto-runs the installer once
        if (firstRun) {
            dropFirstBootMarker(nasTechDir);
        }

        // 6. Write shell init that exports all API keys + wires commands
        writeShellInit(nasTechDir, home, prefs);
    }

    // ── Asset deployment ──────────────────────────────────────────────────────

    private static void copyAsset(Context ctx, String assetName, File dest) {
        try (InputStream in = ctx.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            dest.setExecutable(true, false);
        } catch (IOException ignored) {}
    }

    // ── First-boot marker ─────────────────────────────────────────────────────

    private static void dropFirstBootMarker(File dir) {
        try {
            new File(dir, FIRST_BOOT_MARKER).createNewFile();
        } catch (IOException ignored) {}
    }

    // ── Installer script — calls the remote NasTech Agent URL ─────────────────

    /**
     * nastech_install.sh:
     *   - Uses Termux bash (not /bin/bash)
     *   - Ensures curl is available via pkg (Termux package manager)
     *   - Calls the official installer URL which auto-detects Termux
     *   - Does NOT call sudo, apt, apt-get — Termux doesn't use those
     */
    private static void writeInstallerScript(File dir) {
        String script =
            "#!" + getTermuxBin("bash") + "\n" +
            "# NasTech AI Terminal — Agent Installer\n" +
            "# Calls the official NasTech Agent installer (auto-detects Termux)\n\n" +

            "NASTECH_HOME=\"${NASTECH_HOME:-" + dir.getAbsolutePath() + "}\"\n" +
            "INSTALLED_MARKER=\"$NASTECH_HOME/" + INSTALLED_MARKER + "\"\n\n" +

            "echo -e \"\\033[1;36m⬡ NasTech Agent Installer\\033[0m\"\n" +
            "echo -e \"\\033[0;90m  Termux detected — using pkg, no sudo\\033[0m\"\n\n" +

            "# Ensure curl is present (Termux — use pkg, never apt-get)\n" +
            "if ! command -v curl &>/dev/null; then\n" +
            "  echo -e \"\\033[1;33m[→] Installing curl via pkg…\\033[0m\"\n" +
            "  pkg install -y curl 2>/dev/null || true\n" +
            "fi\n\n" +

            "if ! command -v curl &>/dev/null; then\n" +
            "  echo -e \"\\033[1;31m[✗] curl unavailable. Run: pkg install curl\\033[0m\"\n" +
            "  exit 1\n" +
            "fi\n\n" +

            "echo -e \"\\033[0;90m  Running: curl -fsSL " + AGENT_INSTALLER_URL + " | bash\\033[0m\"\n" +
            "curl -fsSL \"" + AGENT_INSTALLER_URL + "\" | bash\n" +
            "STATUS=$?\n\n" +

            "if [ $STATUS -eq 0 ]; then\n" +
            "  touch \"$INSTALLED_MARKER\"\n" +
            "  echo -e \"\\033[1;32m[✓] NasTech Agent installed\\033[0m\"\n" +
            "else\n" +
            "  echo -e \"\\033[1;31m[✗] Installer returned $STATUS — check your connection\\033[0m\"\n" +
            "fi\n";

        try {
            File f = new File(dir, "nastech_install.sh");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(script.getBytes());
            }
            f.setExecutable(true, false);
        } catch (IOException ignored) {}
    }

    // ── Shell init ────────────────────────────────────────────────────────────

    private static void writeShellInit(File dir, String homeDir, SharedPreferences prefs) {
        String nasTechHome = dir.getAbsolutePath();
        String groqKey     = prefs.getString(KEY_GROQ_KEY, "");
        String geminiKey   = prefs.getString(KEY_GEMINI_KEY, "");
        String orKey       = prefs.getString(KEY_OR_KEY, "");

        String rc =
            "#!" + getTermuxBin("bash") + "\n" +
            "# NasTech AI Terminal — Auto-generated (do not edit)\n" +
            "export NASTECH_HOME=\""       + nasTechHome + "\"\n" +
            "export NASTECH_VERSION=\"v6\"\n" +
            "export GROQ_API_KEY=\""       + groqKey     + "\"\n" +
            "export GEMINI_API_KEY=\""     + geminiKey   + "\"\n" +
            "export OPENROUTER_API_KEY=\"" + orKey       + "\"\n\n" +

            // ── First-boot auto-installer ──────────────────────────────
            // Runs once silently in the background when the marker exists.
            // After it finishes (or if already installed), marker is removed.
            "if [ -f \"$NASTECH_HOME/" + FIRST_BOOT_MARKER + "\" ]; then\n" +
            "  if [ ! -f \"$NASTECH_HOME/" + INSTALLED_MARKER + "\" ]; then\n" +
            "    echo -e \"\\033[1;36m⬡ First boot — installing NasTech Agent in background…\\033[0m\"\n" +
            "    echo -e \"\\033[0;90m  Progress will print below. Use the terminal freely.\\033[0m\"\n" +
            "    # Run installer in background so the shell prompt appears immediately\n" +
            "    ( bash \"$NASTECH_HOME/nastech_install.sh\" ) &\n" +
            "  fi\n" +
            "  rm -f \"$NASTECH_HOME/" + FIRST_BOOT_MARKER + "\"\n" +
            "fi\n\n" +

            // ── Command dispatcher ────────────────────────────────────
            "nastech_cmd() {\n" +
            "  local cmd=\"$1\"; shift\n" +
            "  case \"$cmd\" in\n" +
            "    ai)      python3 \"$NASTECH_HOME/nastech_ai.py\" \"$@\" ;;\n" +
            "    speak)   bash \"$NASTECH_HOME/nastech_speak.sh\" \"$@\" ;;\n" +
            "    ubuntu)  bash \"$NASTECH_HOME/ubuntu_layer.sh\" \"$@\" ;;\n" +
            "    install) bash \"$NASTECH_HOME/nastech_install.sh\" \"$@\" ;;\n" +
            "    agent)   python3 \"$NASTECH_HOME/ai_coordinator.py\" \"$@\" ;;\n" +
            "    system)  python3 \"$NASTECH_HOME/ai_coordinator.py\" --prompt \"system audit: report Termux packages, storage, Python version, and agent status\" ;;\n" +
            "    help)\n" +
            "      echo -e \"\\033[1;36m⬡ NasTech AI Terminal v6\\033[0m\"\n" +
            "      echo -e \"\\033[0;90m  Powered by NasTech Agent (Groq → Gemini → OpenRouter)\\033[0m\"\n" +
            "      echo \"\"\n" +
            "      echo \"  ai [prompt]      Ask the AI\"\n" +
            "      echo \"  nastech install  Install/update NasTech Agent\"\n" +
            "      echo \"  speak [text]     Piper TTS offline voice\"\n" +
            "      echo \"  ubuntu           Ubuntu proot shell\"\n" +
            "      echo \"  agent --help     Raw ai_coordinator.py CLI\"\n" +
            "      echo \"  nastech system   System audit\"\n" +
            "      ;;\n" +
            "    *) echo \"Unknown: $cmd — try: nastech help\" ;;\n" +
            "  esac\n" +
            "}\n\n" +
            "nastech() { nastech_cmd \"$@\"; }\n" +
            "ai()      { nastech_cmd ai \"$@\"; }\n" +
            "speak()   { nastech_cmd speak \"$@\"; }\n\n" +
            "echo -e \"\\033[1;36m⬡ NasTech AI Terminal v6\\033[0m\"\n" +
            "echo -e \"\\033[0;90m  ai [prompt]   speak [text]   nastech help   nastech install\\033[0m\"\n";

        try {
            File rcFile = new File(dir, "nastech_init.sh");
            try (FileOutputStream fos = new FileOutputStream(rcFile)) {
                fos.write(rc.getBytes());
            }

            String sourceLine =
                "\n# NasTech AI Terminal\n" +
                "export NASTECH_HOME=\"" + nasTechHome + "\"\n" +
                ". \"" + nasTechHome + "/nastech_init.sh\"\n";

            // Termux opens LOGIN shells → .bash_profile; also cover .bashrc
            appendIfMissing(new File(homeDir, ".bash_profile"), sourceLine, "nastech_init.sh");
            appendIfMissing(new File(homeDir, ".bashrc"),       sourceLine, "nastech_init.sh");
        } catch (IOException ignored) {}
    }

    // ── nastech_ai.py — thin wrapper around ai_coordinator.py ─────────────────

    private static void writeAIWrapper(File dir) {
        // Android-specific glue only — all AI logic stays in ai_coordinator.py
        String wrapper =
            "#!/usr/bin/env python3\n" +
            "# NasTech AI — delegates to ai_coordinator.py (NasTech Agent)\n" +
            "# This file is Android/Termux glue, not a copy of the AI logic.\n" +
            "import sys, os, subprocess\n\n" +
            "home = os.environ.get('NASTECH_HOME',\n" +
            "        os.path.join(os.path.expanduser('~'), '.nastech'))\n" +
            "coordinator = os.path.join(home, 'ai_coordinator.py')\n\n" +
            "if len(sys.argv) < 2:\n" +
            "    print('\\033[1;36m⬡ NasTech AI — powered by NasTech Agent\\033[0m')\n" +
            "    print('  Usage : ai [prompt]')\n" +
            "    print('  Chain : Groq llama-3.3-70b → Gemini 2.0-flash → OpenRouter')\n" +
            "    sys.exit(0)\n\n" +
            "if not os.path.exists(coordinator):\n" +
            "    print('\\033[1;31m✗ ai_coordinator.py missing.\\033[0m')\n" +
            "    print('  Run: nastech install')\n" +
            "    sys.exit(1)\n\n" +
            "prompt = ' '.join(sys.argv[1:])\n" +
            "result = subprocess.run(\n" +
            "    ['python3', coordinator, '--prompt', prompt],\n" +
            "    env=os.environ.copy()\n" +
            ")\n" +
            "sys.exit(result.returncode)\n";

        try {
            File f = new File(dir, "nastech_ai.py");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(wrapper.getBytes());
            }
            f.setExecutable(true, false);
        } catch (IOException ignored) {}
    }

    // ── Speak script — Termux TTS (not in agent) ──────────────────────────────

    private static void writeSpeakScript(File dir) {
        // Termux-specific: uses pkg (not apt), termux-media-player or mpv
        String script =
            "#!" + getTermuxBin("bash") + "\n" +
            "# NasTech AI Terminal — Piper TTS (Termux)\n" +
            "# Uses pkg to install, not apt/apt-get (Termux doesn't have those)\n" +
            "PIPER_DIR=\"$NASTECH_HOME/piper\"\n" +
            "VOICE_DIR=\"$NASTECH_HOME/voices\"\n" +
            "VOICE_MODEL=\"$VOICE_DIR/en_US-lessac-medium.onnx\"\n" +
            "VOICE_URL=\"https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium\"\n" +
            "TMP_WAV=\"/tmp/nastech_speak.wav\"\n" +
            "TEXT=\"$*\"\n" +
            "[ -z \"$TEXT\" ] && { echo 'Usage: speak [text]'; exit 0; }\n\n" +

            "# Termux: install curl/mpv via pkg if missing — no sudo\n" +
            "command -v curl &>/dev/null || pkg install -y curl 2>/dev/null || true\n\n" +

            "if ! command -v piper &>/dev/null; then\n" +
            "  echo -e \"\\033[1;33m⬡ Installing Piper TTS via pip3…\\033[0m\"\n" +
            "  pip3 install --quiet piper-tts 2>/dev/null || {\n" +
            "    # pip3 not found — install Python first (Termux)\n" +
            "    pkg install -y python 2>/dev/null\n" +
            "    pip3 install --quiet piper-tts 2>/dev/null || true\n" +
            "  }\n" +
            "fi\n\n" +

            "command -v piper &>/dev/null || python3 -m piper --help &>/dev/null || {\n" +
            "  echo -e \"\\033[1;31m✗ Piper unavailable. pip3 install piper-tts\\033[0m\"\n" +
            "  exit 1\n" +
            "}\n\n" +

            "[ ! -f \"$VOICE_MODEL\" ] && {\n" +
            "  echo -e \"\\033[1;33m⬡ Downloading voice model…\\033[0m\"\n" +
            "  mkdir -p \"$VOICE_DIR\"\n" +
            "  curl -sL \"$VOICE_URL/en_US-lessac-medium.onnx\"      -o \"$VOICE_MODEL\"\n" +
            "  curl -sL \"$VOICE_URL/en_US-lessac-medium.onnx.json\" -o \"${VOICE_MODEL}.json\"\n" +
            "}\n\n" +

            "PIPER_CMD=\"piper\"\n" +
            "command -v piper &>/dev/null || PIPER_CMD=\"python3 -m piper\"\n" +
            "echo \"$TEXT\" | $PIPER_CMD --model \"$VOICE_MODEL\" --output_file \"$TMP_WAV\" 2>/dev/null\n\n" +

            "# Termux audio playback — prefer termux-media-player, then mpv\n" +
            "if command -v termux-media-player &>/dev/null; then\n" +
            "  termux-media-player play \"$TMP_WAV\"\n" +
            "elif command -v mpv &>/dev/null; then\n" +
            "  mpv --no-terminal \"$TMP_WAV\" 2>/dev/null\n" +
            "else\n" +
            "  echo \"✓ Audio: $TMP_WAV — run: pkg install mpv  or  pkg install termux-api\"\n" +
            "fi\n";

        try {
            File f = new File(dir, "nastech_speak.sh");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(script.getBytes());
            }
            f.setExecutable(true, false);
        } catch (IOException ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void appendIfMissing(File file, String content, String marker) throws IOException {
        if (file.exists()) {
            byte[] data = readFile(file);
            if (new String(data).contains(marker)) return;
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write(content.getBytes());
            }
        } else {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes());
            }
        }
    }

    private static byte[] readFile(File f) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        fis.read(data);
        fis.close();
        return data;
    }

    // ── Prefs accessors ───────────────────────────────────────────────────────

    public static SharedPreferences getPrefs() {
        return sAppContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getGroqApiKey()    { return getPrefs().getString(KEY_GROQ_KEY,   ""); }
    public static String getGeminiApiKey()  { return getPrefs().getString(KEY_GEMINI_KEY, ""); }
    public static String getApiKey()        { return getPrefs().getString(KEY_OR_KEY,     ""); }

    public static void setGroqApiKey(String k)   { getPrefs().edit().putString(KEY_GROQ_KEY,   k).apply(); }
    public static void setGeminiApiKey(String k) { getPrefs().edit().putString(KEY_GEMINI_KEY, k).apply(); }
    public static void setApiKey(String k)        { getPrefs().edit().putString(KEY_OR_KEY,     k).apply(); }

    public static boolean isBiometricLockEnabled() { return getPrefs().getBoolean(KEY_BIOMETRIC, false); }
    public static void setBiometricLock(boolean e) { getPrefs().edit().putBoolean(KEY_BIOMETRIC, e).apply(); }

    /** True once on first boot — triggers the install progress overlay in TermuxActivity. */
    public static boolean shouldShowInstallOverlay() {
        return getPrefs().getBoolean(KEY_SHOW_OVERLAY, false);
    }

    /** Call immediately before showing the overlay so it never shows twice. */
    public static void clearInstallOverlay() {
        getPrefs().edit().putBoolean(KEY_SHOW_OVERLAY, false).apply();
    }

    /**
     * Returns the path to a binary inside the Termux prefix for the current install.
     * Derived from context — never hardcoded — so it works even if the applicationId changes.
     *   e.g. getTermuxBin("bash")    → /data/data/com.termux/files/usr/bin/bash
     *        getTermuxBin("python3") → /data/data/com.termux/files/usr/bin/python3
     */
    public static String getTermuxBin(String binary) {
        // context.getFilesDir() = /data/data/<packageName>/files
        return sAppContext.getFilesDir().getAbsolutePath() + "/usr/bin/" + binary;
    }

    /** Full path to ~/.nastech — where ai_coordinator.py and all scripts live. */
    public static String getNasTechHome() {
        String env = System.getenv("HOME");
        return (env != null ? env : sAppContext.getFilesDir().getParent()) + "/.nastech";
    }
}
