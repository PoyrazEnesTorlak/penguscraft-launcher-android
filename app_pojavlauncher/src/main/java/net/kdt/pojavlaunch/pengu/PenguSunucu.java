package net.kdt.pojavlaunch.pengu;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minecraft "Server List Ping" ile sunucunun acik olup olmadigini ve oyuncu sayisini okur. */
public final class PenguSunucu {
    public static final class Durum {
        public final boolean acik;
        public final int oyuncu, max;
        Durum(boolean acik, int oyuncu, int max) { this.acik = acik; this.oyuncu = oyuncu; this.max = max; }
    }

    private PenguSunucu() {}

    /** Ag islemi; UI thread'de cagirma. */
    public static Durum sorgula() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(PenguConfig.SUNUCU_IP, PenguConfig.SUNUCU_PORT), 5000);
            s.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());

            ByteArrayOutputStream el = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(el);
            h.writeByte(0x00);                 // handshake
            varint(h, 767);                    // 1.21 protokolu
            string(h, PenguConfig.SUNUCU_IP);
            h.writeShort(PenguConfig.SUNUCU_PORT);
            varint(h, 1);                      // durum
            paket(out, el.toByteArray());
            paket(out, new byte[]{0x00});      // durum istegi

            varintOku(in);                     // paket boyu
            if (varintOku(in) != 0x00) return new Durum(false, 0, 0);
            byte[] json = new byte[varintOku(in)];
            in.readFully(json);
            JSONObject oyuncular = new JSONObject(new String(json, StandardCharsets.UTF_8)).optJSONObject("players");
            return new Durum(true,
                    oyuncular != null ? oyuncular.optInt("online") : 0,
                    oyuncular != null ? oyuncular.optInt("max") : 0);
        } catch (Exception e) {
            return new Durum(false, 0, 0);
        }
    }

    private static void paket(DataOutputStream out, byte[] veri) throws IOException {
        varint(out, veri.length);
        out.write(veri);
        out.flush();
    }

    private static void string(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        varint(out, b.length);
        out.write(b);
    }

    private static void varint(DataOutputStream out, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    private static int varintOku(InputStream in) throws IOException {
        int sonuc = 0, kaydir = 0, b;
        do {
            b = in.read();
            if (b == -1) throw new IOException("baglanti kapandi");
            sonuc |= (b & 0x7F) << kaydir;
            kaydir += 7;
            if (kaydir > 35) throw new IOException("gecersiz varint");
        } while ((b & 0x80) != 0);
        return sonuc;
    }
}
