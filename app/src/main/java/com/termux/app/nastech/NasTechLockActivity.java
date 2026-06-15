package com.termux.app.nastech;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.termux.R;

import java.util.concurrent.Executor;

/**
 * NasTech AI Terminal — Biometric Lock Screen
 * 3D wave animation background, fingerprint/face or passphrase unlock.
 */
public class NasTechLockActivity extends AppCompatActivity {

    private BiometricPrompt mBiometricPrompt;
    private EditText mPassphraseInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nastech_lock);

        mPassphraseInput = findViewById(R.id.nastech_lock_passphrase);
        Button bioBtn = findViewById(R.id.nastech_lock_biometric_btn);
        Button passBtn = findViewById(R.id.nastech_lock_passphrase_btn);

        // Set up biometric prompt
        Executor executor = ContextCompat.getMainExecutor(this);
        mBiometricPrompt = new BiometricPrompt(this, executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    unlockSuccess();
                }
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(NasTechLockActivity.this,
                            getString(R.string.nastech_lock_error), Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(NasTechLockActivity.this,
                        getString(R.string.nastech_lock_error), Toast.LENGTH_SHORT).show();
                }
            });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.nastech_lock_title))
            .setSubtitle(getString(R.string.nastech_lock_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build();

        if (bioBtn != null) {
            bioBtn.setOnClickListener(v -> mBiometricPrompt.authenticate(promptInfo));
        }

        if (passBtn != null) {
            passBtn.setOnClickListener(v -> {
                String passphrase = mPassphraseInput != null
                    ? mPassphraseInput.getText().toString() : "";
                String stored = NasTechManager.getPrefs()
                    .getString("passphrase", "nastech");
                if (passphrase.equals(stored)) {
                    unlockSuccess();
                } else {
                    Toast.makeText(this, getString(R.string.nastech_lock_error),
                        Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Auto-trigger biometrics on launch if available
        BiometricManager bm = BiometricManager.from(this);
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            == BiometricManager.BIOMETRIC_SUCCESS) {
            mBiometricPrompt.authenticate(promptInfo);
        }
    }

    private void unlockSuccess() {
        finish();
    }

    @Override
    public void onBackPressed() {
        // Prevent back press from bypassing lock
    }
}
