package com.termux.app.nastech;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NasTech Install Progress Overlay
 *
 * Shows a terminal-style dialog during first boot.
 * Streams stdout from nastech_install.sh line-by-line in real time.
 * Always dismissible — the terminal session stays fully usable in the background.
 */
public class NasTechInstallProgressDialog {

    private static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";

    /**
     * Show the progress dialog and start the installer.
     * Call from the UI thread only.
     */
    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity, R.style.NasTechInstallDialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_nastech_install_progress);
        dialog.setCancelable(true);

        // Full-width, 90 % screen height
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.88f));
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView    logView    = dialog.findViewById(R.id.nastech_install_log);
        ScrollView  scroll     = dialog.findViewById(R.id.nastech_install_scroll);
        ProgressBar spinner    = dialog.findViewById(R.id.nastech_install_progress);
        ProgressBar strip      = dialog.findViewById(R.id.nastech_install_strip);
        TextView    statusView = dialog.findViewById(R.id.nastech_install_status);
        Button      dismissBtn = dialog.findViewById(R.id.nastech_install_dismiss);
        Button      bgBtn      = dialog.findViewById(R.id.nastech_install_bg);

        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService exec = Executors.newSingleThreadExecutor();

        // "Run in background" just dismisses the dialog; shell will pick it up via .bash_profile
        if (bgBtn != null) bgBtn.setOnClickListener(v -> dialog.dismiss());
        if (dismissBtn != null) {
            dismissBtn.setVisibility(View.GONE); // shown only when done
            dismissBtn.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.setOnDismissListener(d -> exec.shutdownNow());
        dialog.show();

        // ── Installer subprocess ──────────────────────────────────────────────
        exec.submit(() -> {
            String installScript = NasTechManager.getNasTechHome() + "/nastech_install.sh";

            appendLine(main, logView, scroll,
                "⬡ NasTech Agent — First Boot Setup", true);
            appendLine(main, logView, scroll,
                "──────────────────────────────────────────────", false);

            try {
                ProcessBuilder pb = new ProcessBuilder(TERMUX_BASH, installScript);
                pb.environment().put("NASTECH_HOME", NasTechManager.getNasTechHome());
                pb.environment().put("GROQ_API_KEY",       NasTechManager.getGroqApiKey());
                pb.environment().put("GEMINI_API_KEY",     NasTechManager.getGeminiApiKey());
                pb.environment().put("OPENROUTER_API_KEY", NasTechManager.getApiKey());
                pb.environment().put("HOME",
                    System.getenv("HOME") != null ? System.getenv("HOME") : "");
                pb.redirectErrorStream(true); // merge stderr into stdout

                Process proc = pb.start();

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // Strip ANSI colour codes so the log stays clean
                        final String clean = line.replaceAll("\u001B\\[[;\\d]*m", "").trim();
                        if (!clean.isEmpty()) appendLine(main, logView, scroll, clean, false);
                    }
                }

                int exitCode = proc.waitFor();
                final boolean success = (exitCode == 0);

                main.post(() -> {
                    appendLine(main, logView, scroll,
                        "──────────────────────────────────────────────", false);
                    if (success) {
                        appendLine(main, logView, scroll, "✓ Installation complete!", true);
                        if (statusView != null) statusView.setText("Done ✓");
                    } else {
                        appendLine(main, logView, scroll,
                            "✗ Installer exited with code " + exitCode +
                            " — open terminal and run: nastech install", false);
                        if (statusView != null) statusView.setText("Failed (exit " + exitCode + ")");
                    }
                    if (spinner    != null) spinner.setVisibility(View.GONE);
                    if (strip      != null) strip.setVisibility(View.GONE);
                    if (dismissBtn != null) dismissBtn.setVisibility(View.VISIBLE);
                    if (bgBtn      != null) bgBtn.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                main.post(() -> {
                    appendLine(main, logView, scroll, "✗ Error: " + e.getMessage(), false);
                    appendLine(main, logView, scroll,
                        "Open terminal and run: nastech install", false);
                    if (spinner    != null) spinner.setVisibility(View.GONE);
                    if (strip      != null) strip.setVisibility(View.GONE);
                    if (statusView != null) statusView.setText("Error");
                    if (dismissBtn != null) dismissBtn.setVisibility(View.VISIBLE);
                    if (bgBtn      != null) bgBtn.setVisibility(View.GONE);
                });
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void appendLine(Handler main, TextView logView,
                                   ScrollView scroll, String line, boolean header) {
        main.post(() -> {
            if (logView == null) return;
            CharSequence existing = logView.getText();
            String prefix = (existing.length() > 0) ? "\n" : "";
            String text = header ? ("  " + line) : ("  " + line);
            logView.append(prefix + text);
            if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }
}
