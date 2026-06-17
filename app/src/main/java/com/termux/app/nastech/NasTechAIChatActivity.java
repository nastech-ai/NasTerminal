package com.termux.app.nastech;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NasTech AI Chat — Android UI wrapper.
 * All AI logic is handled by ai_coordinator.py (NasTech Agent).
 * This Activity runs: python3 ~/.nastech/ai_coordinator.py --prompt "..." --json-output
 * and displays the result. No AI logic is duplicated here.
 */
public class NasTechAIChatActivity extends AppCompatActivity {

    private static final String PYTHON3 = "/data/data/com.termux/files/usr/bin/python3";

    private LinearLayout mChatContainer;
    private ScrollView   mScrollView;
    private EditText     mInput;
    private ImageButton  mSendBtn;
    private ImageButton  mCloseBtn;
    private TextView     mStatus;

    private final ExecutorService mExecutor   = Executors.newSingleThreadExecutor();
    private final Handler         mMainThread = new Handler(Looper.getMainLooper());

    // Conversation history sent to ai_coordinator via --system context
    private final List<String> mHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nastech_ai_chat);

        mChatContainer = findViewById(R.id.nastech_chat_container);
        mScrollView    = findViewById(R.id.nastech_chat_scroll);
        mInput         = findViewById(R.id.nastech_chat_input);
        mSendBtn       = findViewById(R.id.nastech_chat_send);
        mCloseBtn      = findViewById(R.id.nastech_chat_close);
        mStatus        = findViewById(R.id.nastech_chat_status);

        if (mCloseBtn != null) mCloseBtn.setOnClickListener(v -> finish());
        if (mSendBtn  != null) mSendBtn.setOnClickListener(v -> sendMessage());

        if (mInput != null) {
            mInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    sendMessage();
                    return true;
                }
                return false;
            });
        }

        appendBubble("system", getString(R.string.nastech_chat_welcome));
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private void sendMessage() {
        if (mInput == null) return;
        String text = mInput.getText().toString().trim();
        if (text.isEmpty()) return;

        mInput.setText("");
        appendBubble("user", text);
        mHistory.add("User: " + text);

        setInputEnabled(false);
        setStatus(getString(R.string.nastech_ai_thinking));

        mExecutor.submit(() -> callAgent(text));
    }

    // ── Agent call — subprocess to ai_coordinator.py ──────────────────────────

    private void callAgent(String userPrompt) {
        String coordinator = NasTechManager.getNasTechHome() + "/ai_coordinator.py";

        // Build context from last 6 turns (same as the bot does)
        String context = String.join("\n", mHistory.subList(
                Math.max(0, mHistory.size() - 12), mHistory.size()));
        String fullPrompt = mHistory.size() > 1 ? context : userPrompt;

        String system =
            "You are NasTech AI, the intelligent assistant built into NasTech AI Terminal " +
            "(a Termux-based Android app). Help with terminal commands, programming, " +
            "Android, CI/CD, and general questions. Be concise. Use code blocks.";

        String response = null;
        String provider = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON3, coordinator,
                "--prompt", fullPrompt,
                "--system", system,
                "--json-output"
            );

            // Export all API keys so ai_coordinator picks them up
            Map<String, String> env = pb.environment();
            env.put("GROQ_API_KEY",        NasTechManager.getGroqApiKey());
            env.put("GEMINI_API_KEY",       NasTechManager.getGeminiApiKey());
            env.put("OPENROUTER_API_KEY",   NasTechManager.getApiKey());
            env.put("NASTECH_HOME",         NasTechManager.getNasTechHome());
            env.put("HOME",                 System.getenv("HOME") != null ? System.getenv("HOME") : "");

            pb.redirectErrorStream(false);
            Process proc = pb.start();

            // Read stdout (JSON)
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line);
            }
            proc.waitFor();

            // Parse JSON: {"response": "...", "provider": "..."}
            String raw = out.toString().trim();
            if (!raw.isEmpty()) {
                JSONObject json = new JSONObject(raw);
                response = json.optString("response", null);
                provider = json.optString("provider", null);
            }
        } catch (Exception e) {
            response = getString(R.string.nastech_chat_all_failed) + "\n(" + e.getMessage() + ")";
        }

        // Handle missing keys / coordinator not found
        if (response == null || response.isEmpty()) {
            boolean noKeys = NasTechManager.getGroqApiKey().isEmpty()
                    && NasTechManager.getGeminiApiKey().isEmpty()
                    && NasTechManager.getApiKey().isEmpty();
            response = noKeys
                    ? getString(R.string.nastech_chat_no_key)
                    : getString(R.string.nastech_chat_all_failed);
        }

        final String finalResponse = response;
        final String finalProvider = provider;

        mMainThread.post(() -> {
            setInputEnabled(true);
            appendBubble("assistant", finalResponse);
            mHistory.add("Assistant: " + finalResponse);
            setStatus(finalProvider != null ? "via " + finalProvider : "");
        });
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void appendBubble(String role, String text) {
        if (mChatContainer == null) return;

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14f);
        bubble.setPadding(24, 16, 24, 16);
        bubble.setTextIsSelectable(true);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 6, 0, 6);

        switch (role) {
            case "user":
                bubble.setBackgroundResource(R.drawable.nastech_bubble_user);
                bubble.setTextColor(getColor(R.color.nastech_text));
                p.gravity = android.view.Gravity.END;
                p.setMarginStart(64);
                break;
            case "assistant":
                bubble.setBackgroundResource(R.drawable.nastech_bubble_ai);
                bubble.setTextColor(getColor(R.color.nastech_text));
                p.gravity = android.view.Gravity.START;
                p.setMarginEnd(64);
                break;
            default: // system / error
                bubble.setTextColor(getColor(R.color.nastech_text_dim));
                bubble.setTextSize(12f);
                p.gravity = android.view.Gravity.CENTER;
                break;
        }

        bubble.setLayoutParams(p);
        mChatContainer.addView(bubble);
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void setInputEnabled(boolean on) {
        if (mInput   != null) mInput.setEnabled(on);
        if (mSendBtn != null) mSendBtn.setEnabled(on);
    }

    private void setStatus(String text) {
        if (mStatus == null) return;
        mStatus.setText(text);
        mStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }
}
