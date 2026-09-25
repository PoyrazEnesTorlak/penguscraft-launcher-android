package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Ekranin ust ortasindaki Pengu menu sekmesi: koyu, yesil kenarli, uc cizgili. */
public class DrawerPullButton extends View {
    public DrawerPullButton(Context context) {super(context); init();}
    public DrawerPullButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    private final Paint mArka = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mKenar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCizgi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mAlan = new RectF();

    private void init(){
        setAlpha(0.85f);
        mArka.setColor(0xE60B0F16);
        mKenar.setColor(0xFF5CC23F);
        mKenar.setStyle(Paint.Style.STROKE);
        mKenar.setStrokeWidth(1.5f * getResources().getDisplayMetrics().density);
        mCizgi.setColor(0xFF5CC23F);
        mCizgi.setStrokeCap(Paint.Cap.ROUND);
        mCizgi.setStrokeWidth(2.5f * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float r = h * 0.55f;
        float k = mKenar.getStrokeWidth() / 2f;
        // Ust kenar ekranin disina tasiyor, boylece sadece alt koseler yuvarlak gorunuyor
        mAlan.set(k, -r, w - k, h - k);
        canvas.drawRoundRect(mAlan, r, r, mArka);
        canvas.drawRoundRect(mAlan, r, r, mKenar);

        float cx = w / 2f, yarim = h * 0.42f;
        float bosluk = h * 0.2f, orta = h * 0.45f;
        for (int i = -1; i <= 1; i++) {
            float y = orta + i * bosluk;
            canvas.drawLine(cx - yarim, y, cx + yarim, y, mCizgi);
        }
    }
}
