package net.kdt.pojavlaunch.pengu;

/**
 * Pengu Launcher (Android) ayarlari. PC launcher'daki launcher.js CONFIG blogunun karsiligi;
 * sunucu/surum degisirse burayi ve PC'deki CONFIG'i birlikte guncelle.
 */
public final class PenguConfig {
    private PenguConfig() {}

    public static final String SUNUCU_ADI = "PengusCraft";
    public static final String SUNUCU_IP = "oyna.penguscraft.com";
    public static final int SUNUCU_PORT = 25587;
    public static final String SUNUCU_ADRES = SUNUCU_IP + ":" + SUNUCU_PORT;

    public static final String SITE = "https://www.penguscraft.com.tr";
    public static final String SKIN_API = "https://www.penguscraft.com.tr/skin";
    public static final String AUTHME_DOGRULA = "https://penguscraft.com.tr/launcher-dogrula";
    public static final String ARKADASLAR_URL = "https://arkadaslar.penguscraft.com.tr";

    /** Uygulama guncelleme bilgisi: {"versionCode":N,"versionName":"x","url":"...apk","notlar":"..."} */
    public static final String GUNCELLEME_URL = "https://penguscraft.com.tr/indir/latest-android.json";

    public static final String MC_SURUM = "1.21";
    public static final String FABRIC_SURUM = "0.16.9";

    /**
     * Launcher profilinin anahtari ve oyun klasoru (DIR_GAME_HOME altinda).
     * Anahtar gecerli bir UUID olmali: Amethyst UUID olmayan anahtarlari her yuklemede
     * rastgele UUID ile degistiriyor, secili profil kayboluyordu.
     */
    public static final String PROFIL_ANAHTAR = "7e6a1c2e-5f0b-4c8e-9d61-70656e677573";
    public static final String OYUN_KLASORU = "penguscraft";

    public static final String MODRINTH = "https://api.modrinth.com/v2";
    public static final String USER_AGENT = "penguscraft-launcher-android/1.0 (oyna.penguscraft.com)";

    /**
     * Modrinth modu: proje id, gorunen ad, jar adinda gecen anahtar kelime ve turu.
     * APK icindeki assets/pengu/mods-hazir'da jar'i varsa oradan kurulur (indirme yok),
     * yoksa Modrinth'ten iner. yayinla.ps1 bu klasoru her yayinda gunceller.
     */
    public static final class Mod {
        public final String id, ad, anahtar;
        /** "zorunlu", "fps" (FPS paketi) veya "kontrol" (Bedrock tarzi dokunmatik kontroller) */
        public final String tur;
        Mod(String id, String ad, String anahtar, String tur) {
            this.id = id; this.ad = ad; this.anahtar = anahtar; this.tur = tur;
        }
    }

    public static final Mod[] MODLAR = {
            // Zorunlu (PC ile ayni)
            new Mod("P7dR8mSH", "Fabric API", "fabric-api", "zorunlu"),
            new Mod("9eGKb6K1", "Simple Voice Chat", "voicechat", "zorunlu"),
            // Bedrock (Minecraft PE) tarzi dokunmatik kontroller
            new Mod("U7KwGAnT", "TouchController", "touchcontroller", "kontrol"),
            // FPS paketi: telefonda en cok fark yaratanlar
            new Mod("AANobbMI", "Sodium", "sodium", "fps"),
            new Mod("uXXizFIs", "FerriteCore", "ferritecore", "fps"),
            new Mod("nmDcB62a", "ModernFix", "modernfix", "fps"),
            new Mod("gvQqBUqZ", "Lithium", "lithium", "fps"),
            new Mod("5ZwdcRci", "ImmediatelyFast", "immediatelyfast", "fps"),
            new Mod("NNAgCjsB", "EntityCulling", "entityculling", "fps"),
            new Mod("LQ3K71Q1", "Dynamic FPS", "dynamic-fps", "fps"),
    };

    /** Oyun ici dokunmatik duzenler (assets/pengu/kontroller) */
    public static final String KONTROL_BEDROCK = "pengu-dokunmatik.json";
    public static final String KONTROL_KLASIK = "pengu-klasik.json";

    /** assets/pengu/packs altindaki, oyuncuya otomatik kurulan kaynak paketleri. */
    public static final String[] KAYNAK_PAKETLERI = {
            "penguscraft-logo.zip",
            "pengusmc1.zip",
            "si-font.zip",
            "ResourcePackManager_resource_pack.zip"
    };
    /** Isimlerin basindaki rozet fontu; her acilista acik oldugundan emin oluyoruz. */
    public static final String ZORUNLU_PAKET = "penguscraft-logo.zip";
}
