package net.kdt.pojavlaunch.pengu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.util.UUID;

/**
 * Arkadaslar / sohbet / sesli arama. PC launcher'daki arkadaslar sekmesinin mobil hali;
 * arayuz assets/pengu/arkadaslar.html'de, ayni friends-server'a baglaniyor.
 * Sayfa https kokenli (WebViewAssetLoader) acilir; mikrofon ancak guvenli kokende calisiyor.
 */
public class ArkadaslarActivity extends BaseActivity {
    private static final String SIR = "arkadas_sir";

    private WebView mWebView;
    private PermissionRequest mBekleyenIzin;

    private final ActivityResultLauncher<String> mMikrofonIzni = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), verildi -> {
                if (mBekleyenIzin == null) return;
                if (verildi) mBekleyenIzin.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                else mBekleyenIzin.deny();
                mBekleyenIzin = null;
            });

    @Override
    public boolean setFullscreen() {
        return false;
    }

    /** Arkadaslar sunucusu kullanici adini cihaza bagliyor; bu cihazin kalici sirri. */
    private static String sir(Context ctx) {
        SharedPreferences p = PenguAyarlar.prefs(ctx);
        String s = p.getString(SIR, null);
        if (s == null) {
            s = UUID.randomUUID().toString();
            p.edit().putString(SIR, s).apply();
        }
        return s;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mWebView = new WebView(this);
        mWebView.setBackgroundColor(0xFF0F1420);
        setContentView(mWebView);

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false); // zil sesi ve gelen ses
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);

        WebViewAssetLoader yukleyici = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return yukleyici.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Sayfa disina gitmeye calisan baglantilar acilmasin
                return !"appassets.androidplatform.net".equals(request.getUrl().getHost());
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                boolean sadeceSes = true;
                for (String r : request.getResources()) {
                    if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) { sadeceSes = false; break; }
                }
                if (!sadeceSes) { request.deny(); return; }
                if (ContextCompat.checkSelfPermission(ArkadaslarActivity.this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    mBekleyenIzin = request;
                    mMikrofonIzni.launch(Manifest.permission.RECORD_AUDIO);
                }
            }
        });
        mWebView.addJavascriptInterface(new Kopru(), "Pengu");

        MinecraftAccount hesap = PojavProfile.getCurrentProfileContent(this, null);
        String ad = hesap != null ? hesap.username : "";
        mWebView.loadUrl("https://appassets.androidplatform.net/assets/pengu/arkadaslar.html"
                + "?u=" + Uri.encode(ad) + "&s=" + Uri.encode(sir(this)));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                mWebView.evaluateJavascript("window.geriTusu && window.geriTusu()", sonuc -> {
                    if (!"true".equals(sonuc)) finish();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }

    private class Kopru {
        @JavascriptInterface
        public void titret() {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 300, 400}, -1));
            else v.vibrate(800);
        }
    }
}
