package com.termux.app.nastech;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

/**
 * NasTech AI Terminal — Settings Screen
 * Stores API keys for all three providers (Groq, Gemini, OpenRouter).
 * On save, re-runs NasTechManager.init() to regenerate nastech_init.sh
 * with the updated keys so the terminal picks them up immediately.
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
        Switch  bioSwitch = findViewById(R.id.nastech_settings_biometric);
        Button  saveBtn   = findViewById(R.id.nastech_settings_save);
        Button  backBtn   = findViewById(R.id.nastech_settings_back);

        // Load current (masked) values
        loadMasked(mGroqInput,   NasTechManager.getGroqApiKey());
        loadMasked(mGeminiInput, NasTechManager.getGeminiApiKey());
        loadMasked(mOrInput,     NasTechManager.getApiKey());

        if (bioSwitch != null) {
            bioSwitch.setChecked(NasTechManager.isBiometricLockEnabled());
            bioSwitch.setOnCheckedChangeListener((btn, checked) ->
                    NasTechManager.setBiometricLock(checked));
        }

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                saveKey(mGroqInput,   NasTechManager::setGroqApiKey);
                saveKey(mGeminiInput, NasTechManager::setGeminiApiKey);
                saveKey(mOrInput,     NasTechManager::setApiKey);

                // Re-run init so nastech_init.sh is rewritten with new keys
                NasTechManager.init(this);

                Toast.makeText(this, "Settings saved — terminal restart required",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        }

        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadMasked(EditText field, String value) {
        if (field == null) return;
        if (!value.isEmpty()) {
            field.setText("••••" + value.substring(Math.max(0, value.length() - 4)));
        }
    }

    private void saveKey(EditText field, java.util.function.Consumer<String> setter) {
        if (field == null) return;
        String val = field.getText().toString().trim();
        if (!val.isEmpty() && !val.startsWith("••")) {
            setter.accept(val);
        }
    }
}
