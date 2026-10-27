package com.alan.moviebox;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 搜索页：输入片名（电视上直接输入，或扫码用手机输入），在配置里所有可搜索的站点里一起搜，
// 结果按海报墙显示，点一部进入它所属站点的详情页。
public class SearchActivity extends UiActivity {
    static final int COLS = 5;
    static final int MAX_PER_SITE = 12;
    static final int MAX_TOTAL = 180;
    static final ExecutorService SEARCH_POOL = Executors.newFixedThreadPool(5);

    SourceStore store;
    EditText input;
    TextView statusV, urlText, searchBtn, blockedBtn;
    ImageView qrView;
    LinearLayout grid;
    ConfigServer server;

    int searchToken = 0;
    int totalSites, doneSites, hitCount;
    String curKw = "";
    final Set<String> seen = new HashSet<>();
    List<VodApi.Option> curSites = new ArrayList<>();            // 这一次搜索涉及的站点
    final Set<String> doneSpecs = new HashSet<>();               // 已经返回结果的站点
    final Map<String, Integer> siteHits = new HashMap<>();       // 每个站点显示了几条

    // 从任何页面打开搜索页；keyword 不为空就直接开始搜
    public static void start(Context c, String keyword) {
        Intent in = new Intent(c, SearchActivity.class);
        in.putExtra("keyword", keyword == null ? "" : keyword);
        c.startActivity(in);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        store = new SourceStore(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MainActivity.C_DARK);

        TextView title = tv("搜索", 36, MainActivity.C_TEXT, true);
        title.setLetterSpacing(0.1f);
        root.addView(title, at(64, 36, -2, -2));

        // 左栏：输入框、搜索按钮、状态、二维码
        input = new EditText(this);
        input.setHint("输入片名");
        input.setHintTextColor(MainActivity.C_MUTED);
        input.setTextColor(MainActivity.C_TEXT);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 22 * u);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(px(16), 0, px(16), 0);
        input.setBackground(box(0x29F5F1EA, 8, false));
        input.setOnFocusChangeListener((v, has) -> input.setBackground(box(has ? 0x4DF5F1EA : 0x29F5F1EA, 8, has)));
        input.setOnEditorActionListener((v, actionId, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                hideKeyboard();
                startSearch(input.getText().toString());
                return true;
            }
            return false;
        });
        root.addView(input, at(64, 104, 300, 56));

        searchBtn = button("搜索");
        searchBtn.setOnClickListener(v -> {
            hideKeyboard();
            startSearch(input.getText().toString());
        });
        root.addView(searchBtn, at(64, 176, -2, -2));

        statusV = tv("输入片名后，在配置里所有可搜索的站点中一起搜索。", 15, MainActivity.C_MUTED, false);
        statusV.setLineSpacing(0, 1.3f);
        root.addView(statusV, at(64, 250, 300, -2));

        blockedBtn = tv("", 18, MainActivity.C_TEXT, false);
        blockedBtn.setGravity(Gravity.CENTER);
        blockedBtn.setPadding(px(20), px(8), px(20), px(8));
        blockedBtn.setFocusable(true);
        blockedBtn.setClickable(true);
        blockedBtn.setBackground(box(0x29F5F1EA, 8, false));
        blockedBtn.setOnFocusChangeListener((v, has) -> blockedBtn.setBackground(box(has ? 0x4DF5F1EA : 0x29F5F1EA, 8, has)));
        blockedBtn.setOnClickListener(v -> showBlockedDialog());
        root.addView(blockedBtn, at(64, 346, -2, -2));
        refreshBlockedBtn();

        qrView = new ImageView(this);
        qrView.setBackground(box(0xFFFFFFFF, 10, false));
        qrView.setPadding(px(8), px(8), px(8), px(8));
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(qrView, at(64, 400, 190, 190));

        TextView qrHint = tv("手机扫码，在网页里输入片名", 15, MainActivity.C_MUTED, false);
        root.addView(qrHint, at(64, 604, 300, -2));
        urlText = tv("", 12, MainActivity.C_MUTED, false);
        urlText.setSingleLine(true);
        urlText.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(urlText, at(64, 632, 300, -2));

        // 右栏：结果海报墙
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(px(12), px(14), px(12), px(30));
        scroll.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, at(400, 24, 816, 676));

        setContentView(root);

        String kw = getIntent().getStringExtra("keyword");
        if (kw != null && !kw.trim().isEmpty()) {
            searchBtn.requestFocus();
            startSearch(kw);
        } else {
            input.requestFocus();
        }
    }

    // ---------- 手机扫码输入 ----------

    @Override
    protected void onStart() {
        super.onStart();
        startServer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    void startServer() {
        String token = SettingsActivity.randomToken();
        server = ConfigServer.startNew(this, store, token,
                msg -> { },
                kw -> h.post(() -> startSearch(kw)));
        String ip = ConfigServer.localIp();
        if (server != null && ip != null) {
            String url = "http://" + ip + ":" + server.getListeningPort() + "/?k=" + token;
            Bitmap bmp = SettingsActivity.makeQr(url, px(174));
            if (bmp != null) {
                qrView.setImageBitmap(bmp);
                urlText.setText(url);
                return;
            }
        }
        qrView.setImageDrawable(null);
        urlText.setText(server == null ? "网页服务启动失败" : "没有检测到局域网");
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    // ---------- 搜索 ----------

    void startSearch(String raw) {
        final String kw = raw == null ? "" : raw.trim();
        if (kw.isEmpty()) {
            toast("请输入片名");
            return;
        }
        final int my = ++searchToken;
        curKw = kw;
        if (!kw.equals(input.getText().toString())) {
            input.setText(kw);
            input.setSelection(kw.length());
        }
        grid.removeAllViews();
        hitCount = 0;
        doneSites = 0;
        seen.clear();
        doneSpecs.clear();
        siteHits.clear();

        List<VodApi.Option> sites = sitesToSearch();
        curSites = sites;
        totalSites = sites.size();
        refreshBlockedBtn();
        if (sites.isEmpty()) {
            statusV.setText("还没有选择接口，请先在设置里添加并选择一个接口。");
            return;
        }
        updateStatus();

        final Context app = getApplicationContext();
        for (final VodApi.Option o : sites) {
            SEARCH_POOL.execute(() -> {
                List<Movie> found = null;
                try {
                    found = Catalog.search(app, SiteSpec.parse(o.url), kw).movies;
                } catch (Throwable e) {
                    // 这个站点搜索失败（超时、被拒绝、爬虫报错），跳过，不影响别的站点
                }
                final List<Movie> res = found;
                Net.MAIN.post(() -> onSiteDone(my, o, res));
            });
        }
    }

    // 要搜的站点：当前站点排第一，后面是同一份配置里其他标明可搜索的站点；被屏蔽的站点不搜
    List<VodApi.Option> sitesToSearch() {
        List<VodApi.Option> out = new ArrayList<>();
        String active = store.getActiveApi();
        if (active != null && !store.isBlocked(active)) {
            out.add(new VodApi.Option(store.getActiveName(), active));
        }
        for (VodApi.Option o : store.getPool()) {
            if (o.url.equals(active)) continue;
            if (store.isBlocked(o.url)) continue;
            if (SiteSpec.parse(o.url).searchable) out.add(o);
        }
        return out;
    }

    static String cleanName(String name) {
        return name == null ? "" : name.replace("  [爬虫]", "").trim();
    }

    void onSiteDone(int my, VodApi.Option o, List<Movie> res) {
        if (my != searchToken || isFinishing()) return;
        doneSites++;
        doneSpecs.add(o.url);
        // 搜索途中被屏蔽的站点，结果直接丢掉
        if (res != null && !store.isBlocked(o.url)) {
            List<Movie> keep = new ArrayList<>();
            for (Movie m : res) {
                if (keep.size() >= MAX_PER_SITE || hitCount + keep.size() >= MAX_TOTAL) break;
                if (!seen.add(o.url + "|" + m.vodId + "|" + m.title)) continue;
                keep.add(m);
            }
            if (!keep.isEmpty()) addSection(o, cleanName(o.name), keep);
        }
        updateStatus();
    }

    void updateStatus() {
        String hint = "\n选中站点名按确定键，可以屏蔽这个站点。";
        if (doneSites < totalSites) {
            statusV.setText("正在搜索「" + curKw + "」…\n已搜 " + doneSites + " / " + totalSites
                    + " 个站点，找到 " + hitCount + " 个结果");
        } else if (hitCount == 0) {
            statusV.setText("没有找到「" + curKw + "」的结果（已搜 " + totalSites + " 个站点）。");
        } else {
            statusV.setText("搜索完成：找到 " + hitCount + " 个结果（" + totalSites + " 个站点）。" + hint);
        }
    }

    // 一个站点的结果：一行站点名标题 + 下面一排排海报。每个站点各占一块，不再混在一起。
    void addSection(final VodApi.Option o, final String site, List<Movie> list) {
        TextView head = tv(site + "  ·  " + list.size() + " 个结果", 18, MainActivity.C_TEXT, true);
        head.setPadding(px(10), px(6), px(10), px(6));
        head.setFocusable(true);
        head.setClickable(true);
        head.setTag(o.url);
        head.setOnFocusChangeListener((v, has) -> head.setBackground(has ? box(0x4DF5F1EA, 6, true) : null));
        head.setOnClickListener(v -> confirmBlock(o, site));
        grid.addView(head, lp(-2, -2, 0, grid.getChildCount() == 0 ? 0 : 26));

        LinearLayout row = null;
        int inRow = 0;
        for (final Movie m : list) {
            if (row == null || inRow == COLS) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setTag(o.url);
                grid.addView(row, lp(-2, -2, 0, 12));
                inRow = 0;
            }
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setClipChildren(false);

            PosterView pv = new PosterView(this, m, u * 130f / 112f);
            pv.setOnClickListener(v -> DetailActivity.start(this, o.url, m, false));
            pv.setOnLongClickListener(v -> {
                confirmBlock(o, site);
                return true;
            });
            item.addView(pv, new LinearLayout.LayoutParams(px(130), px(195)));

            row.addView(item, lp(-2, -2, inRow == 0 ? 0 : 24, 0));
            inRow++;
            hitCount++;
        }
        siteHits.put(o.url, list.size());
    }

    // ---------- 屏蔽站点 ----------

    void confirmBlock(final VodApi.Option o, final String site) {
        new AlertDialog.Builder(this)
                .setTitle("屏蔽「" + site + "」？")
                .setMessage("以后搜索不再搜这个站点，也不再显示它的结果。\n想恢复时，在搜索页左边的「已屏蔽」里选回来。")
                .setPositiveButton("屏蔽", (d, w) -> {
                    store.block(site, o.url);
                    removeSection(o.url);
                    refreshBlockedBtn();
                    toast("已屏蔽「" + site + "」");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 把当前结果里这个站点的那一块去掉，并重新计数
    void removeSection(String spec) {
        for (int i = grid.getChildCount() - 1; i >= 0; i--) {
            if (spec.equals(grid.getChildAt(i).getTag())) grid.removeViewAt(i);
        }
        Integer n = siteHits.remove(spec);
        if (n != null) hitCount -= n;
        // 站点数也跟着变
        int total = 0, done = 0;
        for (VodApi.Option o : curSites) {
            if (store.isBlocked(o.url)) continue;
            total++;
            if (doneSpecs.contains(o.url)) done++;
        }
        totalSites = total;
        doneSites = done;
        // 去掉第一块后，新的第一块顶部不留空
        if (grid.getChildCount() > 0) {
            View first = grid.getChildAt(0);
            if (first.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) first.getLayoutParams()).topMargin = 0;
            }
        }
        updateStatus();
    }

    void refreshBlockedBtn() {
        int n = store.getBlocked().size();
        blockedBtn.setText(n == 0 ? "已屏蔽：无" : "已屏蔽 " + n + " 个站点（选中恢复）");
    }

    void showBlockedDialog() {
        final List<VodApi.Option> list = store.getBlocked();
        if (list.isEmpty()) {
            toast("还没有屏蔽任何站点");
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = "恢复：" + cleanName(list.get(i).name);
        new AlertDialog.Builder(this)
                .setTitle("已屏蔽的站点")
                .setItems(names, (d, which) -> {
                    VodApi.Option o = list.get(which);
                    store.unblock(o.url);
                    refreshBlockedBtn();
                    toast("已恢复「" + cleanName(o.name) + "」，下次搜索会重新搜它");
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ---------- 控件 ----------

    TextView button(String label) {
        final TextView b = tv(label, 22, MainActivity.C_DARK, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(40), px(12), px(40), px(12));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(box(MainActivity.C_TEXT, 8, false));
        b.setOnFocusChangeListener((v, has) -> b.setBackground(box(MainActivity.C_TEXT, 8, has)));
        return b;
    }
}
