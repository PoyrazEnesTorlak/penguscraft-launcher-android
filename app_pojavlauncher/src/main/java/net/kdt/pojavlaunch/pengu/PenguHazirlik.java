package net.kdt.pojavlaunch.pengu;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OYNA'ya basilinca calisan hazirlik: PC launcher'daki game:launch akisinin Android karsiligi.
 * Fabric profili, zorunlu + FPS modlari, bizim modumuz, kaynak paketleri, options.txt,
 * kilitli servers.dat ve AuthMe dosyasi burada hazirlanir. Bitince oyunu baslatma isi
 * Amethyst'in normal akisina (LAUNCH_GAME) birakilir.
 */
public final class PenguHazirlik {
    private static final String TAG = "PenguHazirlik";
    private static final String BIZIM_MOD_ONEKI = "penguscraft-";

    private PenguHazirlik() {}

    public interface Sonuc {
        void tamam();
        void hata(Throwable t);
    }

    public static File oyunKlasoru() {
        return new File(Tools.DIR_GAME_HOME, PenguConfig.OYUN_KLASORU);
    }

    public static boolean pengusProfiliMi(MinecraftProfile profil) {
        return profil != null && ("amethyst://" + PenguConfig.OYUN_KLASORU).equals(profil.gameDir);
    }

    public static void baslat(Context ctx, Sonuc sonuc) {
        Context app = ctx.getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            Throwable hata = null;
            try {
                hazirla(app);
            } catch (Throwable t) {
                Log.e(TAG, "Hazirlik basarisiz", t);
                hata = t;
            }
            // Ilerleme temizlenmeden LAUNCH_GAME "islem suruyor" deyip reddediyor; once temizle
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            final Throwable sonHata = hata;
            Tools.runOnUiThread(() -> {
                if (sonHata == null) sonuc.tamam();
                else sonuc.hata(sonHata);
            });
        });
    }

    private static void ilerleme(int yuzde, String metin) {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, yuzde, R.string.pengu_hazirlik, metin);
    }

    private static void hazirla(Context ctx) throws Exception {
        SharedPreferences ayar = PenguAyarlar.prefs(ctx);
        File oyun = oyunKlasoru();
        File mods = new File(oyun, "mods");
        if (!mods.isDirectory() && !mods.mkdirs()) throw new IOException("Oyun klasoru olusturulamadi: " + mods);

        ilerleme(5, "Fabric");
        String surumId = fabricKur();
        kontrolDuzenleri(ctx.getAssets());
        profilKur(ctx, surumId);

        ilerleme(15, "Modlar");
        bizimModlar(ctx.getAssets(), mods);
        kaldirilanModlar(mods);
        modrinthModlari(ctx.getAssets(), mods);

        ilerleme(85, "Kaynak paketleri");
        kaynakPaketleri(ctx.getAssets(), new File(oyun, "resourcepacks"));
        optionsTxt(oyun, ayar);
        logoPaketi(oyun);
        sesliSohbetAyari(oyun);

        ilerleme(95, "Sunucu");
        serversDat(oyun);
        PenguAyarlar.authmeDosyasiYaz(ctx, oyun);
        ilerleme(100, "Hazır");
    }

    /* ------------------------------------------------------------------ Fabric */

    private static String fabricKur() throws IOException {
        String surumId = "fabric-loader-" + PenguConfig.FABRIC_SURUM + "-" + PenguConfig.MC_SURUM;
        File json = new File(Tools.DIR_HOME_VERSION, surumId + "/" + surumId + ".json");
        if (json.isFile() && json.length() > 0) return surumId;

        String url = FabriclikeUtils.FABRIC_UTILS.createJsonDownloadUrl(PenguConfig.MC_SURUM, PenguConfig.FABRIC_SURUM);
        String icerik = new String(indir(url), StandardCharsets.UTF_8);
        try {
            surumId = new JSONObject(icerik).getString("id");
        } catch (Exception e) {
            throw new IOException("Fabric bilgisi okunamadi", e);
        }
        json = new File(Tools.DIR_HOME_VERSION, surumId + "/" + surumId + ".json");
        //noinspection ResultOfMethodCallIgnored
        json.getParentFile().mkdirs();
        Tools.write(json.getAbsolutePath(), icerik);
        return surumId;
    }

    private static void profilKur(Context ctx, String surumId) {
        LauncherProfiles.load();
        MinecraftProfile profil = LauncherProfiles.mainProfileJson.profiles.get(PenguConfig.PROFIL_ANAHTAR);
        // Ayni oyun klasorunu kullanan baska kopyalari (eski surumlerden kalma) temizle
        java.util.Iterator<Map.Entry<String, MinecraftProfile>> it = LauncherProfiles.mainProfileJson.profiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MinecraftProfile> e = it.next();
            if (!e.getKey().equals(PenguConfig.PROFIL_ANAHTAR) && pengusProfiliMi(e.getValue())) it.remove();
        }
        if (profil == null) {
            profil = new MinecraftProfile();
            profil.name = PenguConfig.SUNUCU_ADI;
            try (InputStream is = ctx.getAssets().open("pengu/logo.png")) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                profil.icon = "data:image/png;base64," + android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
            } catch (IOException ignored) { /* varsayilan ikon kalir */ }
        }
        profil.lastVersionId = surumId;
        profil.gameDir = "amethyst://" + PenguConfig.OYUN_KLASORU;
        profil.controlFile = PenguConfig.KONTROL;
        LauncherProfiles.mainProfileJson.profiles.put(PenguConfig.PROFIL_ANAHTAR, profil);
        LauncherProfiles.write();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, PenguConfig.PROFIL_ANAHTAR)
                .commit();
    }

    /* ------------------------------------------------------------------ Modlar */

    /** assets/pengu/mods icindeki penguscraft-*.jar'lari kurar, eski surumlerini siler. */
    private static void bizimModlar(AssetManager am, File mods) throws IOException {
        String[] guncel = am.list("pengu/mods");
        if (guncel == null) guncel = new String[0];
        List<String> guncelAdlar = new ArrayList<>();
        for (String a : guncel) if (a.endsWith(".jar")) guncelAdlar.add(a);

        File[] mevcut = mods.listFiles();
        if (mevcut != null) for (File f : mevcut) {
            String ad = f.getName();
            if (!ad.toLowerCase(Locale.ROOT).startsWith(BIZIM_MOD_ONEKI)) continue;
            String temiz = ad.replaceAll("\\.disabled$", "");
            if (guncelAdlar.contains(temiz) && !ad.endsWith(".disabled")) continue;
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        for (String ad : guncelAdlar) assetKopyala(am, "pengu/mods/" + ad, new File(mods, ad));
    }

    /** Turkce dokunmatik duzenleri (her surumde guncel olsun diye her seferinde) controlmap'e kopyalar. */
    private static void kontrolDuzenleri(AssetManager am) {
        File hedef = new File(Tools.CTRLMAP_PATH);
        //noinspection ResultOfMethodCallIgnored
        hedef.mkdirs();
        // Eski surumlerin klasik duzeni artik kullanilmiyor
        //noinspection ResultOfMethodCallIgnored
        new File(hedef, "pengu-klasik.json").delete();
        File f = new File(hedef, PenguConfig.KONTROL);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        try {
            assetKopyala(am, "pengu/kontroller/" + PenguConfig.KONTROL, f);
        } catch (IOException e) {
            Log.w(TAG, "Kontrol duzeni kopyalanamadi", e);
        }
    }

    /** Eski surumlerin kurdugu FPS paketi ve TouchController jar'larini siler. */
    private static void kaldirilanModlar(File mods) {
        for (String anahtar : PenguConfig.KALDIRILAN_MODLAR)
            for (File f : modBul(mods, anahtar)) //noinspection ResultOfMethodCallIgnored
                f.delete();
    }

    private static void modrinthModlari(AssetManager am, File mods) {
        String[] hazir;
        try { hazir = am.list("pengu/mods-hazir"); } catch (IOException e) { hazir = null; }
        if (hazir == null) hazir = new String[0];

        PenguConfig.Mod[] liste = PenguConfig.MODLAR;
        for (int i = 0; i < liste.length; i++) {
            PenguConfig.Mod mod = liste[i];
            List<File> kurulu = modBul(mods, mod.anahtar);
            if (!kurulu.isEmpty()) continue; // .disabled de sayilir: oyuncu bilerek kapatmis olabilir

            ilerleme(15 + (i * 70 / liste.length), mod.ad);
            // Once APK'nin icindeki hazir kopya: indirme yok, internetsiz de kurulur
            String gomulu = null;
            for (String h : hazir) if (h.toLowerCase(Locale.ROOT).contains(mod.anahtar)) { gomulu = h; break; }
            if (gomulu != null) {
                try {
                    assetKopyala(am, "pengu/mods-hazir/" + gomulu, new File(mods, gomulu));
                    continue;
                } catch (IOException e) {
                    Log.w(TAG, mod.ad + " APK'dan kopyalanamadi, indiriliyor", e);
                }
            }
            try {
                modrinthKur(mod.id, mods);
            } catch (Exception e) {
                // Bir mod inmezse (Modrinth kapali vb.) oyunu engellemiyoruz
                Log.w(TAG, mod.ad + " kurulamadi", e);
            }
        }
    }

    private static List<File> modBul(File mods, String anahtar) {
        List<File> sonuc = new ArrayList<>();
        File[] dosyalar = mods.listFiles();
        if (dosyalar == null) return sonuc;
        String k = anahtar.toLowerCase(Locale.ROOT);
        for (File f : dosyalar) {
            String ad = f.getName().toLowerCase(Locale.ROOT);
            if (ad.contains(k) && (ad.endsWith(".jar") || ad.endsWith(".jar.disabled"))) sonuc.add(f);
        }
        return sonuc;
    }

    public static void modrinthKur(String projeId, File mods) throws Exception {
        JSONObject surum = modrinthSurum(projeId);
        if (surum == null) throw new IOException(projeId + " icin " + PenguConfig.MC_SURUM + " surumu yok");
        dosyaIndir(surum, mods);

        JSONArray bag = surum.optJSONArray("dependencies");
        if (bag == null) return;
        for (int i = 0; i < bag.length(); i++) {
            JSONObject d = bag.getJSONObject(i);
            if (!"required".equals(d.optString("dependency_type")) || d.isNull("project_id")) continue;
            try {
                JSONObject ds = modrinthSurum(d.getString("project_id"));
                if (ds != null) dosyaIndir(ds, mods);
            } catch (Exception ignored) { /* bagimlilik atlanabilir */ }
        }
    }

    private static JSONObject modrinthSurum(String projeId) throws Exception {
        String q = "game_versions=" + URLEncoder.encode("[\"" + PenguConfig.MC_SURUM + "\"]", "UTF-8")
                + "&loaders=" + URLEncoder.encode("[\"fabric\"]", "UTF-8");
        JSONArray surumler = new JSONArray(new String(
                indir(PenguConfig.MODRINTH + "/project/" + projeId + "/version?" + q), StandardCharsets.UTF_8));
        if (surumler.length() == 0) return null;
        for (int i = 0; i < surumler.length(); i++) {
            JSONObject s = surumler.getJSONObject(i);
            if ("release".equals(s.optString("version_type"))) return s;
        }
        return surumler.getJSONObject(0);
    }

    private static void dosyaIndir(JSONObject surum, File mods) throws Exception {
        JSONArray dosyalar = surum.getJSONArray("files");
        JSONObject sec = dosyalar.getJSONObject(0);
        for (int i = 0; i < dosyalar.length(); i++) {
            if (dosyalar.getJSONObject(i).optBoolean("primary")) { sec = dosyalar.getJSONObject(i); break; }
        }
        File hedef = new File(mods, sec.getString("filename"));
        if (hedef.isFile()) return;
        File gecici = new File(mods, hedef.getName() + ".part");
        try (OutputStream os = new FileOutputStream(gecici)) {
            os.write(indir(sec.getString("url")));
        }
        if (!gecici.renameTo(hedef)) throw new IOException("Dosya tasinamadi: " + hedef);
    }

    /* ------------------------------------------------------------ Kaynak paketleri */

    private static void kaynakPaketleri(AssetManager am, File dizin) throws IOException {
        if (!dizin.isDirectory()) //noinspection ResultOfMethodCallIgnored
            dizin.mkdirs();
        for (String ad : PenguConfig.KAYNAK_PAKETLERI) {
            try {
                assetKopyala(am, "pengu/packs/" + ad, new File(dizin, ad));
            } catch (IOException e) {
                Log.w(TAG, "Kaynak paketi yok: " + ad, e);
            }
        }
    }

    /**
     * options.txt'yi ilk kurulumda hazirlar: Turkce, kaynak paketleri acik ve telefona uygun
     * performans ayarlari. Sadece bir kez; oyuncu sonradan degistirirse karismiyoruz.
     */
    private static void optionsTxt(File oyun, SharedPreferences ayar) throws IOException {
        if (ayar.getBoolean("options_uygulandi", false)) return;

        JSONArray paketler = new JSONArray();
        paketler.put("vanilla");
        for (String p : PenguConfig.KAYNAK_PAKETLERI) paketler.put("file/" + p);

        Map<String, String> satirlar = new LinkedHashMap<>();
        satirlar.put("lang", "tr_tr");
        satirlar.put("resourcePacks", paketler.toString().replace("\\/", "/"));
        // Performans: telefonlarda en cok FPS kazandiran ayarlar
        satirlar.put("renderDistance", "6");
        satirlar.put("simulationDistance", "5");
        satirlar.put("graphicsMode", "0");          // Hizli
        satirlar.put("ao", "false");                // Yumusak isik kapali
        satirlar.put("particles", "2");             // Az
        satirlar.put("entityShadows", "false");
        satirlar.put("biomeBlendRadius", "0");
        satirlar.put("mipmapLevels", "0");
        satirlar.put("renderClouds", "\"false\"");
        satirlar.put("enableVsync", "false");
        satirlar.put("maxFps", "60");
        satirlar.put("entityDistanceScaling", "0.75");
        satirlar.put("fullscreen", "false");
        satirlar.put("skipMultiplayerWarning", "true");
        satirlar.put("onboardAccessibility", "false");
        satirlar.put("tutorialStep", "none");

        File dosya = new File(oyun, "options.txt");
        String icerik = dosya.isFile() ? Tools.read(dosya) : "";
        for (Map.Entry<String, String> e : satirlar.entrySet()) icerik = satirAyarla(icerik, e.getKey(), e.getValue());
        Tools.write(dosya.getAbsolutePath(), icerik);
        ayar.edit().putBoolean("options_uygulandi", true).apply();
    }

    /**
     * Simple Voice Chat varsayilan olarak bas-konus (Caps Lock) modunda geliyor; telefonda o tus yok,
     * mikrofon hic acilamiyordu. Her acilista sesle etkinlestirmeye aliyoruz ve ilk kurulum
     * sihirbazini atliyoruz. Susturma oyundaki "Mikrofon" dugmesiyle (M) yapiliyor.
     */
    private static void sesliSohbetAyari(File oyun) {
        File dosya = new File(oyun, "config/voicechat/voicechat-client.properties");
        Map<String, String> zorunlu = new LinkedHashMap<>();
        zorunlu.put("microphone_activation_type", "VOICE");
        zorunlu.put("onboarding_finished", "true");
        zorunlu.put("java_microphone_implementation", "false");
        try {
            List<String> satirlar = new ArrayList<>();
            if (dosya.isFile()) {
                for (String satir : Tools.read(dosya.getAbsolutePath()).split("\\r?\\n")) {
                    int esit = satir.indexOf('=');
                    String anahtar = esit > 0 ? satir.substring(0, esit).trim() : null;
                    if (anahtar != null && zorunlu.containsKey(anahtar)) continue;
                    if (!satir.isEmpty()) satirlar.add(satir);
                }
            }
            for (Map.Entry<String, String> e : zorunlu.entrySet()) satirlar.add(e.getKey() + "=" + e.getValue());
            //noinspection ResultOfMethodCallIgnored
            dosya.getParentFile().mkdirs();
            Tools.write(dosya.getAbsolutePath(), String.join("\n", satirlar) + "\n");
        } catch (IOException e) {
            Log.w(TAG, "Sesli sohbet ayari yazilamadi", e);
        }
    }

    /** Rozet fontu kapaliysa isimlerin basinda kutu gorunur; her acilista listeye ekliyoruz. */
    private static void logoPaketi(File oyun) {
        File dosya = new File(oyun, "options.txt");
        if (!dosya.isFile()) return;
        try {
            String icerik = Tools.read(dosya);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^resourcePacks:(.*)$").matcher(icerik);
            if (!m.find()) return;
            JSONArray liste = new JSONArray(m.group(1));
            String giris = "file/" + PenguConfig.ZORUNLU_PAKET;
            for (int i = 0; i < liste.length(); i++) if (giris.equals(liste.optString(i))) return;
            liste.put(giris);
            Tools.write(dosya.getAbsolutePath(), satirAyarla(icerik, "resourcePacks", liste.toString().replace("\\/", "/")));
        } catch (Exception e) {
            Log.w(TAG, "Rozet fontu etkinlestirilemedi", e);
        }
    }

    private static String satirAyarla(String icerik, String anahtar, String deger) {
        String satir = anahtar + ":" + deger;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^" + java.util.regex.Pattern.quote(anahtar) + ":.*$");
        java.util.regex.Matcher m = p.matcher(icerik);
        if (m.find()) return m.replaceFirst(java.util.regex.Matcher.quoteReplacement(satir));
        String govde = icerik.replaceAll("\\s+$", "");
        return (govde.isEmpty() ? "" : govde + "\n") + satir + "\n";
    }

    /* ---------------------------------------------------------------- servers.dat */

    /** Sunucu listesinde sadece bizim sunucumuz kalir (sikistirilmamis NBT). */
    private static void serversDat(File oyun) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(0x0a); out.writeUTF("");            // kok compound
            out.writeByte(0x09); out.writeUTF("servers");     // servers listesi
            out.writeByte(0x0a); out.writeInt(1);             // eleman turu compound, adet 1
            out.writeByte(0x08); out.writeUTF("ip"); out.writeUTF(PenguConfig.SUNUCU_ADRES);
            out.writeByte(0x08); out.writeUTF("name"); out.writeUTF(PenguConfig.SUNUCU_ADI);
            // Sunucunun kaynak paketi sorulmadan insin (1 = her zaman kabul)
            out.writeByte(0x01); out.writeUTF("acceptTextures"); out.writeByte(1);
            out.writeByte(0x00);                              // sunucu compound sonu
            out.writeByte(0x00);                              // kok compound sonu
            out.flush();

            File hedef = new File(oyun, "servers.dat");
            //noinspection ResultOfMethodCallIgnored
            hedef.setWritable(true);
            try (OutputStream os = new FileOutputStream(hedef)) { os.write(bos.toByteArray()); }
            // Oyun kapanirken listeyi geri yaziyor; salt-okunur yapinca degisiklik kalici olmuyor
            //noinspection ResultOfMethodCallIgnored
            hedef.setReadOnly();
            //noinspection ResultOfMethodCallIgnored
            new File(oyun, "servers.dat_old").delete();
        } catch (IOException e) {
            Log.w(TAG, "servers.dat yazilamadi", e);
        }
    }

    /* ------------------------------------------------------------------ Yardimcilar */

    private static void assetKopyala(AssetManager am, String yol, File hedef) throws IOException {
        long boyut = -1;
        try (android.content.res.AssetFileDescriptor fd = am.openFd(yol)) {
            boyut = fd.getLength();
        } catch (IOException ignored) { /* sikistirilmis asset: boyutu bilinmiyor */ }
        if (hedef.isFile() && boyut >= 0 && hedef.length() == boyut) return;

        try (InputStream is = am.open(yol); OutputStream os = new FileOutputStream(hedef)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    public static byte[] indir(String adres) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(adres).openConnection();
        c.setRequestProperty("User-Agent", PenguConfig.USER_AGENT);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        try {
            int kod = c.getResponseCode();
            if (kod / 100 != 2) throw new IOException("HTTP " + kod + ": " + adres);
            try (InputStream is = c.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
