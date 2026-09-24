package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.pengu.PenguAyarlar;

import java.util.regex.Pattern;

/**
 * Offline (sifresiz Minecraft hesabi) giris. PengusCraft'ta kimlik AuthMe ile dogrulandigi icin
 * PC launcher'daki gibi kullanici adiyla birlikte sunucu sifresi de burada aliniyor.
 */
public class LocalLoginFragment extends Fragment {
    public static final String TAG = "LOCAL_LOGIN_FRAGMENT";

    private static final Pattern KULLANICI_ADI = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private EditText mUsernameEditText, mSifre, mSifreTekrar;
    private CheckBox mKayit;
    private TextView mHata;
    private Button mGiris;

    public LocalLoginFragment(){
        super(R.layout.fragment_local_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.login_edit_email);
        mSifre = view.findViewById(R.id.pengu_login_sifre);
        mSifreTekrar = view.findViewById(R.id.pengu_login_sifre_tekrar);
        mKayit = view.findViewById(R.id.pengu_login_kayit);
        mHata = view.findViewById(R.id.pengu_login_hata);
        mGiris = view.findViewById(R.id.login_button);

        mKayit.setOnCheckedChangeListener((b, secili) -> mSifreTekrar.setVisibility(secili ? View.VISIBLE : View.GONE));
        mGiris.setOnClickListener(v -> girisYap());
    }

    private void hata(String metin) {
        mHata.setText(metin);
        mHata.setVisibility(metin == null ? View.GONE : View.VISIBLE);
    }

    private void girisYap() {
        Context ctx = requireContext();
        String ad = mUsernameEditText.getText().toString().trim();
        String sifre = mSifre.getText().toString();
        boolean kayit = mKayit.isChecked();

        if (!KULLANICI_ADI.matcher(ad).matches()) {
            hata(ctx.getString(R.string.local_login_bad_username_text));
            return;
        }
        // Sifre istege bagli: bos birakilirsa oyunda /login elle yazilir
        if (!sifre.isEmpty()) {
            if (sifre.length() < 4) { hata(getString(R.string.pengu_sifre_kisa)); return; }
            if (sifre.matches(".*\\s.*")) { hata(getString(R.string.pengu_sifre_bosluk)); return; }
            if (kayit && !sifre.equals(mSifreTekrar.getText().toString())) { hata(getString(R.string.pengu_sifre_ayni_degil)); return; }
        }
        hata(null);

        if (sifre.isEmpty() || kayit) {
            if (!sifre.isEmpty()) PenguAyarlar.authmeKaydet(ctx, sifre, true);
            else PenguAyarlar.authmeSil(ctx);
            bitir(ad);
            return;
        }

        // Mevcut hesap: sifreyi once sitede dogrula
        mGiris.setEnabled(false);
        mGiris.setText(R.string.pengu_kontrol_ediliyor);
        PojavApplication.sExecutorService.execute(() -> {
            String durum = PenguAyarlar.authmeDogrula(ad, sifre);
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                mGiris.setEnabled(true);
                mGiris.setText(R.string.login_online_login_label);
                switch (durum) {
                    case "yanlis": hata(getString(R.string.pengu_sifre_yanlis)); return;
                    case "kayitsiz": hata(getString(R.string.pengu_sifre_kayitsiz)); return;
                    case "limit": hata(getString(R.string.pengu_sifre_limit)); return;
                    default: // "dogru" ya da siteye ulasilamadi: AuthMe oyunda uyarir
                        PenguAyarlar.authmeKaydet(ctx, sifre, false);
                        bitir(ad);
                }
            });
        });
    }

    private void bitir(String ad) {
        ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{ad, ""});
        Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
    }
}
