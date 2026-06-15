package com.termux.app.nastech;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

/**
 * NasTech AI Terminal — Built-in Browser
 * Full WebView with address bar, back/forward, progress indicator.
 */
public class NasTechBrowserActivity extends AppCompatActivity {

    private WebView mWebView;
    private EditText mAddressBar;
    private ProgressBar mProgress;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nastech_browser);

        mWebView = findViewById(R.id.nastech_browser_webview);
        mAddressBar = findViewById(R.id.nastech_browser_address);
        mProgress = findViewById(R.id.nastech_browser_progress);
        ImageButton backBtn = findViewById(R.id.nastech_browser_back);
        ImageButton forwardBtn = findViewById(R.id.nastech_browser_forward);
        ImageButton refreshBtn = findViewById(R.id.nastech_browser_refresh);
        ImageButton closeBtn = findViewById(R.id.nastech_browser_close);

        // WebView settings
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (mAddressBar != null) mAddressBar.setText(url);
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (mProgress != null) {
                    mProgress.setProgress(newProgress);
                    mProgress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                }
            }
        });

        // Address bar — navigate on Enter
        if (mAddressBar != null) {
            mAddressBar.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    navigateTo(mAddressBar.getText().toString().trim());
                    return true;
                }
                return false;
            });
        }

        if (backBtn != null) backBtn.setOnClickListener(v -> { if (mWebView.canGoBack()) mWebView.goBack(); });
        if (forwardBtn != null) forwardBtn.setOnClickListener(v -> { if (mWebView.canGoForward()) mWebView.goForward(); });
        if (refreshBtn != null) refreshBtn.setOnClickListener(v -> mWebView.reload());
        if (closeBtn != null) closeBtn.setOnClickListener(v -> finish());

        // Load start page
        String url = getIntent().getStringExtra("url");
        navigateTo(url != null ? url : "https://duckduckgo.com");
    }

    private void navigateTo(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                url = "https://duckduckgo.com/?q=" + url.replace(" ", "+");
            }
        }
        mWebView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
