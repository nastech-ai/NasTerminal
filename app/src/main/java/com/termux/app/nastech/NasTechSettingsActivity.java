package com.termux.app.nastech;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

/**
 * NasTech AI Terminal — Settings Screen
 *
 * • API keys for Groq / Gemini / OpenRouter (Groq→Gemini→OpenRouter fallback)
 * • Biometric lock toggle
 * • AI Daemon toggle (starts/stops NasTechDaemonService)
 * • Re-run Installer button (shows NasTechInstallProgressDialog)
 */
public class NasTechSettingsActivity extends AppCompatActivity {

    private EditText mGroqInput;
    private EditText mGeminiInput;
    private EditText mOrInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nastech_settings);

        mGroqInput   = findViewById(R.id.nastech_settings_groq_key);
        mGeminiInput = findViewById(R.id.nastech_settings_gemini_key);
        mOrInput     = findViewById(R.id.nastech_settings_api_key);

        Switch  bioSwitch    = findViewById(R.id.nastech_settings_biometric);
        Switch  daemonSwitch = findViewById(R.id.nastech_settings_daemon);
        Button  saveBtn      = findViewById(R.id.nastech_settings_save);
        Button  reinstallBtn = findViewById(R.id.nastech_settings_reinstall);
        Button  backBtn      = findViewById(R.id.nastech_settings_back);

        // Load current (masked) values
        loadMasked(mGroqInput,   NasTechManager.getGroqApiKey());
        loadMasked(mGeminiInput, NasTechManager.getGeminiApiKey());
        loadMasked(mOrInput,     NasTechManager.getApiKey());

        if (bioSwitch != null) {
            bioSwitch.setChecked(NasTechManager.isBiometricLockEnabled());
            bioSwitch.setOnCheckedChangeListener((btn, checked) ->
                    NasTechManager.setBiometricLock(checked));
        }

        if (daemonSwitch != null) {
            daemonSwitch.setChecked(NasTechManager.isDaemonEnabled());
            daemonSwitch.setOnCheckedChangeListener((btn, checked) -> {
                NasTechManager.setDaemonEnabled(checked);
                Intent svc = new Intent(this, NasTechDaemonService.class);
                if (checked) {
                    svc.setAction(NasTechDaemonService.ACTION_START);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(svc);
                    } else {
                        startService(svc);
                    }
                    Toast.makeText(this, "NasTech daemon started", Toast.LENGTH_SHORT).show();
                } else {
                    svc.setAction(NasTechDaemonService.ACTION_STOP);
                    startService(svc);
                    Toast.makeText(this, "NasTech daemon stopped", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                saveKey(mGroqInput,   NasTechManager::setGroqApiKey);
                saveKey(mGeminiInput, NasTechManager::setGeminiApiKey);
                saveKey(mOrInput,     NasTechManager::setApiKey);
                NasTechManager.init(this);
                Toast.makeText(this, "Settings saved — restart terminal to apply keys",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        }

        if (reinstallBtn != null) {
            reinstallBtn.setOnClickListener(v -> {
                // Re-arm the install overlay flag then show the progress dialog
                NasTechManager.setInstallOverlayPending();
                NasTechInstallProgressDialog.show(this);
            });
        }

        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadMasked(EditText field, String value) {
        if (field == null) return;
        if (value != null && !value.isEmpty()) {
            field.setText("••••" + value.substring(Math.max(0, value.length() - 4)));
        }
    }

    /** Only saves if the field has been edited (not the masked placeholder). */
    private void saveKey(EditText field, KeySetter setter) {
        if (field == null) return;
        String val = field.getText().toString().trim();
        if (!val.isEmpty() && !val.startsWith("••")) {
            setter.set(val);
        }
    }

    /** API-21 compatible functional interface (avoids java.util.function.Consumer). */
    interface KeySetter { void set(String value); }
}
