package com.alan.moviebox;

import android.app.Activity;
import android.graphics.Color;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// 第 1 阶段：首页界面（顶部标题和时钟、分类标签、功能按钮、海报墙），遥控器可以上下左右选
public class MainActivity extends Activity {
    static final int C_BG = 0xFF0B0B0B;
    static final int C_ACCENT = 0xFF1D8FB8;
    static final int C_CHIP = 0xFF1F1F1F;
    static final int C_FOCUS = 0xFF3A3A3A;
    static final int COLS = 5;

    static final String[] TABS = {"主页", "热门电影", "热播剧集", "热播综艺"};
    static final String[] CHIPS = {"历史", "直播", "搜索", "配置", "线路", "收藏", "设置"};

    final Handler h = new Handler(Looper.getMainLooper());
    final SimpleDateFormat fmt = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA);

    TextView clock;
    ScrollView scroll;
    LinearLayout grid;
    final TextView[] tabViews = new TextView[TABS.length];
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setPadding(dp(32), dp(16), dp(32), 0);
        setContentView(root);

        root.addView(buildHeader());
        root.addView(buildTabs());
        root.addView(buildChips());

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(6), 0, dp(24));
        scroll.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        selectTab(0);
        tabViews[0].requestFocus();
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

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    // ---------- 顶部：圆形标志 + 标题 + 时钟 ----------

    View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View logo = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFFFB300);
        logo.setBackground(g);
        row.addView(logo, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView title = text("MovieBox  |  热播", 26, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = dp(14);
        row.addView(title, tp);

        clock = text(fmt.format(new Date()), 22, 0xFFFFFFFF, true);
        row.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    // ---------- 分类标签：选中（或焦点移上去）就切换下面的海报墙 ----------

    View buildTabs() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(4));
        for (int i = 0; i < TABS.length; i++) {
            final int n = i;
            TextView t = text(TABS[i], 20, 0xFFBBBBBB, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(18), dp(8), dp(18), dp(8));
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                if (has) selectTab(n);
                else paintTabs();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(t, lp);
            tabViews[i] = t;
        }
        return row;
    }

    void selectTab(int n) {
        if (n != curTab) {
            curTab = n;
            rebuildGrid();
        }
        paintTabs();
    }

    // 选中 = 蓝底白字，焦点 = 灰底白字，其余 = 灰字
    void paintTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            TextView t = tabViews[i];
            boolean sel = i == curTab;
            boolean foc = t.isFocused();
            t.setBackground(round(sel ? C_ACCENT : (foc ? C_FOCUS : 0x00000000), 24));
            t.setTextColor(sel || foc ? 0xFFFFFFFF : 0xFFBBBBBB);
        }
    }

    // ---------- 功能按钮：历史、直播、搜索……（下个阶段再接上功能） ----------

    View buildChips() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        for (int i = 0; i < CHIPS.length; i++) {
            final String name = CHIPS[i];
            final TextView t = text(name, 18, 0xFFFFFFFF, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(22), dp(10), dp(22), dp(10));
            t.setFocusable(true);
            t.setClickable(true);
            t.setBackground(round(C_CHIP, 12));
            t.setOnFocusChangeListener((v, has) ->
                t.setBackground(round(has ? C_ACCENT : C_CHIP, 12)));
            t.setOnClickListener(v ->
                Toast.makeText(this, "「" + name + "」功能后面的阶段再做", Toast.LENGTH_SHORT).show());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(10);
            row.addView(t, lp);
        }
        return row;
    }

    // ---------- 海报墙：每行 COLS 张，一行一行往下排 ----------

    void rebuildGrid() {
        grid.removeAllViews();
        List<Movie> list = SampleData.forTab(curTab);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int margin = dp(6);
        int cardW = (screenW - dp(32) * 2 - margin * 2 * COLS) / COLS;
        int cardH = cardW * 7 / 5;

        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.setMargins(margin, margin, margin, margin);
            row.addView(buildCard(list.get(i)), lp);
        }
        scroll.scrollTo(0, 0);
    }

    // 一张海报：渐变底色 + 大字 + 角标 + 评分 + 标题；焦点时放大并画白框
    View buildCard(final Movie m) {
        final FrameLayout f = new FrameLayout(this);
        f.setFocusable(true);
        f.setClickable(true);

        int[] c = SampleData.COLORS[m.colorIdx % SampleData.COLORS.length];
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{c[0], c[1]});
        bg.setCornerRadius(dp(10));
        f.setBackground(bg);

        // 标题第一个字，当作海报的大图案
        TextView big = text(m.title.substring(0, 1), 72, 0x33FFFFFF, true);
        big.setGravity(Gravity.CENTER);
        f.addView(big, new FrameLayout.LayoutParams(-1, -1));

        // 左上角角标
        TextView tag = text(m.tag, 13, 0xFFFFFFFF, true);
        tag.setBackground(round(0x99000000, 6));
        tag.setPadding(dp(8), dp(2), dp(8), dp(2));
        FrameLayout.LayoutParams tagLp =
                new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tagLp.setMargins(dp(8), dp(8), 0, 0);
        f.addView(tag, tagLp);

        // 左下角评分
        TextView rating = text(m.rating, 15, 0xFFFFFFFF, true);
        rating.setBackground(round(0x99000000, 6));
        rating.setPadding(dp(8), dp(2), dp(8), dp(2));
        FrameLayout.LayoutParams ratingLp =
                new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        ratingLp.setMargins(dp(8), 0, 0, dp(44));
        f.addView(rating, ratingLp);

        // 底部标题条（上透明、下深色的渐变，只圆下面两个角）
        TextView title = text(m.title, 17, 0xFFFFFFFF, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable shade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xCC000000});
        float r = dp(10);
        shade.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        title.setBackground(shade);
        f.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // 焦点效果：白色边框 + 放大一点
        final GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), 0xFFFFFFFF);
        f.setOnFocusChangeListener((v, has) -> {
            f.setForeground(has ? ring : null);
            float s = has ? 1.06f : 1f;
            f.animate().scaleX(s).scaleY(s).setDuration(120).start();
        });

        f.setOnClickListener(v -> Toast.makeText(this,
                "下个阶段会在这里打开「" + m.title + "」的详情页和播放器",
                Toast.LENGTH_SHORT).show());
        return f;
    }

    // ---------- 返回键：在海报墙里先回到顶部标签，再按一次（2 秒内）才退出 ----------

    @Override
    public void onBackPressed() {
        View focus = getCurrentFocus();
        boolean inTabs = false;
        for (TextView t : tabViews) {
            if (t == focus) inTabs = true;
        }
        if (focus != null && !inTabs) {
            scroll.smoothScrollTo(0, 0);
            tabViews[curTab].requestFocus();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
        }
    }
}
