package com.alan.moviebox;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 设置页：左边是二维码，手机扫码后在网页里添加接口；右边是已添加的接口列表。
// 遥控器：上下选接口，按确定键删除（要连按两次确认）；返回键回首页。
public class SettingsActivity extends Activity {
    static final int C_ACCENT = 0xFFF0A35C;

    final Handler h = new Handler(Looper.getMainLooper());
    float u;
    FrameLayout root;
    SourceStore store;
    ConfigServer server;
    String token = "";

    ImageView qrView;
    TextView urlText, countLabel, regenBtn;
    LinearLayout listBox;
    final List<LinearLayout> rows = new ArrayList<>();
    final List<TextView> hints = new ArrayList<>();
    String pendingUrl = null;  // 刚按过一次确定键、等待第二次确认的接口
    long pendingAt = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        u = getResources().getDisplayMetrics().widthPixels / 1280f;
        store = new SourceStore(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(MainActivity.C_DARK);

        TextView title = tv("设置", 36, MainActivity.C_TEXT, true);
        title.setLetterSpacing(0.1f);
        root.addView(title, at(64, 36, -2, -2));
        buildLeft();
        buildRight();

        setContentView(root);
        refreshList();
        regenBtn.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startServer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopServer();
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

    GradientDrawable box(int color, float radius, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        if (focused) g.setStroke(px(3), C_ACCENT);
        return g;
    }

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

    // ---------- 左边：二维码 ----------

    void buildLeft() {
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        root.addView(left, at(64, 112, 420, -2));

        left.addView(tv("扫码添加接口", 24, MainActivity.C_TEXT, true), lp(-2, -2, 0, 0));

        TextView desc = tv("手机和电视连同一个 Wi-Fi，用手机浏览器或微信扫一扫，在打开的网页里输入接口地址。",
                16, MainActivity.C_MUTED, false);
        desc.setLineSpacing(0, 1.3f);
        left.addView(desc, lp(-1, -2, 0, 10));

        qrView = new ImageView(this);
        qrView.setBackground(box(0xFFFFFFFF, 12, false));
        qrView.setPadding(px(14), px(14), px(14), px(14));
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        left.addView(qrView, lp(px(260), px(260), 0, 18));

        urlText = tv("", 15, MainActivity.C_MUTED, false);
        left.addView(urlText, lp(-1, -2, 0, 12));

        regenBtn = tv("重新生成二维码", 20, MainActivity.C_TEXT, false);
        regenBtn.setGravity(Gravity.CENTER);
        regenBtn.setPadding(px(28), px(12), px(28), px(12));
        regenBtn.setFocusable(true);
        regenBtn.setClickable(true);
        regenBtn.setBackground(box(0x29F5F1EA, 8, false));
        regenBtn.setOnFocusChangeListener((v, has) -> {
            regenBtn.setBackground(box(has ? 0x66F5F1EA : 0x29F5F1EA, 8, has));
        });
        regenBtn.setOnClickListener(v -> {
            startServer();
            toast("已重新生成，旧二维码失效");
        });
        left.addView(regenBtn, lp(-2, -2, 0, 16));

        String active = store.getActiveApi() == null ? "示例数据（还没选接口）" : store.getActiveName();
        TextView activeText = tv("当前使用：" + active, 16, MainActivity.C_MUTED, false);
        activeText.setSingleLine(true);
        activeText.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(activeText, lp(-1, -2, 0, 14));
    }

    // ---------- 右边：接口列表 ----------

    void buildRight() {
        countLabel = tv("已添加的接口", 24, MainActivity.C_TEXT, true);
        root.addView(countLabel, at(540, 112, -2, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(px(4), px(8), px(4), px(8));
        scroll.addView(listBox, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, at(540, 160, 676, 520));
    }

    // 重新画列表；如果焦点原来在某一行上，重画后焦点留在同一个位置
    void refreshList() {
        int focusIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isFocused()) focusIdx = i;
        }

        List<SourceStore.Source> list = store.list();
        listBox.removeAllViews();
        rows.clear();
        hints.clear();
        countLabel.setText("已添加的接口（" + list.size() + "）");

        if (list.isEmpty()) {
            TextView empty = tv("还没有接口，用手机扫左边的二维码添加。", 18, MainActivity.C_MUTED, false);
            listBox.addView(empty, lp(-2, -2, 4, 8));
        }
        for (int i = 0; i < list.size(); i++) {
            addRow(i, list.get(i));
        }

        if (focusIdx >= 0) {
            if (rows.isEmpty()) {
                regenBtn.requestFocus();
            } else {
                rows.get(Math.min(focusIdx, rows.size() - 1)).requestFocus();
            }
        }
    }

    void addRow(final int i, final SourceStore.Source s) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(18), px(14), px(18), px(14));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(box(0x29F5F1EA, 8, false));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = tv(s.name, 20, MainActivity.C_TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(name, lp(-1, -2, 0, 0));

        TextView link = tv(s.url, 15, MainActivity.C_MUTED, false);
        link.setSingleLine(true);
        link.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        txt.addView(link, lp(-1, -2, 0, 4));

        final TextView hint = tv("", 15, C_ACCENT, true);
        row.addView(hint, lp(-2, -2, 16, 0));

        rows.add(row);
        hints.add(hint);

        row.setOnFocusChangeListener((v, has) -> paintRow(row, hint, s, has));
        row.setOnClickListener(v -> useSource(s));  // 按确定键：使用这个接口
        row.setLongClickable(true);
        row.setOnLongClickListener(v -> {           // 长按确定键：删除（要连续长按两次）
            long now = System.currentTimeMillis();
            if (s.url.equals(pendingUrl) && now - pendingAt < 3000) {
                pendingUrl = null;
                store.remove(s.url);
                toast("已删除");
                refreshList();
            } else {
                pendingUrl = s.url;
                pendingAt = now;
                paintRow(row, hint, s, true);
                h.postDelayed(() -> paintRow(row, hint, s, row.isFocused()), 3100);
            }
            return true;
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = px(10);
        listBox.addView(row, p);
    }

    void paintRow(LinearLayout row, TextView hint, SourceStore.Source s, boolean focused) {
        row.setBackground(box(focused ? 0x4DF5F1EA : 0x29F5F1EA, 8, focused));
        boolean confirming = s.url.equals(pendingUrl) && System.currentTimeMillis() - pendingAt < 3000;
        hint.setText(!focused ? "" : (confirming ? "再长按一次确定删除" : "确定键使用 · 长按删除"));
    }

    // ---------- 使用一个接口：识别它是什么，必要时让用户选线路 / 站点 ----------

    interface OnPick {
        void pick(VodApi.Option o);
    }

    void useSource(SourceStore.Source s) {
        toast("正在读取「" + s.name + "」…");
        resolveAndPick(s.name, s.url, 0);
    }

    void resolveAndPick(final String name, final String url, final int depth) {
        Net.POOL.execute(() -> {
            try {
                final VodApi.Resolved r = VodApi.resolve(Net.getText(url), url);
                Net.MAIN.post(() -> onResolved(name, url, r, depth));
            } catch (final Exception e) {
                final String m = e.getMessage() == null ? e.toString() : e.getMessage();
                Net.MAIN.post(() -> toast("读取失败：" + m));
            }
        });
    }

    void onResolved(String name, String url, VodApi.Resolved r, int depth) {
        if (isFinishing()) return;
        if ("direct".equals(r.kind)) {
            String spec = new SiteSpec(1, url, "", "").toString();
            List<VodApi.Option> single = new ArrayList<>();
            single.add(new VodApi.Option(name, spec));
            choose(name, spec, single);
        } else if ("lines".equals(r.kind)) {
            if (depth >= 2 || r.options.isEmpty()) {
                showInfo("这里面没有可用的线路。");
                return;
            }
            showChoice("选择线路（共 " + r.options.size() + " 条）", r.options, o -> {
                toast("正在读取「" + o.name + "」…");
                resolveAndPick(o.name, o.url, depth + 1);
            });
        } else {
            if (r.options.isEmpty()) {
                showInfo("这条线路共 " + r.totalSites + " 个站点，但暂时没有能用的"
                        + "（支持 type 1 接口站点，和用 jar 的 type 3 爬虫站点；js、py 脚本站点还不支持）。"
                        + "请换一条线路，或添加你自己的接口地址。");
                return;
            }
            String title = "选择站点（共 " + r.options.size() + " 个可用"
                    + (r.unsupported > 0 ? "，另有 " + r.unsupported + " 个暂不支持" : "") + "）";
            final List<VodApi.Option> pool = r.options;
            showChoice(title, pool, o -> choose(o.name, o.url, pool));
        }
    }

    // 选定当前站点；同一份配置里的所有站点一起存成「站点池」，全站搜索要用
    void choose(String name, String spec, List<VodApi.Option> pool) {
        store.setActive(name, spec);
        store.setPool(pool);
        toast("已切换到「" + name + "」");
        finish();  // 回到首页，首页会自己重新读数据
    }

    void showChoice(String title, final List<VodApi.Option> opts, final OnPick cb) {
        String[] names = new String[opts.size()];
        for (int i = 0; i < names.length; i++) names[i] = opts.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(names, (d, which) -> cb.pick(opts.get(which)))
                .setNegativeButton("取消", null)
                .show();
    }

    void showInfo(String msg) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("知道了", null).show();
    }

    // ---------- 网页服务 + 二维码 ----------

    void startServer() {
        stopServer();
        token = randomToken();
        server = ConfigServer.startNew(this, store, token,
                msg -> h.post(() -> {
                    refreshList();
                    toast(msg);
                }));

        String ip = ConfigServer.localIp();
        if (server != null && ip != null) {
            String url = "http://" + ip + ":" + server.getListeningPort() + "/?k=" + token;
            Bitmap bmp = makeQr(url, px(232));
            if (bmp != null) {
                qrView.setImageBitmap(bmp);
                urlText.setText(url);
                urlText.setTextColor(MainActivity.C_MUTED);
                return;
            }
        }
        qrView.setImageDrawable(null);
        urlText.setText(server == null
                ? "网页服务启动失败（端口被占用），请退出再进入一次设置。"
                : "没有检测到局域网，请先把电视连上 Wi-Fi 或网线，再点下面的按钮重试。");
        urlText.setTextColor(C_ACCENT);
    }

    void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    static String randomToken() {
        byte[] b = new byte[4];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
        return sb.toString();
    }

    static Bitmap makeQr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int w = m.getWidth();
            int hh = m.getHeight();
            int[] pix = new int[w * hh];
            for (int y = 0; y < hh; y++) {
                for (int x = 0; x < w; x++) {
                    pix[y * w + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pix, 0, w, 0, 0, w, hh);
            return bmp;
        } catch (WriterException e) {
            return null;
        }
    }
}
