package com.alan.moviebox;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

// 整屏背景：暗底 + 光晕 + 太阳 + 两层山 + 左侧和底部的暗色遮罩。
// 没有真剧照的时候用它撑场面；换片时颜色会平滑过渡。
public class BackdropView extends View {
    static final int BG = 0xFF08090B;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path far = new Path();
    private final Path near = new Path();
    private final ArgbEvaluator ev = new ArgbEvaluator();
    private int sun = 0xFFF0A35C;
    private int mFar = 0xFF12262D;
    private int mNear = 0xFF0B171B;
    private ValueAnimator anim;

    public BackdropView(Context c) {
        super(c);
    }

    // t 是 SampleData.THEMES 里的一行，用到后三个颜色
    public void setTheme(int[] t) {
        final int fs = sun, ff = mFar, fn = mNear;
        final int ts = t[2], tf = t[3], tn = t[4];
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(350);
        anim.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            sun = ((Integer) ev.evaluate(f, fs, ts)).intValue();
            mFar = ((Integer) ev.evaluate(f, ff, tf)).intValue();
            mNear = ((Integer) ev.evaluate(f, fn, tn)).intValue();
            invalidate();
        });
        anim.start();
    }

    // 在 (x0,y0) 开始、大小 bw×bh 的方框里，按比例点画多边形（坐标按 1280×720 设计）
    private void poly(Path path, float sx, float sy, float x0, float y0, float bw, float bh, float[] pts) {
        path.reset();
        for (int i = 0; i < pts.length; i += 2) {
            float x = (x0 + pts[i] * bw) * sx;
            float y = (y0 + pts[i + 1] * bh) * sy;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        float sx = w / 1280f;
        float sy = h / 720f;
        c.drawColor(BG);
        p.setStyle(Paint.Style.FILL);

        // 大光晕
        p.setShader(new RadialGradient(0.8f * w, 0.35f * h, 0.6f * w,
                (sun & 0x00FFFFFF) | 0x52000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // 太阳和它的光
        float cx = 975 * sx;
        float cy = 215 * sy;
        float r = 95 * sx;
        p.setShader(new RadialGradient(cx, cy, r * 2.6f,
                (sun & 0x00FFFFFF) | 0x8C000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 2.6f, p);
        p.setShader(null);
        p.setColor(sun);
        c.drawCircle(cx, cy, r, p);

        // 两层山
        poly(far, sx, sy, 520, 230, 760, 310,
                new float[]{0, 1, 0.22f, 0.38f, 0.42f, 0.72f, 0.68f, 0.20f, 1, 0.70f, 1, 1});
        p.setColor(mFar);
        c.drawPath(far, p);
        poly(near, sx, sy, 520, 330, 760, 210,
                new float[]{0, 1, 0.18f, 0.50f, 0.40f, 0.80f, 0.70f, 0.45f, 1, 0.85f, 1, 1});
        p.setColor(mNear);
        c.drawPath(near, p);

        // 左侧遮罩：让左边的文字清楚
        p.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0xFF08090B, 0xEB08090B, 0x0008090B},
                new float[]{0f, 0.30f, 0.70f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // 底部遮罩：让下面的海报和标题清楚
        p.setShader(new LinearGradient(0, 430 * sy, 0, 590 * sy,
                0x0008090B, 0xFF08090B, Shader.TileMode.CLAMP));
        c.drawRect(0, 430 * sy, w, h, p);
        p.setShader(null);
    }
}
