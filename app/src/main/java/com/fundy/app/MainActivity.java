package com.fundy.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQUEST_IMPORT = 2001;
    private static final int REQUEST_EXPORT = 2002;

    private WebView webView;
    private String pendingImportFormat = "json";

    private String pendingExportFilename = null;
    private String pendingExportMime = null;
    private String pendingExportBase64 = null;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        // Keep WebView rendering on the GPU. FUNDY is a local single-page app.
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void pickImportFile(final String format) {
            runOnUiThread(() -> openNativeImportPicker(format));
        }

        @JavascriptInterface
        public void saveExportFile(final String filename, final String mimeType, final String base64Data) {
            runOnUiThread(() -> openNativeExportPicker(filename, mimeType, base64Data));
        }
    }

    private void openNativeImportPicker(String format) {
        pendingImportFormat = format == null ? "json" : format;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if ("csv".equals(pendingImportFormat)) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "text/csv", "text/comma-separated-values", "text/plain",
                    "application/csv", "application/octet-stream"
            });
        } else if ("excel".equals(pendingImportFormat)) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel", "application/octet-stream"
            });
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/json", "text/json", "text/plain",
                    "application/octet-stream"
            });
        }

        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception e) {
            Toast.makeText(this, "Pemilih file Android tidak dapat dibuka.", Toast.LENGTH_LONG).show();
        }
    }

    private void openNativeExportPicker(String filename, String mimeType, String base64Data) {
        pendingExportFilename = (filename == null || filename.trim().isEmpty())
                ? "FUNDY-backup"
                : filename;
        pendingExportMime = (mimeType == null || mimeType.trim().isEmpty())
                ? "application/octet-stream"
                : mimeType;
        pendingExportBase64 = base64Data;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(pendingExportMime);
        intent.putExtra(Intent.EXTRA_TITLE, pendingExportFilename);

        try {
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Exception e) {
            clearPendingExport();
            notifyExportError("Tidak bisa membuka lokasi penyimpanan.");
        }
    }

    private void clearPendingExport() {
        pendingExportFilename = null;
        pendingExportMime = null;
        pendingExportBase64 = null;
    }

    private void notifyExportSaved(String filename) {
        final String js = "window.onAndroidExportSaved(" + quoteJs(filename) + ");";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    private void notifyExportCancelled() {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.onAndroidExportCancelled();", null);
        });
    }

    private void notifyExportError(String message) {
        final String js = "window.onAndroidExportError(" + quoteJs(message) + ");";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXPORT) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                clearPendingExport();
                notifyExportCancelled();
                return;
            }

            final Uri uri = data.getData();
            final String filename = pendingExportFilename == null ? "FUNDY-backup" : pendingExportFilename;
            final String base64 = pendingExportBase64;

            if (base64 == null) {
                clearPendingExport();
                notifyExportError("Data backup kosong.");
                return;
            }

            new Thread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    OutputStream output = getContentResolver().openOutputStream(uri, "w");
                    if (output == null) throw new Exception("File tidak dapat dibuat");

                    try {
                        output.write(bytes);
                        output.flush();
                    } finally {
                        output.close();
                    }

                    clearPendingExport();
                    notifyExportSaved(filename);
                } catch (Exception e) {
                    clearPendingExport();
                    notifyExportError(e.getMessage() == null ? "Gagal menulis file backup." : e.getMessage());
                }
            }, "fundy-export").start();

            return;
        }

        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null) return;

        final Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    data.getFlags() &
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            );
        } catch (Exception ignored) {}

        final String importFormat = pendingImportFormat;
        new Thread(() -> {
            try {
                byte[] bytes = readAllBytes(uri);
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                String fileName = getFileName(uri);

                final String js = "window.onAndroidImport(" +
                        quoteJs(importFormat) + "," +
                        quoteJs(base64) + "," +
                        quoteJs(fileName) + ");";

                runOnUiThread(() -> {
                    if (webView != null) webView.evaluateJavascript(js, null);
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Gagal membaca file: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }, "fundy-import").start();
    }

    private byte[] readAllBytes(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new Exception("File tidak dapat dibuka");

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private String getFileName(Uri uri) {
        String name = "backup";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name == null ? "backup" : name;
    }

    private String quoteJs(String value) {
        if (value == null) return "\"\"";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
