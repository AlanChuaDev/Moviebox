package com.alan.moviebox;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

// 详情页：左边大海报，右边片名、信息、简介、播放按钮、播放线路和选集。
// 遥控器：上下左右移动，确定键选择；返回键回首页。
public class DetailActivity extends UiActivity {
    static final int EP_COLS = 6;

    String specRaw, vodId, title, poster, meta, desc, rating, fallbackPlay;
    int color;
    boolean autoplay;
    SiteSpec spec;

    TextView metaV, peopleV, descV, statusV, playBtn, lineLabel, epLabel;
    HorizontalScrollView lineScroll;
    LinearLayout lineBar, epBox;
    final List<VodApi.Line> lines = new ArrayList<>();
    final List<TextView> chips = new ArrayList<>();
    int curLine = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        specRaw = ex(in, "spec");
        spec = SiteSpec.parse(specRaw);
        vodId = ex(in, "vodId");
        title = ex(in, "title");
        poster = ex(in, "poster");
        meta = ex(in, "meta");
        desc = ex(in, "desc");
        rating = ex(in, "rating");
        fallbackPlay = ex(in, "playUrl");
        color = in.getIntExtra("color", 0);
        autoplay = in.getBooleanExtra("autoplay", false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MainActivity.C_DARK);
        BackdropView backdrop = new BackdropView(this);
        backdrop.setTheme(SampleData.theme(color));
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        // 左边：把首页的海报放大一倍
        Movie pm = new Movie(title, rating, "", color, meta, desc, poster.isEmpty() ? null : poster, vodId, "");
        PosterView pv = new PosterView(this, pm, u * 2);
        pv.setFocusable(false);
        pv.setClickable(false);
        root.addView(pv, at(64, 100, 224, 336));

        // 右边：一个可以上下滚动的栏
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(8), px(8), px(8), px(40));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, at(320, 56, 896, 640));

        TextView t = tv(title, 52, MainActivity.C_TEXT, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t, lp(-1, -2, 0, 0));

        metaV = tv(joinMeta(rating, meta), 18, MainActivity.C_MUTED, false);
        col.addView(metaV, lp(-1, -2, 0, 10));

        peopleV = tv("", 16, MainActivity.C_MUTED, false);
        peopleV.setLineSpacing(0, 1.3f);
        peopleV.setVisibility(android.view.View.GONE);
        col.addView(peopleV, lp(-1, -2, 0, 8));

        descV = tv(desc, 18, 0xFFD8D3C8, false);
        descV.setLineSpacing(0, 1.35f);
        descV.setMaxLines(5);
        descV.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(descV, lp(-1, -2, 0, 12));

        playBtn = button("播放", () -> {
            if (!lines.isEmpty()) play(curLine, 0);
        });
        col.addView(playBtn, lp(-2, -2, 0, 20));

        statusV = tv("正在读取详情…", 16, C_ACCENT, false);
        col.addView(statusV, lp(-2, -2, 0, 18));

        lineLabel = tv("播放线路", 18, MainActivity.C_TEXT, true);
        lineLabel.setVisibility(android.view.View.GONE);
        col.addView(lineLabel, lp(-2, -2, 0, 22));

        lineScroll = new HorizontalScrollView(this);
        lineScroll.setHorizontalScrollBarEnabled(false);
        lineScroll.setVisibility(android.view.View.GONE);
        lineBar = new LinearLayout(this);
        lineBar.setOrientation(LinearLayout.HORIZONTAL);
        lineBar.setPadding(px(4), px(8), px(4), px(8));
        lineScroll.addView(lineBar, new FrameLayout.LayoutParams(-2, -2));
        col.addView(lineScroll, lp(-1, -2, 0, 4));

        epLabel = tv("选集", 18, MainActivity.C_TEXT, true);
        epLabel.setVisibility(android.view.View.GONE);
        col.addView(epLabel, lp(-2, -2, 0, 14));

        epBox = new LinearLayout(this);
        epBox.setOrientation(LinearLayout.VERTICAL);
        epBox.setPadding(px(4), px(8), px(4), px(8));
        col.addView(epBox, lp(-2, -2, 0, 4));

        setContentView(root);
        playBtn.requestFocus();
        load();
    }

    String ex(Intent in, String key) {
        String s = in.getStringExtra(key);
        return s == null ? "" : s;
    }

    String joinMeta(String rating, String meta) {
        if (rating.isEmpty()) return meta;
        if (meta.isEmpty()) return rating;
        return rating + "   ·   " + meta;
    }

    // ---------- 读详情 ----------

    void load() {
        Net.POOL.execute(() -> {
            VodApi.Detail d = null;
            String err = null;
            try {
                d = Catalog.detail(getApplicationContext(), spec, vodId);
            } catch (Throwable e) {
                err = errText(e);
            }
            // 读详情失败，但首页的列表里已经带着播放地址（type 1 常见），就用它
            if ((d == null || d.lines.isEmpty()) && !fallbackPlay.isEmpty()) {
                List<VodApi.Line> fb = VodApi.parseLines("", fallbackPlay);
                if (!fb.isEmpty()) {
                    if (d == null) d = new VodApi.Detail();
                    d.lines.clear();
                    d.lines.addAll(fb);
                }
            }
            final VodApi.Detail result = d;
            final String msg = err;
            Net.MAIN.post(() -> onLoaded(result, msg));
        });
    }

    void onLoaded(VodApi.Detail d, String err) {
        if (isFinishing()) return;
        if (d == null) {
            statusV.setText("读取详情失败：" + err);
            return;
        }
        if (!d.meta.isEmpty()) metaV.setText(joinMeta(rating, d.meta));
        if (!d.desc.isEmpty()) descV.setText(d.desc);
        StringBuilder people = new StringBuilder();
        if (!d.director.isEmpty()) people.append("导演：").append(d.director);
        if (!d.actor.isEmpty()) {
            if (people.length() > 0) people.append('\n');
            people.append("主演：").append(d.actor);
        }
        if (people.length() > 0) {
            peopleV.setText(people.toString());
            peopleV.setMaxLines(3);
            peopleV.setEllipsize(TextUtils.TruncateAt.END);
            peopleV.setVisibility(android.view.View.VISIBLE);
        }

        lines.clear();
        lines.addAll(d.lines);
        if (lines.isEmpty()) {
            statusV.setText(err != null ? "没有可播放的内容（" + err + "）" : "没有可播放的内容");
            return;
        }
        statusV.setText("");
        statusV.setVisibility(android.view.View.GONE);
        curLine = 0;
        showLines();

        if (autoplay) {
            autoplay = false;
            play(0, 0);
        }
    }

    // ---------- 线路和选集 ----------

    void showLines() {
        lineBar.removeAllViews();
        chips.clear();
        boolean multi = lines.size() > 1;
        lineLabel.setVisibility(multi ? android.view.View.VISIBLE : android.view.View.GONE);
        lineScroll.setVisibility(multi ? android.view.View.VISIBLE : android.view.View.GONE);
        if (multi) {
            for (int i = 0; i < lines.size(); i++) {
                final int li = i;
                final TextView c = chip(lines.get(i).name);
                c.setOnFocusChangeListener((v, has) -> styleChip(c, li == curLine, has));
                c.setOnClickListener(v -> {
                    curLine = li;
                    paintChips();
                    showEpisodes();
                });
                chips.add(c);
                lineBar.addView(c, lp(-2, -2, i == 0 ? 0 : 10, 0));
            }
            paintChips();
        }
        showEpisodes();
    }

    void paintChips() {
        for (int i = 0; i < chips.size(); i++) {
            TextView c = chips.get(i);
            styleChip(c, i == curLine, c.isFocused());
        }
    }

    void showEpisodes() {
        epBox.removeAllViews();
        epLabel.setVisibility(android.view.View.VISIBLE);
        List<VodApi.Ep> eps = lines.get(curLine).eps;
        epLabel.setText("选集（共 " + eps.size() + " 集）");
        LinearLayout row = null;
        for (int k = 0; k < eps.size(); k++) {
            if (k % EP_COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                epBox.addView(row, lp(-2, -2, 0, k == 0 ? 0 : 10));
            }
            final int ei = k;
            final TextView b = chip(eps.get(k).name);
            b.setOnFocusChangeListener((v, has) -> styleChip(b, false, has));
            b.setOnClickListener(v -> play(curLine, ei));
            row.addView(b, lp(px(132), -2, k % EP_COLS == 0 ? 0 : 10, 0));
        }
    }

    // 去播放页：把当前线路的所有集一起带过去，播放页里才能换集、自动下一集
    void play(int li, int ei) {
        VodApi.Line ln = lines.get(li);
        String[] names = new String[ln.eps.size()];
        String[] ids = new String[ln.eps.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = ln.eps.get(i).name;
            ids[i] = ln.eps.get(i).url;
        }
        Intent in = new Intent(this, PlayerActivity.class);
        in.putExtra("spec", specRaw);
        in.putExtra("title", title);
        in.putExtra("flag", ln.name);
        in.putExtra("names", names);
        in.putExtra("ids", ids);
        in.putExtra("index", ei);
        startActivity(in);
    }

    // ---------- 控件样式 ----------

    TextView button(String label, final Runnable onClick) {
        final TextView b = tv(label, 22, MainActivity.C_DARK, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(48), px(14), px(48), px(14));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(box(MainActivity.C_TEXT, 8, false));
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(box(MainActivity.C_TEXT, 8, has));
            float s = has ? 1.05f : 1f;
            b.animate().scaleX(s).scaleY(s).setDuration(120).start();
        });
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    TextView chip(String text) {
        TextView t = tv(text, 18, MainActivity.C_TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(px(14), px(10), px(14), px(10));
        t.setFocusable(true);
        t.setClickable(true);
        styleChip(t, false, false);
        return t;
    }

    void styleChip(TextView t, boolean selected, boolean focused) {
        GradientDrawable g = box(selected ? 0x66F0A35C : (focused ? 0x4DF5F1EA : 0x29F5F1EA), 8, focused);
        t.setBackground(g);
    }
}
