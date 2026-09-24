package net.kdt.pojavlaunch.pengu;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Uygulama ici otomatik guncelleme. Sitedeki latest-android.json'daki versionCode bizimkinden
 * buyukse APK'yi indirir, SHA-256 ile dogrular ve Android'in kurulum ekranini acar.
 *
 * latest-android.json:
 * {"versionCode":2,"versionName":"1.0.1","url":"https://penguscraft.com.tr/indir/Pengu-Launcher-1.0.1.apk",
 *  "sha256":"...","zorunlu":true,"notlar":"..."}
 */
public final class PenguGuncelleme {
    private static final String TAG = "PenguGuncelleme";
    private static boolean kontrolEdildi = false;

    private PenguGuncelleme() {}

    /** Launcher acilisinda bir kez cagrilir. */
    public static void kontrolEt(Activity activity) {
        if (kontrolEdildi) return;
        kontrolEdildi = true;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JSONObject bilgi = new JSONObject(new String(
                        PenguHazirlik.indir(PenguConfig.GUNCELLEME_URL + "?t=" + System.currentTimeMillis()),
                        StandardCharsets.UTF_8));
                if (bilgi.optInt("versionCode", 0) <= BuildConfig.VERSION_CODE) return;
                activity.runOnUiThread(() -> sor(activity, bilgi));
            } catch (Exception e) {
                Log.i(TAG, "Guncelleme kontrol edilemedi: " + e);
            }
        });
    }

    private static void sor(Activity activity, JSONObject bilgi) {
        if (activity.isFinishing()) return;
        boolean zorunlu = bilgi.optBoolean("zorunlu", true);
        String notlar = bilgi.optString("notlar", "");
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.pengu_guncelleme_baslik, bilgi.optString("versionName", "")))
                .setMessage(notlar.isEmpty() ? activity.getString(R.string.pengu_guncelleme_mesaj) : notlar)
                .setCancelable(!zorunlu)
                .setPositiveButton(R.string.pengu_guncelle, (d, w) -> indir(activity, bilgi));
        if (!zorunlu) b.setNegativeButton(R.string.pengu_sonra, null);
        b.show();
    }

    private static void indir(Activity activity, JSONObject bilgi) {
        File hedef = new File(activity.getCacheDir(), "guncelleme/pengu-launcher.apk");
        PojavApplication.sExecutorService.execute(() -> {
            try {
                //noinspection ResultOfMethodCallIgnored
                hedef.getParentFile().mkdirs();
                apkIndir(bilgi.getString("url"), hedef);
                String beklenen = bilgi.optString("sha256", "");
                if (!beklenen.isEmpty() && !beklenen.equalsIgnoreCase(sha256(hedef))) {
                    //noinspection ResultOfMethodCallIgnored
                    hedef.delete();
                    throw new IOException("İndirilen dosya bozuk, tekrar dene.");
                }
                activity.runOnUiThread(() -> kur(activity, hedef));
            } catch (Exception e) {
                Log.e(TAG, "Guncelleme indirilemedi", e);
                Tools.runOnUiThread(() -> Toast.makeText(activity,
                        activity.getString(R.string.pengu_guncelleme_hata, e.getMessage()), Toast.LENGTH_LONG).show());
                kontrolEdildi = false;
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        });
    }

    private static void apkIndir(String adres, File hedef) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(adres).openConnection();
        c.setRequestProperty("User-Agent", PenguConfig.USER_AGENT);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        try {
            if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode());
            long toplam = c.getContentLengthLong();
            long inen = 0;
            int sonYuzde = -1;
            try (InputStream is = c.getInputStream(); OutputStream os = new FileOutputStream(hedef)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    inen += n;
                    int yuzde = toplam > 0 ? (int) (inen * 100 / toplam) : 0;
                    if (yuzde != sonYuzde) {
                        sonYuzde = yuzde;
                        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, yuzde,
                                R.string.pengu_hazirlik, "Güncelleme %" + yuzde);
                    }
                }
            }
        } finally {
            c.disconnect();
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static void kur(Activity activity, File apk) {
        // Android 8+: "bilinmeyen uygulamalari yukle" izni yoksa once ayar ekranina yolla
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.pengu_kurulum_izni, Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            kontrolEdildi = false; // izin verip geri donunce tekrar sorulsun
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".guncelleme", apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }
}
