package com.alan.moviebox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** YouTube page for Android TV. Uses YouTube's own website; no Data API or API key is used. */
public class YoutubeActivity extends Activity {
    private static final String YOUTUBE_TV = "https://www.youtube.com/tv";
    private static final String YOUTUBE_HOME = "https://www.youtube.com/";
    private LinearLayout root;
    private WebView web;
    private TextView status;

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(int color, boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(8));
        if (focused) d.setStroke(dp(2), 0xFFF0A35C);
        return d;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setBackground(background(0xFF292B30, false));
        b.setOnFocusChangeListener((v, focused) -> b.setBackground(background(focused ? 0xFF42454C : 0xFF292B30, focused)));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Remove a legacy API key saved by older builds; this version does not use the Data API.
        getSharedPreferences("moviebox_youtube_v1", MODE_PRIVATE).edit().remove("data_api_key").apply();
        buildPage();
    }

    private void buildPage() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.setBackgroundColor(0xFF08090B);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView title = text("YouTube", 22, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(button("首页", () -> load(YOUTUBE_HOME)), new LinearLayout.LayoutParams(dp(90), -1));
        bar.addView(button("刷新", () -> { if (web != null) web.reload(); }), new LinearLayout.LayoutParams(dp(90), -1));
        bar.addView(button("登录 / Guest", this::openOfficialYoutube), new LinearLayout.LayoutParams(dp(150), -1));
        bar.addView(button("返回", this::finish), new LinearLayout.LayoutParams(dp(90), -1));

        status = text("正在加载 YouTube 首页……首次加载需要网络连接。", 13, false);
        status.setTextColor(0xFFB9B4AA);
        status.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (status != null) status.setText("正在加载 YouTube 内容……");
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (status != null) status.setText("YouTube 页面已加载。可使用遥控器浏览影片；登录功能如受限制，请使用官方 YouTube 应用。 ");
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be") || host.endsWith(".google.com"))) {
                    return false;
                }
                return false;
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && status != null) {
                    status.setText("YouTube 加载失败，请检查网络后按“刷新”。");
                }
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        load(YOUTUBE_TV);
        web.setFocusable(true);
        web.requestFocus();
    }

    private void load(String url) {
        if (web != null) web.loadUrl(url);
    }

    /** QR-code sign-in and Guest selection are provided by the official TV app when installed. */
    private void openOfficialYoutube() {
        String[] packages = {"com.google.android.youtube.tv", "com.google.android.youtube"};
        for (String packageName : packages) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null) {
                try {
                    startActivity(launch);
                    return;
                } catch (ActivityNotFoundException ignored) { }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("YouTube 登录 / Guest")
                .setMessage("当前设备没有检测到官方 YouTube 应用。你仍然可以在本页以未登录状态浏览公开影片。若要使用电视二维码登录或官方 Guest 选项，请先在设备上安装/启用官方 YouTube 应用。Google 可能会阻止在第三方内嵌 WebView 中登录。")
                .setPositiveButton("继续浏览", (dialog, which) -> load(YOUTUBE_TV))
                .setNegativeButton("打开 YouTube 网站", (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_HOME))); }
                    catch (Exception e) { Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show(); }
                }).show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
