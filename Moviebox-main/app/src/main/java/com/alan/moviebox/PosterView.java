package com.alan.moviebox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

// 一张海报（112×168 设计尺寸）。没有真图时用几何图案代替；选中时放大并画白框。
// 接了接口之后，这里改成画真实的海报图片就行。
public class PosterView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final Movie movie;
    private final float u;
    private final Rect src = new Rect();
    private Bitmap bmp;  // 真实海报；还没下载好或没有时为 null，用图案代替

    public PosterView(Context c, Movie m, float u) {
        super(c);
        this.movie = m;
        this.u = u;
        setFocusable(true);
        setClickable(true);
        if (m.poster != null) {
            Images.load(m.poster, Math.round(112 * u * 1.3f), b -> {
                bmp = b;
                invalidate();
            });
        }
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect prev) {
        super.onFocusChanged(gain, direction, prev);
        float s = gain ? 1.08f : 1f;
        animate().scaleX(s).scaleY(s).setDuration(130).start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        float r = 8 * u;
        int[] t = SampleData.theme(movie.colorIdx);

        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);

        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(t[0]);
        c.drawRect(rect, p);

        if (bmp != null) {
            // 真实海报：按「铺满并居中裁剪」画进来
            float scale = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
            int sw = Math.min(bmp.getWidth(), Math.round(w / scale));
            int sh = Math.min(bmp.getHeight(), Math.round(h / scale));
            int sx = (bmp.getWidth() - sw) / 2;
            int sy = (bmp.getHeight() - sh) / 2;
            src.set(sx, sy, sx + sw, sy + sh);
            p.setFilterBitmap(true);
            c.drawBitmap(bmp, src, rect, p);
            p.setFilterBitmap(false);
        } else {
            // 没有真图：按编号换五种图案
            p.setColor(t[1]);
            switch (Math.abs(movie.colorIdx) % 5) {
                case 0: c.drawCircle(36 * u, 86 * u, 60 * u, p); break;
                case 1: c.drawCircle(75 * u, 25 * u, 55 * u, p); break;
                case 2: c.drawRect(0, 60 * u, w, 86 * u, p); break;
                case 3: c.drawCircle(75 * u, 65 * u, 45 * u, p); break;
                default: c.drawCircle(40 * u, 90 * u, 50 * u, p); break;
            }
        }

        // 底部暗色渐变，托住标题
        p.setShader(new LinearGradient(0, h - 64 * u, 0, h,
                0x00000000, 0xB8000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h - 64 * u, w, h, p);
        p.setShader(null);

        // 标题（太长就缩小）
        p.setColor(0xFFF5F1EA);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        float size = 14 * u;
        p.setTextSize(size);
        float avail = w - 16 * u;
        float tw = p.measureText(movie.title);
        if (tw > avail) {
            size = size * avail / tw;
            p.setTextSize(size);
        }
        c.drawText(movie.title, 8 * u, h - 9 * u, p);
        c.restore();

        // 选中时的白框
        if (isFocused()) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * u);
            p.setColor(0xFFF5F1EA);
            c.drawRoundRect(1.5f * u, 1.5f * u, w - 1.5f * u, h - 1.5f * u, r, r, p);
            p.setStyle(Paint.Style.FILL);
        }
    }
}
