package com.alan.moviebox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

// 首页（方案 D · 影院暗调）：整屏背景 + 左侧大标题和按钮 + 底部带数字的海报栏。
// 焦点移到哪部片，上面的标题、简介和背景颜色就跟着换。
public class MainActivity extends Activity {
    static final int C_TEXT = 0xFFF5F1EA;
    static final int C_MUTED = 0xFFB9B4AA;
    static final int C_DARK = 0xFF08090B;

    static final String[] TABS = {"主页", "电影", "剧集", "综艺", "YouTube", "搜索"};
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

    SourceStore store;
    String loadedApi;                                    // 当前首页数据对应的接口；null 表示还没选接口
    final Map<Integer, List<Movie>> cache = new HashMap<>();  // 每个标签读到的片子
    final Set<Integer> loadingTabs = new HashSet<>();
    static final String CACHE_PREFS = "moviebox_home_cache_v2";
    volatile List<VodApi.Category> cats;                 // 接口的分类列表（用来找「电影」「剧集」「综艺」）

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
        store = new SourceStore(this);
        loadedApi = store.getActiveApi();

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

    // 从设置页回来时，如果换了接口，就清掉旧数据重新读
    @Override
    protected void onResume() {
        super.onResume();
        String api = store.getActiveApi();
        if (!TextUtils.equals(api, loadedApi)) {
            loadedApi = api;
            cache.clear();
            loadingTabs.clear();
            cats = null;
            if (curTab >= 0) {
                int t = curTab;
                curTab = -1;
                selectTab(t);
            }
        }
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

    // 打开详情页。autoplay 为 true 时，详情读完后直接开始播放第一集。
    void openDetail(Movie m, boolean autoplay) {
        String api = store.getActiveApi();
        if (api == null) {
            toast("请先在设置里选一个接口");
            return;
        }
        // 有些首页爬虫只返回片名和海报，没有 vod_id；用片名进入搜索页补全真实站点条目。
        if (m.vodId == null || m.vodId.trim().isEmpty()) {
            SearchActivity.start(this, m.title);
            return;
        }
        // 豆瓣热播榜这类条目不是真正的片子，是「拿片名去所有站点搜索」的入口（影视仓也是这样处理）
        if (m.vodId.startsWith("msearch:")) {
            SearchActivity.start(this, m.title);
            return;
        }
        DetailActivity.start(this, api, m, autoplay);
    }

    // 栏目标题：在后面加上当前站点名，免得分不清现在看的是哪个站点的内容
    String labelFor(int tab) {
        String name = store.getActiveName();
        return name.isEmpty() ? SHELF_LABELS[tab] : SHELF_LABELS[tab] + "  ·  " + name;
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
                if (n == DATA_TABS) startActivity(new Intent(this, YoutubeActivity.class));
            else if (n == DATA_TABS + 1) SearchActivity.start(this, "");
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
        loadTab(n);
        paintTabs();
    }

    // 给当前标签准备数据：没选接口就提示去设置，选了接口就联网读（读过的有缓存）。不再用示例数据顶替。
    void loadTab(final int n) {
        final String api = store.getActiveApi();
        if (api == null) {
            applyEmpty("还没有选接口", "请先到右上角「设置」，扫码添加并选择一个接口。");
            return;
        }
        List<Movie> hit = cache.get(n);
        if (hit != null) {
            applyList(hit, labelFor(n));
            return;
        }
        // Restore cached category metadata before requesting a category page.
        if (n != 0 && cats == null) readDiskCache(api, 0);
        // First paint a saved result immediately, then refresh it in the background.
        List<Movie> saved = readDiskCache(api, n);
        if (saved != null && !saved.isEmpty()) {
            cache.put(n, saved);
            applyList(saved, labelFor(n) + "  ·  正在更新");
        } else {
            applyList(new ArrayList<Movie>(), "加载中…");
        }
        if (loadingTabs.contains(n)) return;
        loadingTabs.add(n);

        Net.POOL.execute(() -> {
            List<Movie> movies;
            String emptyMsg = null;
            String error = null;
            try {
                SiteSpec spec = SiteSpec.parse(api);
                Context app = getApplicationContext();
                VodApi.Page page;
                if (n == 0) {
                    page = Catalog.home(app, spec);
                    cats = page.categories;
                } else {
                    if (cats == null) cats = Catalog.home(app, spec).categories;
                    String typeId = VodApi.matchCategory(cats, n);
                    if (typeId == null) {
                        page = new VodApi.Page();
                        emptyMsg = "这个接口里没有「" + TABS[n] + "」分类";
                    } else {
                        page = Catalog.category(app, spec, typeId);
                    }
                }
                movies = page.movies;
                if (movies.isEmpty() && emptyMsg == null) emptyMsg = "这个分类里暂时没有内容";
            } catch (Throwable e) {
                // 爬虫 jar 出问题时常常是 Error（比如缺类），所以这里连 Error 一起接住，把原因显示出来
                movies = null;
                error = (e instanceof Exception && e.getMessage() != null) ? e.getMessage() : e.toString();
            }

            final List<Movie> result = movies;
            final String msg = emptyMsg;
            final String err = error;
            Net.MAIN.post(() -> {
                loadingTabs.remove(n);
                if (!api.equals(store.getActiveApi())) return;  // 读的过程中换了接口，这批数据作废
                if (result == null) {
                    List<Movie> old = cache.get(n);
                    if (old != null && !old.isEmpty()) {
                        if (curTab == n) applyList(old, labelFor(n) + "  ·  更新失败，显示缓存");
                    } else if (curTab == n) {
                        applyEmpty("加载失败", "这个接口读不出内容，可以到「设置」换一个接口再试。");
                    }
                    if (!isFinishing() && (old == null || old.isEmpty())) {
                        // 用对话框显示，方便截图发给我排查
                        new AlertDialog.Builder(this).setTitle("加载失败").setMessage(err)
                                .setPositiveButton("知道了", null).show();
                    }
                    return;
                }
                cache.put(n, result);
                writeDiskCache(api, n, result);
                if (curTab == n) applyList(result, msg != null ? msg : labelFor(n));
            });
        });
    }

    String diskKey(String api, int tab) {
        return "home_" + Integer.toHexString(api.hashCode()) + "_" + tab;
    }

    List<Movie> readDiskCache(String api, int tab) {
        try {
            SharedPreferences prefs = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE);
            String raw = prefs.getString(diskKey(api, tab), null);
            if (raw == null) return null;
            JSONObject root = new JSONObject(raw);
            if (!api.equals(root.optString("api"))) return null;
            if (tab == 0) {
                JSONArray savedCats = root.optJSONArray("categories");
                if (savedCats != null) {
                    List<VodApi.Category> restored = new ArrayList<>();
                    for (int i = 0; i < savedCats.length(); i++) {
                        JSONObject c = savedCats.optJSONObject(i);
                        if (c != null) restored.add(new VodApi.Category(c.optString("id", ""), c.optString("name", "")));
                    }
                    cats = restored;
                }
            }
            JSONArray arr = root.optJSONArray("movies");
            if (arr == null) return null;
            List<Movie> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new Movie(o.optString("title", ""), o.optString("rating", ""),
                        o.optString("tag", ""), o.optInt("colorIdx", 0),
                        o.optString("meta", ""), o.optString("desc", ""),
                        nullableString(o, "poster"), o.optString("vodId", ""),
                        o.optString("playUrl", "")));
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    void writeDiskCache(String api, int tab, List<Movie> movies) {
        try {
            JSONArray arr = new JSONArray();
            for (Movie m : movies) {
                JSONObject o = new JSONObject();
                o.put("title", m.title); o.put("rating", m.rating); o.put("tag", m.tag);
                o.put("colorIdx", m.colorIdx); o.put("meta", m.meta); o.put("desc", m.desc);
                o.put("poster", m.poster == null ? JSONObject.NULL : m.poster);
                o.put("vodId", m.vodId); o.put("playUrl", m.playUrl);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("api", api); root.put("savedAt", System.currentTimeMillis()); root.put("movies", arr);
            if (tab == 0 && cats != null) {
                JSONArray catArr = new JSONArray();
                for (VodApi.Category c : cats) {
                    JSONObject item = new JSONObject();
                    item.put("id", c.id); item.put("name", c.name); catArr.put(item);
                }
                root.put("categories", catArr);
            }
            getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit()
                    .putString(diskKey(api, tab), root.toString()).apply();
        } catch (Throwable ignored) { }
    }

    String nullableString(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, "");
    }

    // 没有内容可显示时：大标题位置放一句短话，简介位置放说明
    void applyEmpty(String title, String hint) {
        applyList(new ArrayList<Movie>(), title);
        heroDesc.setText(hint);
    }

    // 把一批片子放到海报栏里；空的时候把说明文字显示在大标题的位置
    void applyList(List<Movie> list, String label) {
        rebuildShelf(list, label);
        if (!list.isEmpty()) {
            showMovie(list.get(0));
            return;
        }
        current = null;
        backdrop.setTheme(SampleData.theme(0));
        heroTitle.setText(label);
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, 44 * u);
        heroMeta.setText("");
        heroDesc.setText("");
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
            if (current != null) openDetail(current, primary);  // 「播放」进去后直接开始播
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
        heroMeta.setText(m.rating.isEmpty() ? m.meta : m.rating + "   ·   " + m.meta);
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

    void rebuildShelf(List<Movie> list, String label) {
        shelfRow.removeAllViews();
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
            pv.setOnClickListener(v -> openDetail(m, false));
            item.addView(pv, new LinearLayout.LayoutParams(px(112), px(168)));

            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
            ip.rightMargin = px(40);
            shelfRow.addView(item, ip);
        }
        shelf.scrollTo(0, 0);
        shelfLabel.setText(label);
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
