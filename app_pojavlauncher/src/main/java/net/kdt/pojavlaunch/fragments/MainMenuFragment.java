package net.kdt.pojavlaunch.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.webkit.WebViewAssetLoader;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcAccountSpinner;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.pengu.ArkadaslarActivity;
import net.kdt.pojavlaunch.pengu.PenguAyarlar;
import net.kdt.pojavlaunch.pengu.PenguConfig;
import net.kdt.pojavlaunch.pengu.PenguHazirlik;
import net.kdt.pojavlaunch.pengu.PenguSkin;
import net.kdt.pojavlaunch.pengu.PenguSunucu;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Ana ekran: PC launcher'la ayni tasarimdaki web arayuzu (assets/pengu/ana.html).
 * Sinif adi Amethyst'in "ana menuye don" akislari bozulmasin diye korundu; Amethyst'in kendi
 * arayuzu artik sadece arka planda (hesaplar, indirme, oyunu baslatma) calisiyor.
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private static final Pattern KULLANICI_ADI = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final String[] ILERLEME_KAYITLARI = {
            ProgressLayout.INSTALL_MODPACK, ProgressLayout.DOWNLOAD_MINECRAFT, ProgressLayout.UNPACK_RUNTIME,
            ProgressLayout.AUTHENTICATE_MICROSOFT, ProgressLayout.DOWNLOAD_VERSION_LIST
    };

    private WebView mWebView;
    private final List<ProgressListener> mIlerlemeDinleyicileri = new ArrayList<>();
    private boolean mSkinInce;

    private final ActivityResultLauncher<String> mSkinSec = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::skinSecildi);
    private final ActivityResultLauncher<String> mModSec = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::modSecildi);
    /** Sesli sohbet (Simple Voice Chat) icin; cevap ne olursa olsun oyun baslar */
    private final ActivityResultLauncher<String> mMikrofonIzni = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), verildi -> oynaDevam());

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mWebView = view.findViewById(R.id.pengu_web);
        requireActivity().getWindow().setStatusBarColor(0xFF0B0F16);
        requireActivity().getWindow().setNavigationBarColor(0xFF0B0F16);
        mWebView.setBackgroundColor(0xFF0B0F16);
        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);

        WebViewAssetLoader yukleyici = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(requireContext()))
                .build();
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return yukleyici.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                Tools.openURL(requireActivity(), u.toString()); // dis baglantilar tarayicida
                return true;
            }
        });
        mWebView.addJavascriptInterface(new Kopru(), "PenguApp");
        mWebView.loadUrl("https://appassets.androidplatform.net/assets/pengu/ana.html");

        mcAccountSpinner.sHesapDinleyici = () -> olay("hesap", new JSONObject());
        for (String kayit : ILERLEME_KAYITLARI) ilerlemeDinle(kayit);

    }

    /** LauncherActivity geri tusunda cagirir: once web arayuzundeki acik pencereler kapanir. */
    public void geriBas(Runnable yoksa) {
        if (mWebView == null) { yoksa.run(); return; }
        mWebView.evaluateJavascript("window.geriTusu && window.geriTusu()", sonuc -> {
            if (!"true".equals(sonuc)) yoksa.run();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        olay("yenile", new JSONObject());
    }

    @Override
    public void onDestroyView() {
        for (int i = 0; i < ILERLEME_KAYITLARI.length && i < mIlerlemeDinleyicileri.size(); i++) {
            ProgressKeeper.removeListener(ILERLEME_KAYITLARI[i], mIlerlemeDinleyicileri.get(i));
        }
        mIlerlemeDinleyicileri.clear();
        mcAccountSpinner.sHesapDinleyici = null;
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroyView();
    }

    /* ------------------------------------------------------------ native -> web */

    private void olay(String ad, JSONObject veri) {
        Tools.runOnUiThread(() -> {
            if (mWebView == null) return;
            mWebView.evaluateJavascript("window.pengu && window.pengu.olay(" + JSONObject.quote(ad) + "," + veri + ")", null);
        });
    }

    private void cevap(String id, JSONObject veri) {
        Tools.runOnUiThread(() -> {
            if (mWebView == null) return;
            mWebView.evaluateJavascript("window.pengu && window.pengu.cevap(" + JSONObject.quote(id) + "," + veri + ")", null);
        });
    }

    private static JSONObject json(Object... anahtarDeger) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < anahtarDeger.length; i += 2) o.put((String) anahtarDeger[i], anahtarDeger[i + 1]);
        } catch (Exception ignored) {}
        return o;
    }

    private void ilerlemeDinle(String kayit) {
        ProgressListener l = new ProgressListener() {
            @Override public void onProgressStarted() {
                olay("ilerleme", json("kayit", kayit, "yuzde", 0, "metin", ""));
            }
            @Override public void onProgressUpdated(int yuzde, int resid, Object... va) {
                String metin = "";
                Context ctx = getContext();
                if (ctx != null && resid > 0) {
                    try { metin = ctx.getString(resid, va); } catch (Exception e) { metin = ctx.getString(resid); }
                }
                olay("ilerleme", json("kayit", kayit, "yuzde", yuzde, "metin", metin));
            }
            @Override public void onProgressEnded() {
                olay("ilerlemeBitti", json("kayit", kayit));
            }
        };
        mIlerlemeDinleyicileri.add(l);
        ProgressKeeper.addListener(kayit, l);
    }

    private @Nullable mcAccountSpinner secici() {
        return getActivity() instanceof LauncherActivity ? ((LauncherActivity) getActivity()).getHesapSecici() : null;
    }

    private void oynaDevam() {
        Context ctx = getContext();
        if (ctx == null) return;
        PenguHazirlik.baslat(ctx, new PenguHazirlik.Sonuc() {
            @Override public void tamam() {
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
            }
            @Override public void hata(Throwable t) {
                olay("oynaHata", json("mesaj", "Oyun hazırlanamadı: " + t.getMessage()));
            }
        });
    }

    /** Oyuncunun sectigi .jar'i mods klasorune kopyalar. */
    private void modSecildi(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = requireContext();
        String ad = null;
        try (android.database.Cursor c = ctx.getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) ad = c.getString(0);
        } catch (Exception ignored) {}
        if (ad == null || !ad.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            olay("modEklendi", json("hata", "Sadece .jar mod dosyası eklenebilir."));
            return;
        }
        String dosyaAdi = new File(ad).getName();
        PojavApplication.sExecutorService.execute(() -> {
            File mods = new File(PenguHazirlik.oyunKlasoru(), "mods");
            //noinspection ResultOfMethodCallIgnored
            mods.mkdirs();
            try (InputStream is = ctx.getContentResolver().openInputStream(uri);
                 java.io.OutputStream os = new java.io.FileOutputStream(new File(mods, dosyaAdi))) {
                if (is == null) throw new java.io.IOException("dosya açılamadı");
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
                olay("modEklendi", json("ok", true, "ad", dosyaAdi));
            } catch (Exception e) {
                olay("modEklendi", json("hata", "Kopyalanamadı: " + e.getMessage()));
            }
        });
    }

    /* ------------------------------------------------------------ skin */

    private void skinSecildi(@Nullable Uri uri) {
        if (uri == null) { olay("skin", json("iptal", true)); return; }
        Context ctx = requireContext();
        byte[] png;
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1 && bos.size() <= 1024 * 1024) bos.write(buf, 0, n);
            png = bos.toByteArray();
        } catch (Exception e) {
            olay("skin", json("hata", "Dosya okunamadı: " + e.getMessage()));
            return;
        }
        String hata = PenguSkin.dogrula(png);
        if (hata != null) { olay("skin", json("hata", hata)); return; }
        MinecraftAccount h = PojavProfile.getCurrentProfileContent(ctx, null);
        if (h == null) { olay("skin", json("hata", "Önce giriş yapmalısın.")); return; }
        olay("skin", json("yukleniyor", true, "png", android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)));
        String oyuncu = h.username;
        boolean ince = mSkinInce;
        PojavApplication.sExecutorService.execute(() -> {
            String sonuc = PenguSkin.yukle(oyuncu, png, ince);
            olay("skin", sonuc == null ? json("tamam", true) : json("hata", sonuc));
        });
    }

    /* ------------------------------------------------------------ web -> native */

    /** window.PenguApp: ana.html'deki arayuzun cagirdigi metotlar. Hepsi UI thread disinda gelir. */
    private class Kopru {

        @JavascriptInterface
        public String durum() {
            Context ctx = getContext();
            if (ctx == null) return "{}";
            try {
                JSONObject o = new JSONObject();
                o.put("surum", BuildConfig.VERSION_NAME);
                o.put("sunucu", PenguConfig.SUNUCU_IP);
                JSONArray hesaplar = new JSONArray();
                for (MinecraftAccount a : PojavProfile.getAllProfiles()) {
                    hesaplar.put(json("ad", a.username, "microsoft", !a.isLocal()));
                }
                o.put("hesaplar", hesaplar);
                MinecraftAccount secili = PojavProfile.getCurrentProfileContent(ctx, null);
                o.put("secili", secili != null ? secili.username : JSONObject.NULL);
                o.put("microsoft", secili != null && !secili.isLocal());
                o.put("authme", PenguAyarlar.authmeVar(ctx));
                SharedPreferences p = PenguAyarlar.prefs(ctx);
                o.put("fps", p.getBoolean(PenguAyarlar.FPS_PAKETI, true));
                o.put("sodium", p.getBoolean(PenguAyarlar.SODIUM, true));
                o.put("bedrock", p.getBoolean(PenguAyarlar.BEDROCK_KONTROL, true));
                o.put("ram", LauncherPreferences.DEFAULT_PREF.getInt("allocation", LauncherPreferences.PREF_RAM_ALLOCATION));
                o.put("ramMax", Tools.getTotalDeviceMemory(ctx));
                o.put("calisiyor", ProgressLayout.hasProcesses());
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void sunucu(String id) {
            PojavApplication.sExecutorService.execute(() -> {
                PenguSunucu.Durum d = PenguSunucu.sorgula();
                cevap(id, json("acik", d.acik, "oyuncu", d.oyuncu, "max", d.max));
            });
        }

        /** Offline giris: kullanici adi + (istege bagli) AuthMe sifresi. */
        @JavascriptInterface
        public void girisOffline(String id, String ad, String sifre, boolean kayit) {
            Context ctx = getContext();
            if (ctx == null) return;
            ad = ad == null ? "" : ad.trim();
            sifre = sifre == null ? "" : sifre;
            if (!KULLANICI_ADI.matcher(ad).matches()) {
                cevap(id, json("hata", "Kullanıcı adı 3-16 karakter olmalı (harf, rakam, _)"));
                return;
            }
            String hata = sifreHatasi(sifre);
            if (hata != null) { cevap(id, json("hata", hata)); return; }

            String sonAd = ad, sonSifre = sifre;
            Runnable bitir = () -> Tools.runOnUiThread(() -> {
                ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{sonAd, ""});
                mcAccountSpinner s = secici();
                if (s != null) s.hesapSec(sonAd);
                cevap(id, json("ok", true));
            });
            if (sifre.isEmpty()) { PenguAyarlar.authmeSil(ctx); bitir.run(); return; }
            if (kayit) { PenguAyarlar.authmeKaydet(ctx, sifre, true); bitir.run(); return; }

            PojavApplication.sExecutorService.execute(() -> {
                String durum = PenguAyarlar.authmeDogrula(sonAd, sonSifre);
                String dogrulamaHatasi = dogrulamaHatasi(durum);
                if (dogrulamaHatasi != null) { cevap(id, json("hata", dogrulamaHatasi)); return; }
                PenguAyarlar.authmeKaydet(ctx, sonSifre, false);
                bitir.run();
            });
        }

        @JavascriptInterface
        public void girisMicrosoft() {
            Tools.runOnUiThread(() -> {
                if (isAdded()) Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null);
            });
        }

        @JavascriptInterface
        public void hesapSec(String ad) {
            Tools.runOnUiThread(() -> {
                mcAccountSpinner s = secici();
                if (s != null) s.hesapSec(ad);
                olay("hesap", new JSONObject());
            });
        }

        @JavascriptInterface
        public void hesapSil(String ad) {
            Tools.runOnUiThread(() -> {
                mcAccountSpinner s = secici();
                if (s != null) s.hesapSil(ad);
                olay("hesap", new JSONObject());
            });
        }

        /** Sunucu sifresini sonradan degistirme (Ayarlar). */
        @JavascriptInterface
        public void sifreKaydet(String id, String sifre, boolean kayit) {
            Context ctx = getContext();
            if (ctx == null) return;
            MinecraftAccount h = PojavProfile.getCurrentProfileContent(ctx, null);
            if (h == null) { cevap(id, json("hata", "Önce giriş yapmalısın.")); return; }
            if (sifre == null || sifre.isEmpty()) { PenguAyarlar.authmeSil(ctx); cevap(id, json("ok", true)); return; }
            String hata = sifreHatasi(sifre);
            if (hata != null) { cevap(id, json("hata", hata)); return; }
            if (kayit) { PenguAyarlar.authmeKaydet(ctx, sifre, true); cevap(id, json("ok", true)); return; }
            PojavApplication.sExecutorService.execute(() -> {
                String dh = dogrulamaHatasi(PenguAyarlar.authmeDogrula(h.username, sifre));
                if (dh != null) { cevap(id, json("hata", dh)); return; }
                PenguAyarlar.authmeKaydet(ctx, sifre, false);
                cevap(id, json("ok", true));
            });
        }

        @JavascriptInterface
        public void oyna() {
            Tools.runOnUiThread(() -> {
                Context ctx = getContext();
                if (ctx == null) return;
                if (PojavProfile.getCurrentProfileContent(ctx, null) == null) {
                    olay("oynaHata", json("mesaj", "Önce giriş yapmalısın."));
                    return;
                }
                if (ProgressLayout.hasProcesses()) {
                    olay("oynaHata", json("mesaj", "Zaten hazırlanıyor, biraz bekle."));
                    return;
                }
                // Sesli sohbet icin mikrofon izni: bir kez sor, cevap ne olursa olsun devam et
                SharedPreferences p = PenguAyarlar.prefs(ctx);
                if (!p.getBoolean(PenguAyarlar.MIKROFON_SORULDU, false)
                        && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    p.edit().putBoolean(PenguAyarlar.MIKROFON_SORULDU, true).apply();
                    mMikrofonIzni.launch(android.Manifest.permission.RECORD_AUDIO);
                    return;
                }
                oynaDevam();
            });
        }

        @JavascriptInterface
        public void modDosyadan() {
            Tools.runOnUiThread(() -> mModSec.launch("*/*"));
        }

        @JavascriptInterface
        public void skinSec(boolean ince) {
            mSkinInce = ince;
            Tools.runOnUiThread(() -> mSkinSec.launch("image/png"));
        }

        @JavascriptInterface
        public void ayar(String anahtar, String deger) {
            Context ctx = getContext();
            if (ctx == null) return;
            switch (anahtar) {
                case "fps":
                    PenguAyarlar.prefs(ctx).edit().putBoolean(PenguAyarlar.FPS_PAKETI, Boolean.parseBoolean(deger)).apply();
                    break;
                case "sodium":
                    PenguAyarlar.prefs(ctx).edit().putBoolean(PenguAyarlar.SODIUM, Boolean.parseBoolean(deger)).apply();
                    break;
                case "bedrock":
                    PenguAyarlar.prefs(ctx).edit().putBoolean(PenguAyarlar.BEDROCK_KONTROL, Boolean.parseBoolean(deger)).apply();
                    break;
                case "ram":
                    int mb = Integer.parseInt(deger);
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", mb).apply();
                    LauncherPreferences.PREF_RAM_ALLOCATION = mb;
                    break;
            }
        }

        @JavascriptInterface
        public void ac(String ne) {
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                switch (ne) {
                    case "arkadaslar": startActivity(new Intent(requireContext(), ArkadaslarActivity.class)); break;
                    case "kontroller": startActivity(new Intent(requireContext(), CustomControlsActivity.class)); break;
                    case "gelismis": Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null); break;
                    case "site": Tools.openURL(requireActivity(), PenguConfig.SITE); break;
                    case "klasor": Tools.openPath(requireContext(), PenguHazirlik.oyunKlasoru(), false); break;
                    case "log": Tools.shareLog(requireContext()); break;
                }
            });
        }

        /* ---------------- modlar ---------------- */

        @JavascriptInterface
        public String modlar() {
            JSONArray liste = new JSONArray();
            File[] dosyalar = new File(PenguHazirlik.oyunKlasoru(), "mods").listFiles();
            if (dosyalar == null) return liste.toString();
            Arrays.sort(dosyalar, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : dosyalar) {
                String ad = f.getName();
                boolean acik = ad.endsWith(".jar");
                if (!acik && !ad.endsWith(".jar.disabled")) continue;
                String ku = ad.toLowerCase(Locale.ROOT);
                String tur = ku.startsWith("penguscraft-") ? "sunucu" : "";
                for (PenguConfig.Mod m : PenguConfig.MODLAR) {
                    if (ku.contains(m.anahtar)) { tur = "zorunlu".equals(m.tur) ? "sunucu" : m.tur; break; }
                }
                liste.put(json("dosya", ad, "acik", acik, "tur", tur, "boyut", f.length()));
            }
            return liste.toString();
        }

        @JavascriptInterface
        public void modAc(String dosya, boolean acik) {
            File mods = new File(PenguHazirlik.oyunKlasoru(), "mods");
            File f = new File(mods, dosya);
            if (!f.getParentFile().equals(mods) || !f.exists()) return;
            String yeni = acik ? dosya.replaceAll("\\.disabled$", "") : (dosya.endsWith(".disabled") ? dosya : dosya + ".disabled");
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(new File(mods, yeni));
        }

        @JavascriptInterface
        public void modSil(String dosya) {
            File mods = new File(PenguHazirlik.oyunKlasoru(), "mods");
            File f = new File(mods, dosya);
            if (f.getParentFile().equals(mods) && !dosya.toLowerCase(Locale.ROOT).startsWith("penguscraft-")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }

        @JavascriptInterface
        public void modKur(String id, String projeId) {
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    File mods = new File(PenguHazirlik.oyunKlasoru(), "mods");
                    //noinspection ResultOfMethodCallIgnored
                    mods.mkdirs();
                    PenguHazirlik.modrinthKur(projeId, mods);
                    cevap(id, json("ok", true));
                } catch (Exception e) {
                    cevap(id, json("hata", String.valueOf(e.getMessage())));
                }
            });
        }
    }

    private @Nullable String sifreHatasi(String sifre) {
        if (sifre.isEmpty()) return null;
        if (sifre.length() < 4) return "Şifre en az 4 karakter olmalı.";
        if (sifre.matches(".*\\s.*")) return "Şifrede boşluk olamaz.";
        return null;
    }

    private @Nullable String dogrulamaHatasi(String durum) {
        switch (durum) {
            case "yanlis": return "Şifre yanlış.";
            case "kayitsiz": return "Bu kullanıcı adıyla sunucuda hesap yok. \"İlk kez giriyorum\" seçeneğini işaretle.";
            case "limit": return "Çok fazla deneme yapıldı, birkaç saniye bekleyip tekrar dene.";
            default: return null; // "dogru" ya da siteye ulasilamadi: AuthMe oyunda uyarir
        }
    }
}
