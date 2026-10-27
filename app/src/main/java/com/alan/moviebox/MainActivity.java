package com.alan.moviebox;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// 首页（方案 D · 影院暗调）：整屏背景 + 左侧大标题和按钮 + 底部带数字的海报栏。
// 焦点移到哪部片，上面的标题、简介和背景颜色就跟着换。
public class MainActivity extends Activity {
    static final int C_TEXT = 0xFFF5F1EA;
    static final int C_MUTED = 0xFFB9B4AA;
    static final int C_DARK = 0xFF08090B;

    static final String[] TABS = {"主页", "电影", "剧集", "综艺", "搜索"};
    static final int DATA_TABS = 4;  // 前 4 个标签有数据，「搜索」只是按钮
    static final String[] SHELF_LABELS = {"热播推荐", "热门电影 TOP 10", "热播剧集 TOP 10", "热播综艺 TOP 10"};

    final Handler h = new Handler(Looper.getMainLooper());
    final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.CHINA);

    float u;  // 缩放比例：屏幕宽度 ÷ 1280，让所有尺寸在不同分辨率的电视上看起来一样
    FrameLayout root;
    BackdropView backdrop;
    TextView clock, settingsBtn, heroTag, heroTitle, heroMeta, heroDesc, btnPlay, btnInfo, shelfLabel;
    final TextView[] tabViews = new TextView[TABS.length];
    LinearLayout shelfRow;
    HorizontalScrollView shelf;
    Movie current;
    int curTab = -1;
    long lastBack = 0;

    // 每秒刷新一次时钟
    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clock.setText(fmt.format(new Date()));
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        u = getResources().getDisplayMetrics().widthPixels / 1280f;

        root = new FrameLayout(this);
        root.setBackgroundColor(C_DARK);
        backdrop = new BackdropView(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        buildHeader();
        buildHero();
        buildShelf();
        setContentView(root);

        selectTab(0);
        btnPlay.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        h.removeCallbacks(tick);
    }

    // ---------- 小工具 ----------

    int px(float v) {
        return Math.round(v * u);
    }

    TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        return g;
    }

    // 在屏幕上放一个东西：left、top 是离左上角的距离，w、h 小于 0 时表示 -1 铺满 / -2 自适应
    FrameLayout.LayoutParams at(float left, float top, float w, float hh) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                w < 0 ? (int) w : px(w), hh < 0 ? (int) hh : px(hh));
        p.gravity = Gravity.TOP | Gravity.START;
        p.leftMargin = px(left);
        p.topMargin = px(top);
        return p;
    }

    LinearLayout.LayoutParams lp(int w, int hh, float left, float top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh);
        p.leftMargin = px(left);
        p.topMargin = px(top);
        return p;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- 顶部：MOVIEBOX + 标签 + 时钟 ----------

    void buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar, at(64, 28, 1152, -2));

        TextView brand = tv("MOVIEBOX", 18, C_TEXT, true);
        brand.setLetterSpacing(0.33f);
        bar.addView(brand, lp(-2, -2, 0, 0));

        for (int i = 0; i < TABS.length; i++) {
            final int n = i;
            TextView t = tv(TABS[i], 18, C_MUTED, false);
            t.setPadding(px(14), px(8), px(14), px(8));
            t.setFocusable(true);
            t.setClickable(true);
            t.setOnFocusChangeListener((v, has) -> {
                if (has && n < DATA_TABS) selectTab(n);
                paintTabs();
            });
            t.setOnClickListener(v -> {
                if (n == DATA_TABS) toast("「搜索」功能后面的阶段再做");
            });
            bar.addView(t, lp(-2, -2, i == 0 ? 28 : 0, 0));
            tabViews[i] = t;
        }

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // 「设置」：进入设置页，扫码添加接口
        settingsBtn = tv("设置", 18, C_MUTED, false);
        settingsBtn.setPadding(px(14), px(8), px(14), px(8));
        settingsBtn.setFocusable(true);
        settingsBtn.setClickable(true);
        settingsBtn.setOnFocusChangeListener((v, has) -> {
            settingsBtn.setBackground(has ? round(0x29FFFFFF, 20) : null);
            settingsBtn.setTextColor(has ? C_TEXT : C_MUTED);
        });
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(settingsBtn, lp(-2, -2, 0, 0));

        clock = tv(fmt.format(new Date()), 18, C_MUTED, false);
        bar.addView(clock, lp(-2, -2, 20, 0));
    }

    void selectTab(int n) {
        if (n == curTab) {
            paintTabs();
            return;
        }
        curTab = n;
        rebuildShelf();
        showMovie(SampleData.forTab(n).get(0));
        paintTabs();
    }

    // 选中的标签字是亮的、加粗；焦点所在的标签有一个浅色的胶囊底
    void paintTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            TextView t = tabViews[i];
            boolean sel = i == curTab;
            boolean foc = t.isFocused();
            t.setBackground(foc ? round(0x29FFFFFF, 20) : null);
            t.setTextColor(sel || foc ? C_TEXT : C_MUTED);
            t.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    // ---------- 左侧：标签 + 大标题 + 信息 + 简介 + 两个按钮 ----------

    void buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        root.addView(hero, at(64, 150, 560, -2));

        heroTag = tv("热播推荐", 14, 0xFFF0A35C, true);
        heroTag.setLetterSpacing(0.2f);
        heroTag.setPadding(px(12), px(4), px(12), px(4));
        hero.addView(heroTag, lp(-2, -2, 0, 0));

        heroTitle = tv("", 92, C_TEXT, true);
        heroTitle.setLetterSpacing(0.06f);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroTitle, lp(-1, -2, 0, 12));

        heroMeta = tv("", 19, C_MUTED, false);
        hero.addView(heroMeta, lp(-2, -2, 0, 10));

        heroDesc = tv("", 20, 0xFFD8D3C8, false);
        heroDesc.setLineSpacing(0, 1.35f);
        heroDesc.setMaxLines(3);
        heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroDesc, lp(-1, -2, 0, 12));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        hero.addView(btns, lp(-2, -2, 0, 26));
        btnPlay = button("播放", true);
        btnInfo = button("详情", false);
        btns.addView(btnPlay, lp(-2, -2, 0, 0));
        btns.addView(btnInfo, lp(-2, -2, 14, 0));
    }

    TextView button(String label, final boolean primary) {
        final TextView b = tv(label, 22, primary ? C_DARK : C_TEXT, primary);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(primary ? 40 : 32), px(14), px(primary ? 40 : 32), px(14));
        b.setFocusable(true);
        b.setClickable(true);
        styleButton(b, primary, false);
        b.setOnFocusChangeListener((v, has) -> {
            styleButton(b, primary, has);
            float s = has ? 1.05f : 1f;
            b.animate().scaleX(s).scaleY(s).setDuration(120).start();
        });
        b.setOnClickListener(v -> {
            String name = current != null ? current.title : "";
            toast(primary ? "第 2 阶段会在这里播放「" + name + "」"
                    : "第 2 阶段会在这里打开「" + name + "」的详情页");
        });
        return b;
    }

    void styleButton(TextView b, boolean primary, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(8));
        g.setColor(primary ? C_TEXT : (focused ? 0x66F5F1EA : 0x29F5F1EA));
        if (focused) g.setStroke(px(3), 0xFFF0A35C);
        b.setBackground(g);
    }

    // 焦点移到哪部片，就刷新标题、信息、简介、标签色和背景
    void showMovie(Movie m) {
        current = m;
        int[] t = SampleData.theme(m.colorIdx);
        backdrop.setTheme(t);
        heroTitle.setText(m.title);
        int len = m.title.length();
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, (len <= 4 ? 92 : (len <= 6 ? 72 : 56)) * u);
        heroMeta.setText(m.rating + "   ·   " + m.meta);
        heroDesc.setText(m.desc);

        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(px(4));
        g.setStroke(Math.max(1, px(1)), t[2]);
        heroTag.setBackground(g);
        heroTag.setTextColor(t[2]);
    }

    // ---------- 底部：栏目名 + 横向滚动的海报栏（每张前面有大号数字） ----------

    void buildShelf() {
        shelfLabel = tv(SHELF_LABELS[0], 20, C_TEXT, true);
        shelfLabel.setLetterSpacing(0.1f);
        root.addView(shelfLabel, at(64, 464, -2, -2));

        shelf = new HorizontalScrollView(this);
        shelf.setHorizontalScrollBarEnabled(false);
        shelfRow = new LinearLayout(this);
        shelfRow.setOrientation(LinearLayout.HORIZONTAL);
        shelfRow.setGravity(Gravity.BOTTOM);
        shelfRow.setClipChildren(false);
        shelfRow.setClipToPadding(false);
        shelfRow.setPadding(px(56), px(20), px(64), px(20));
        shelf.addView(shelfRow, new FrameLayout.LayoutParams(-2, -2));
        root.addView(shelf, at(0, 492, -1, 208));
    }

    void rebuildShelf() {
        shelfRow.removeAllViews();
        List<Movie> list = SampleData.forTab(curTab);
        for (int i = 0; i < list.size(); i++) {
            final Movie m = list.get(i);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.BOTTOM);
            item.setClipChildren(false);

            OutlineNumberView num = new OutlineNumberView(this, i + 1, u);
            item.addView(num, new LinearLayout.LayoutParams(num.neededWidth(), px(168)));

            PosterView pv = new PosterView(this, m, u);
            pv.setOnFocusChangeListener((v, has) -> {
                if (has) showMovie(m);
            });
            pv.setOnClickListener(v -> toast("第 2 阶段会在这里打开「" + m.title + "」的详情页"));
            item.addView(pv, new LinearLayout.LayoutParams(px(112), px(168)));

            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
            ip.rightMargin = px(40);
            shelfRow.addView(item, ip);
        }
        shelf.scrollTo(0, 0);
        shelfLabel.setText(SHELF_LABELS[curTab]);
    }

    // ---------- 返回键：不在顶部标签时先回到标签，再按一次（2 秒内）才退出 ----------

    @Override
    public void onBackPressed() {
        View focus = getCurrentFocus();
        boolean inTabs = focus == settingsBtn;
        for (TextView t : tabViews) {
            if (t == focus) inTabs = true;
        }
        if (focus != null && !inTabs) {
            tabViews[Math.max(curTab, 0)].requestFocus();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            toast("再按一次返回键退出");
        }
    }
}
