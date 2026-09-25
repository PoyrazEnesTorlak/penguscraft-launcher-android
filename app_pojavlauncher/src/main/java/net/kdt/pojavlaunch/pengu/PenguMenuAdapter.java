package net.kdt.pojavlaunch.pengu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.R;

/** Oyun ici Pengu menusu satirlari: ikon + yazi, istenirse son satir kirmizi (tehlikeli islem). */
public class PenguMenuAdapter extends ArrayAdapter<String> {
    private static final int YAZI = 0xFFE8EEF7;
    private static final int KIRMIZI = 0xFFEF4444;
    private static final int YESIL = 0xFF5CC23F;

    private final int[] mIkonlar;
    private final int mTehlikeli;

    /**
     * @param ikonlar her satirin drawable'i (0 = ikonsuz); null ise hic ikon yok
     * @param tehlikeli kirmizi gosterilecek satir, yoksa -1
     */
    public PenguMenuAdapter(Context ctx, String[] satirlar, int[] ikonlar, int tehlikeli) {
        super(ctx, R.layout.pengu_menu_item, android.R.id.text1, satirlar);
        mIkonlar = ikonlar;
        mTehlikeli = tehlikeli;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        TextView tv = (TextView) super.getView(position, convertView, parent);
        boolean kirmizi = position == mTehlikeli;
        tv.setTextColor(kirmizi ? KIRMIZI : YAZI);
        Drawable ikon = null;
        if (mIkonlar != null && position < mIkonlar.length && mIkonlar[position] != 0) {
            ikon = ContextCompat.getDrawable(getContext(), mIkonlar[position]);
            if (ikon != null) {
                ikon = ikon.mutate();
                ikon.setTint(kirmizi ? KIRMIZI : YESIL);
                int boyut = (int) (22 * getContext().getResources().getDisplayMetrics().density);
                ikon.setBounds(0, 0, boyut, boyut);
            }
        }
        tv.setCompoundDrawablesRelative(ikon, null, null, null);
        return tv;
    }
}
