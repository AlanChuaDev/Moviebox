package com.alan.moviebox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

// 海报旁边的大号描边数字（只有轮廓，没有填充）
public class OutlineNumberView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String s;
    private final float u;

    public OutlineNumberView(Context c, int n, float u) {
        super(c);
        this.s = String.valueOf(n);
        this.u = u;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * u);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFF6B6F78);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(120 * u);
        p.setTextAlign(Paint.Align.RIGHT);
    }

    // 这个数字需要的宽度（一位数窄，两位数宽）
    public int neededWidth() {
        return (int) (p.measureText(s) + 8 * u);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawText(s, getWidth() - 4 * u, getHeight() - 4 * u, p);
    }
}
