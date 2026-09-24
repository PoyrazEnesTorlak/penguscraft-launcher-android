package net.kdt.pojavlaunch.pengu;

import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Skin yukleme: PNG'yi sitemize (skin.php) yollar, sunucudaki SkinsRestorer oradan okur.
 * Istemci modu oyuna girince skin-uygula.txt'yi gorup /skin komutunu bir kez gonderir.
 */
public final class PenguSkin {
    private PenguSkin() {}

    /** @return hata metni, gecerliyse null */
    public static String dogrula(byte[] png) {
        if (png.length > 512 * 1024) return "Dosya çok büyük (en fazla 512 KB).";
        byte[] imza = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (png.length < 24) return "Bu bir PNG dosyası değil.";
        for (int i = 0; i < 8; i++) if (png[i] != imza[i]) return "Bu bir PNG dosyası değil.";
        int g = oku32(png, 16), y = oku32(png, 20);
        if (!(g == 64 && (y == 64 || y == 32))) return "Skin ölçüsü 64x64 olmalı (seçilen: " + g + "x" + y + ").";
        return null;
    }

    private static int oku32(byte[] b, int i) {
        return ((b[i] & 0xff) << 24) | ((b[i + 1] & 0xff) << 16) | ((b[i + 2] & 0xff) << 8) | (b[i + 3] & 0xff);
    }

    /**
     * Ag islemi; UI thread'de cagirma.
     * @return hata metni, basariliysa null
     */
    public static String yukle(String oyuncu, byte[] png, boolean ince) {
        String tip = ince ? "slim" : "classic";
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(PenguConfig.SKIN_API).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", PenguConfig.USER_AGENT);
            JSONObject govde = new JSONObject()
                    .put("oyuncu", oyuncu)
                    .put("variant", tip)
                    .put("png", Base64.encodeToString(png, Base64.NO_WRAP));
            try (OutputStream os = c.getOutputStream()) {
                os.write(govde.toString().getBytes(StandardCharsets.UTF_8));
            }
            int kod = c.getResponseCode();
            InputStream is = kod / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            JSONObject cevap;
            try {
                cevap = new JSONObject(new String(PenguAyarlar.oku(is), StandardCharsets.UTF_8));
            } catch (Exception e) {
                cevap = new JSONObject();
            }
            String url = cevap.optString("url", "");
            if (kod / 100 != 2 || url.isEmpty()) return cevap.optString("error", "Sunucu hatası (" + kod + ")");

            File oyun = PenguHazirlik.oyunKlasoru();
            //noinspection ResultOfMethodCallIgnored
            oyun.mkdirs();
            try (OutputStream os = new FileOutputStream(new File(oyun, "skin-uygula.txt"))) {
                os.write((tip + " " + url + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return null;
        } catch (Exception e) {
            return "Bağlanılamadı: " + e.getMessage();
        }
    }
}
