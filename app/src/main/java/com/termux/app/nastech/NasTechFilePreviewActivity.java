package com.termux.app.nastech;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import java.io.File;

/**
 * NasTech AI Terminal — File Preview
 * Renders Markdown and PDF files inside a WebView.
 */
public class NasTechFilePreviewActivity extends AppCompatActivity {

    private WebView mWebView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nastech_file_preview);

        mWebView = findViewById(R.id.nastech_preview_webview);
        TextView titleView = findViewById(R.id.nastech_preview_title);
        ImageButton closeBtn = findViewById(R.id.nastech_preview_close);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        mWebView.setWebViewClient(new WebViewClient());

        String filePath = getIntent().getStringExtra("file_path");
        if (filePath != null) {
            File file = new File(filePath);
            if (titleView != null) titleView.setText(file.getName());

            if (filePath.endsWith(".pdf")) {
                // Use Google Docs viewer for PDF
                String pdfUrl = "https://docs.google.com/viewer?url=" +
                    Uri.encode("file://" + filePath) + "&embedded=true";
                mWebView.loadUrl(pdfUrl);
            } else if (filePath.endsWith(".md") || filePath.endsWith(".txt")) {
                // Render markdown via marked.js from CDN, load file content
                loadMarkdownFile(filePath);
            } else {
                mWebView.loadUrl("file://" + filePath);
            }
        }

        if (closeBtn != null) closeBtn.setOnClickListener(v -> finish());
    }

    private void loadMarkdownFile(String filePath) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath));
            String md = new String(bytes).replace("`", "\\`").replace("$", "\\$");
            String html =
                "<!DOCTYPE html><html><head>" +
                "<meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<script src='https://cdn.jsdelivr.net/npm/marked/marked.min.js'></script>" +
                "<style>body{background:#0A0A0F;color:#E8E8F0;font-family:monospace;padding:16px;}" +
                "a{color:#00C8FF;}code{color:#00FF88;background:#1A1A2E;padding:2px 4px;border-radius:3px;}" +
                "pre{background:#1A1A2E;padding:12px;border-radius:6px;overflow-x:auto;}</style>" +
                "</head><body>" +
                "<div id='content'></div>" +
                "<script>document.getElementById('content').innerHTML=" +
                "marked.parse(`" + md + "`);</script>" +
                "</body></html>";
            mWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        } catch (Exception e) {
            mWebView.loadData("<pre style='color:#E8E8F0;background:#0A0A0F;padding:16px'>" +
                "Could not load file: " + filePath + "</pre>", "text/html", "utf-8");
        }
    }
}
