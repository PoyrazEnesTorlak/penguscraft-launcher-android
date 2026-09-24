package net.kdt.pojavlaunch.pengu;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Pengu'ya ozel ayarlar: FPS paketi secimleri ve AuthMe (sunucu sifresi) bilgisi. */
public final class PenguAyarlar {
    public static final String FPS_PAKETI = "fps_paketi";
    public static final String SODIUM = "sodium";
    /** Bedrock tarzi dokunmatik kontroller (TouchController modu + sade dugme katmani) */
    public static final String BEDROCK_KONTROL = "bedrock_kontrol";
    public static final String MIKROFON_SORULDU = "mikrofon_soruldu";
    private static final String AUTHME_SIFRE = "authme_sifre";
    private static final String AUTHME_KAYIT = "authme_kayit_bekliyor";

    private PenguAyarlar() {}

    /** Uygulamanin ozel alaninda; diger uygulamalar okuyamaz. */
    public static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("pengu", Context.MODE_PRIVATE);
    }

    public static void authmeKaydet(Context ctx, String sifre, boolean kayit) {
        prefs(ctx).edit().putString(AUTHME_SIFRE, sifre).putBoolean(AUTHME_KAYIT, kayit).apply();
    }

    public static boolean authmeVar(Context ctx) {
        return !prefs(ctx).getString(AUTHME_SIFRE, "").isEmpty();
    }

    public static void authmeSil(Context ctx) {
        prefs(ctx).edit().remove(AUTHME_SIFRE).remove(AUTHME_KAYIT).apply();
    }

    /**
     * Istemci modu oyuna girince bu dosyayi okuyup /login (ilk seferde /register) gonderiyor.
     * PC launcher'daki authmeDosyasiYaz ile ayni bicim.
     */
    static void authmeDosyasiYaz(Context ctx, File oyun) {
        File dosya = new File(oyun, "authme.txt");
        SharedPreferences p = prefs(ctx);
        String sifre = p.getString(AUTHME_SIFRE, "");
        if (sifre.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            dosya.delete();
            return;
        }
        boolean kayit = p.getBoolean(AUTHME_KAYIT, false);
        try (OutputStream os = new FileOutputStream(dosya)) {
            os.write(((kayit ? "register " : "login ") + sifre + "\n").getBytes(StandardCharsets.UTF_8));
            // Kayit komutu bir kez gider; sonraki acilista normal giris
            if (kayit) p.edit().putBoolean(AUTHME_KAYIT, false).apply();
        } catch (IOException e) {
            Log.w("PenguAyarlar", "authme.txt yazilamadi", e);
        }
    }

    /**
     * Sifreyi sitedeki launcher-dogrula ucuna sorar.
     * @return "dogru", "yanlis", "kayitsiz", "limit" veya "ulasilamadi". Ag islemi; UI thread'de cagirma.
     */
    public static String authmeDogrula(String oyuncu, String sifre) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(PenguConfig.AUTHME_DOGRULA).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Content-Type", "application/json");
            JSONObject govde = new JSONObject().put("oyuncu", oyuncu).put("sifre", sifre);
            try (OutputStream os = c.getOutputStream()) {
                os.write(govde.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) return "ulasilamadi";
            try (InputStream is = c.getInputStream()) {
                String cevap = new String(oku(is), StandardCharsets.UTF_8);
                return new JSONObject(cevap).optString("durum", "ulasilamadi");
            }
        } catch (Exception e) {
            // Siteye ulasilamiyorsa girisi engellemiyoruz; AuthMe oyun icinde uyarir
            return "ulasilamadi";
        }
    }

    static byte[] oku(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
