package com.alan.moviebox;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

// 详情页、播放器共用的基类：按屏幕宽度换算尺寸（设计尺寸 1280 宽），带一些画界面用的小工具。
public abstract class UiActivity extends Activity {
    static final int C_ACCENT = 0xFFF0A35C;

    protected float u;
    protected final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        u = getResources().getDisplayMetrics().widthPixels / 1280f;
    }

    protected int px(float v) {
        return Math.round(v * u);
    }

    protected TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // 圆角底色；focused 时加一圈强调色的边
    protected GradientDrawable box(int color, float radius, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        if (focused) g.setStroke(px(3), C_ACCENT);
        return g;
    }

    // 在屏幕上放一个东西：left、top 是离左上角的距离，w、hh 小于 0 时表示 -1 铺满 / -2 自适应
    protected FrameLayout.LayoutParams at(float left, float top, float w, float hh) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                w < 0 ? (int) w : px(w), hh < 0 ? (int) hh : px(hh));
        p.gravity = Gravity.TOP | Gravity.START;
        p.leftMargin = px(left);
        p.topMargin = px(top);
        return p;
    }

    protected LinearLayout.LayoutParams lp(int w, int hh, float left, float top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh);
        p.leftMargin = px(left);
        p.topMargin = px(top);
        return p;
    }

    protected void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // 把可能出错的原因整理成能显示给人看的文字（Error 类的异常没有中文说明，就显示类名）
    protected static String errText(Throwable e) {
        return (e instanceof Exception && e.getMessage() != null) ? e.getMessage() : e.toString();
    }
}
