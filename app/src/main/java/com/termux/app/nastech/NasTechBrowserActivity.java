package com.termux.app.nastech;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.termux.R;

/**
 * NasTech AI Terminal — Built-in Browser
 *
 * Full Chromium-WebView browser with:
 *   • DuckDuckGo as default search engine
 *   • Full JS, DOM storage, cookies, geolocation, WebRTC
 *   • File downloads via DownloadManager (shows in status bar)
 *   • File upload chooser (input[type=file] works)
 *   • Fullscreen video support (YouTube, etc.)
 *   • Chrome-compatible user-agent (mobile Chrome)
 *   • AI-assisted queries (natural language → NasTech AI Chat)
 *   • Share current URL, Home button, nav state indicators
 *   • Mixed-content compatibility mode
 */
public class NasTechBrowserActivity extends AppCompatActivity {

    private static final String HOME_URL   = "https://duckduckgo.com";
    private static final String SEARCH_URL = "https://duckduckgo.com/?q=";
    // Modern Chrome-compatible UA — required for Google, GitHub, etc.
    private static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; NasTech-AI) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36";

    private WebView      mWebView;
    private EditText     mAddressBar;
    private ProgressBar  mProgress;
    private ImageButton  mBackBtn, mForwardBtn;
    private FrameLayout  mFullscreenContainer;
    private View         mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    // File chooser for input[type=file]
    private ValueCallback<Uri[]> mFileChooserCallback;
    private final ActivityResultLauncher<Intent> mFilePicker =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (mFileChooserCallback == null) return;
            Uri[] uris = null;
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri data = result.getData().getData();
                if (data != null) uris = new Uri[]{data};
            }
            mFileChooserCallback.onReceiveValue(uris);
            mFileChooserCallback = null;
        });

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nastech_browser);

        mWebView             = findViewById(R.id.nastech_browser_webview);
        mAddressBar          = findViewById(R.id.nastech_browser_address);
        mProgress            = findViewById(R.id.nastech_browser_progress);
        mBackBtn             = findViewById(R.id.nastech_browser_back);
        mForwardBtn          = findViewById(R.id.nastech_browser_forward);
        mFullscreenContainer = findViewById(R.id.nastech_browser_fullscreen);

        ImageButton refreshBtn = findViewById(R.id.nastech_browser_refresh);
        ImageButton homeBtn    = findViewById(R.id.nastech_browser_home);
        ImageButton shareBtn   = findViewById(R.id.nastech_browser_share);
        ImageButton aiBtn      = findViewById(R.id.nastech_browser_ai);
        ImageButton closeBtn   = findViewById(R.id.nastech_browser_close);

        setupWebView();
        setupAddressBar();
        updateNavButtons();

        if (mBackBtn    != null) mBackBtn.setOnClickListener(v -> { if (mWebView.canGoBack()) mWebView.goBack(); });
        if (mForwardBtn != null) mForwardBtn.setOnClickListener(v -> { if (mWebView.canGoForward()) mWebView.goForward(); });
        if (refreshBtn  != null) refreshBtn.setOnClickListener(v -> {
            if (mWebView.getProgress() < 100) mWebView.stopLoading();
            else mWebView.reload();
        });
        if (homeBtn   != null) homeBtn.setOnClickListener(v -> mWebView.loadUrl(HOME_URL));
        if (closeBtn  != null) closeBtn.setOnClickListener(v -> finish());

        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                String url = mWebView.getUrl();
                if (url != null) {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, url);
                    startActivity(Intent.createChooser(share, "Share URL"));
                }
            });
        }

        if (aiBtn != null) {
            aiBtn.setOnClickListener(v -> {
                String q = mAddressBar != null ? mAddressBar.getText().toString().trim() : "";
                if (isNaturalLanguage(q)) {
                    openAiChat(q);
                } else {
                    // Summarise the current page via AI
                    openAiChat("Summarise this page: " + mWebView.getTitle()
                            + "\nURL: " + mWebView.getUrl());
                }
            });
        }

        // Load URL from intent or open home
        String url = getIntent().getStringExtra("url");
        navigateTo(url != null && !url.isEmpty() ? url : HOME_URL);
    }

    // ── WebView configuration ─────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = mWebView.getSettings();

        // Scripting & storage
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setSaveFormData(true);

        // Rendering
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setTextZoom(100);

        // Media — autoplay allowed (needed for many sites)
        ws.setMediaPlaybackRequiresUserGesture(false);

        // Mixed content (HTTP resources on HTTPS pages — compat mode)
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Chrome-compatible UA
        ws.setUserAgentString(USER_AGENT);

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);

        // ── WebViewClient ─────────────────────────────────────────────────────
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                // Let WebView handle http/https; hand off other schemes to system
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (mAddressBar != null) mAddressBar.setText(url);
                updateNavButtons();
                CookieManager.getInstance().flush();
            }
        });

        // ── WebChromeClient ───────────────────────────────────────────────────
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                if (mProgress != null) {
                    mProgress.setProgress(p);
                    mProgress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
                }
                updateNavButtons();
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                // Reflect page title in address bar hint
                if (mAddressBar != null && title != null) mAddressBar.setHint(title);
            }

            // Geolocation
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback cb) {
                if (ContextCompat.checkSelfPermission(NasTechBrowserActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    ActivityCompat.requestPermissions(NasTechBrowserActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
                    cb.invoke(origin, false, false);
                }
            }

            // Camera / mic (WebRTC)
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources()); // user can deny in OS settings
            }

            // File chooser — input[type=file]
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                    FileChooserParams params) {
                if (mFileChooserCallback != null) {
                    mFileChooserCallback.onReceiveValue(null);
                }
                mFileChooserCallback = cb;
                Intent intent = params.createIntent();
                try {
                    mFilePicker.launch(intent);
                } catch (ActivityNotFoundException e) {
                    mFileChooserCallback = null;
                    return false;
                }
                return true;
            }

            // Fullscreen video (YouTube, etc.)
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mCustomView != null) { onHideCustomView(); return; }
                mCustomView = view;
                mCustomViewCallback = callback;
                if (mFullscreenContainer != null) {
                    mFullscreenContainer.addView(view);
                    mFullscreenContainer.setVisibility(View.VISIBLE);
                }
                mWebView.setVisibility(View.GONE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                                   | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onHideCustomView() {
                if (mCustomView == null) return;
                mWebView.setVisibility(View.VISIBLE);
                if (mFullscreenContainer != null) {
                    mFullscreenContainer.removeAllViews();
                    mFullscreenContainer.setVisibility(View.GONE);
                }
                mCustomViewCallback.onCustomViewHidden();
                mCustomView = null;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                                     | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        // ── Download listener ─────────────────────────────────────────────────
        mWebView.setDownloadListener((url, ua, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                req.addRequestHeader("User-Agent", USER_AGENT);
                req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                req.setTitle(fileName);
                req.setDescription("NasTech Browser download");
                req.allowScanningByMediaScanner();
                req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) dm.enqueue(req);
                Toast.makeText(this, "⬇ Downloading: " + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Address bar ───────────────────────────────────────────────────────────

    private void setupAddressBar() {
        if (mAddressBar == null) return;
        mAddressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateTo(mAddressBar.getText().toString().trim());
                return true;
            }
            return false;
        });
        mAddressBar.setOnFocusChangeListener((v, focused) -> {
            if (focused) mAddressBar.selectAll();
        });
    }

    // ── Smart navigation ──────────────────────────────────────────────────────

    private void navigateTo(String input) {
        if (input == null || input.isEmpty()) return;

        if (input.startsWith("http://") || input.startsWith("https://")
                || input.startsWith("file://") || input.startsWith("data:")) {
            mWebView.loadUrl(input);
        } else if (!input.contains(" ") && input.contains(".")
                && !input.endsWith(".") && input.length() > 3) {
            mWebView.loadUrl("https://" + input);
        } else if (isNaturalLanguage(input)) {
            // Long natural-language query → NasTech AI Chat
            openAiChat(input);
        } else {
            // Everything else → DuckDuckGo
            mWebView.loadUrl(SEARCH_URL + Uri.encode(input));
        }
    }

    /** True for multi-word phrases / questions that should go to AI. */
    private boolean isNaturalLanguage(String s) {
        if (s == null || s.isEmpty()) return false;
        String l = s.toLowerCase();
        return s.split("\\s+").length > 4
            || l.startsWith("how ") || l.startsWith("why ")
            || l.startsWith("what ") || l.startsWith("explain ")
            || l.startsWith("write ") || l.startsWith("create ")
            || l.startsWith("show me") || l.endsWith("?");
    }

    private void openAiChat(String prompt) {
        Intent i = new Intent(this, NasTechAIChatActivity.class);
        i.putExtra("prompt", prompt);
        startActivity(i);
    }

    private void updateNavButtons() {
        if (mWebView == null) return;
        if (mBackBtn    != null) mBackBtn.setAlpha(mWebView.canGoBack()    ? 1f : 0.35f);
        if (mForwardBtn != null) mForwardBtn.setAlpha(mWebView.canGoForward() ? 1f : 0.35f);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (mCustomView != null && mCustomViewCallback != null) {
            // Exit fullscreen video
            mWebView.setVisibility(View.VISIBLE);
            if (mFullscreenContainer != null) {
                mFullscreenContainer.removeAllViews();
                mFullscreenContainer.setVisibility(View.GONE);
            }
            mCustomViewCallback.onCustomViewHidden();
            mCustomView = null;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                                 | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) mWebView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) mWebView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.setWebChromeClient(null);
            mWebView.setWebViewClient(null);
            mWebView.destroy();
        }
        super.onDestroy();
    }
}
