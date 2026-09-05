package com.ledger.daftar;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_STORAGE = 100;
    private static final int REQ_FILE_CHOOSER = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Ledger);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = findViewById(R.id.webview);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // Performance optimizations
        s.setRenderPriority(WebSettings.RenderPriority.HIGH);
        s.setEnableSmoothTransition(true);
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setDrawingCacheEnabled(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setBackgroundColor(0xFF16222B);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("appassets.androidplatform.net".equals(request.getUrl().getHost())) {
                    return assetLoader.shouldInterceptRequest(request.getUrl());
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                boolean isAppAssets = "appassets.androidplatform.net".equals(host);
                if (!isAppAssets) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "لا يوجد تطبيق مناسب لفتح هذا الرابط", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                              FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/*"});
                try {
                    startActivityForResult(Intent.createChooser(intent, "اختر ملف النسخة الاحتياطية"), REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        String js = "(function(){"
                + "var mo=document.getElementById('modal-root');"
                + "if(mo && mo.innerHTML.trim()!==''){ if(typeof closeModal==='function'){closeModal();} return 'handled'; }"
                + "if(typeof view!=='undefined' && view.route && view.route!=='home'){ if(typeof goBack==='function'){goBack();} return 'handled'; }"
                + "return 'exit';"
                + "})();";
        webView.evaluateJavascript(js, value -> {
            if (value != null && value.replace("\"", "").equals("exit")) {
                runOnUiThread(() -> MainActivity.super.onBackPressed());
            }
        });
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void saveFile(String base64Data, String filename, String mime) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    Uri uri = writeToDownloads(bytes, filename, mime);
                    if (uri != null) {
                        Toast.makeText(MainActivity.this, "تم الحفظ بنجاح", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "تعذر حفظ الملف", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "خطأ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void shareFile(String base64Data, String filename, String mime) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists()) dir.mkdirs();
                    File out = new File(dir, filename);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(bytes);
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this,
                            "com.ledger.daftar.fileprovider", out);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType(mime);
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "مشاركة الملف"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر مشاركة الملف", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void printHTML(String html) {
            runOnUiThread(() -> {
                try {
                    WebView printWebView = new WebView(MainActivity.this);
                    WebSettings ps = printWebView.getSettings();
                    ps.setJavaScriptEnabled(false); // No JS needed for static reports
                    ps.setDomStorageEnabled(false);
                    printWebView.setBackgroundColor(0xFFFFFFFF);
                    printWebView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            PrintManager printManager =
                                    (PrintManager) getSystemService(Context.PRINT_SERVICE);
                            if (printManager != null) {
                                String jobName = "دفتر الحساب - طباعة";
                                android.print.PrintDocumentAdapter adapter =
                                        view.createPrintDocumentAdapter(jobName);
                                printManager.print(jobName, adapter,
                                        new PrintAttributes.Builder().build());
                            }
                        }
                    });
                    printWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر فتح الطباعة", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private Uri writeToDownloads(byte[] bytes, String filename, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri item = getContentResolver().insert(collection, values);
            if (item == null) return null;
            try (OutputStream os = getContentResolver().openOutputStream(item)) {
                if (os == null) return null;
                os.write(bytes);
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(item, values, null, null);
            return item;
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(bytes);
            }
            return Uri.fromFile(out);
        }
    }
}
