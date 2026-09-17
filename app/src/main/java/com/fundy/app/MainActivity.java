package com.fundy.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        // FUNDY membutuhkan JavaScript
        settings.setJavaScriptEnabled(true);

        // localStorage / DOM storage
        settings.setDomStorageEnabled(true);

        // Database WebView
        settings.setDatabaseEnabled(true);

        // Izinkan akses file
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Responsive
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Zoom browser dimatikan
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {

                // Batalkan callback lama jika masih ada
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent;

                try {

                    intent = fileChooserParams.createIntent();

                    // Hanya pilih satu file
                    intent.putExtra(
                            Intent.EXTRA_ALLOW_MULTIPLE,
                            false
                    );

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (ActivityNotFoundException e) {

                    MainActivity.this.filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "Tidak ada aplikasi pemilih file di perangkat.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return false;
                }
            }
        });

        // Buka FUNDY
        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }


    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            return;
        }

        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {

            if (data != null) {

                String dataString = data.getDataString();

                if (dataString != null) {

                    results = new Uri[]{
                            Uri.parse(dataString)
                    };

                } else if (
                        data.getClipData() != null
                ) {

                    int count =
                            data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {

                        results[i] =
                                data
                                        .getClipData()
                                        .getItemAt(i)
                                        .getUri();
                    }
                }
            }
        }

        filePathCallback.onReceiveValue(results);

        filePathCallback = null;
    }


    @Override
    public void onBackPressed() {

        if (
                webView != null &&
                webView.canGoBack()
        ) {

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
