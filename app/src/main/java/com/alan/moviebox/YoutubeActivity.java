package com.alan.moviebox;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** YouTube area for Android TV. Search uses the official YouTube Data API; playback uses the official embed player. */
public class YoutubeActivity extends Activity {
    private static final String PREFS = "moviebox_youtube_v1";
    private static final String KEY_API = "data_api_key";
    private static final String KEY_HISTORY = "local_history";
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root, results;
    private EditText query;
    private TextView status;
    private WebView web;
    private String apiKey;
    private final List<Video> videos = new ArrayList<>();

    static class Video {
        String id, title, channel, thumbnail;
        Video(String id, String title, String channel) { this(id, title, channel, ""); }
        Video(String id, String title, String channel, String thumbnail) { this.id=id; this.title=title; this.channel=channel; this.thumbnail=thumbnail; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        apiKey = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_API, "");
        buildHome();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color, boolean focus) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(8));
        if (focus) d.setStroke(dp(2), 0xFFF0A35C); return d;
    }
    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(0xFFF5F1EA);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setFocusable(false); return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(16); b.setAllCaps(false);
        b.setFocusable(true); b.setBackground(bg(0xFF292B30, false));
        b.setOnFocusChangeListener((v, f) -> b.setBackground(bg(f ? 0xFF42454C : 0xFF292B30, f)));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void buildBase(String title) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(36), dp(20), dp(36), dp(18)); root.setBackgroundColor(0xFF08090B);
        TextView heading = text(title, 27, true); root.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }
    private void buildHome() {
        if (web != null) { web.destroy(); web = null; }
        buildBase("YouTube");
        TextView subtitle = text("为电视大屏设计 · Guest 游客模式 · 观看记录仅保存在本机", 15, false);
        subtitle.setTextColor(0xFFB9B4AA); subtitle.setPadding(0, dp(5), 0, dp(12)); root.addView(subtitle);
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); root.addView(controls, new LinearLayout.LayoutParams(-1, dp(58)));
        query = new EditText(this); query.setSingleLine(true); query.setTextColor(Color.WHITE); query.setHintTextColor(0xFFB9B4AA); query.setHint("搜索 YouTube 视频"); query.setTextSize(18); query.setImeOptions(3); query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setBackground(bg(0xFF202126, false)); query.setPadding(dp(12), 0, dp(12), 0); query.setFocusable(true);
        controls.addView(query, new LinearLayout.LayoutParams(0, -1, 1));
        Button search = button("搜索", this::search); controls.addView(search, new LinearLayout.LayoutParams(dp(116), -1));
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); root.addView(actions, new LinearLayout.LayoutParams(-1, dp(52)));
        actions.addView(button("观看历史", this::showHistory), new LinearLayout.LayoutParams(dp(150), -1));
        actions.addView(button("API 设置（开发版）", this::editApiKey), new LinearLayout.LayoutParams(dp(210), -1));
        actions.addView(button("账号功能说明", this::accountInfo), new LinearLayout.LayoutParams(dp(180), -1));
        TextView section = text("开始探索", 21, true); section.setPadding(0, dp(18), 0, dp(8)); root.addView(section);
        TextView intro = text(apiKey.isEmpty() ? "界面已准备好。搜索和实时影片资料需要稍后由应用管理员配置 API；顾客不需要自己注册 Google Cloud。" : "选择一个分类快速搜索，或输入你想看的内容。", 15, false);
        intro.setTextColor(0xFFB9B4AA); intro.setPadding(0, 0, 0, dp(10)); root.addView(intro);
        LinearLayout categories = new LinearLayout(this); categories.setOrientation(LinearLayout.HORIZONTAL); root.addView(categories, new LinearLayout.LayoutParams(-1, dp(50)));
        String[] topics = {"音乐", "电影预告", "科技", "动画", "纪录片"};
        for (String topic : topics) {
            Button chip = button(topic, () -> { query.setText(topic); search(); });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -1); cp.rightMargin = dp(8); categories.addView(chip, cp);
        }
        status = text(apiKey.isEmpty() ? "游客模式已就绪 · API 尚未配置" : "已配置 API · 搜索结果将显示影片封面", 14, false);
        status.setTextColor(0xFFF0A35C); status.setPadding(0, dp(12), 0, dp(8)); root.addView(status);
        ScrollView scroll = new ScrollView(this); results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addSectionLabel("最近观看");
        renderHistoryPreview();
        if (apiKey.isEmpty()) {
            TextView empty = text("当前开发版需要在 API 设置中配置 Key；正式发布时应改为应用统一配置，让顾客不必自己申请 Key。", 15, false);
            empty.setTextColor(0xFF8F9298); empty.setPadding(dp(4), dp(18), dp(4), dp(18)); results.addView(empty);
        } else {
            addSectionLabel("热门视频 · 马来西亚");
            loadHomeFeed();
        }
        query.setOnEditorActionListener((v, id, event) -> { search(); return true; });
        query.requestFocus();
    }
    private void addSectionLabel(String label) {
        TextView t = text(label, 18, true); t.setPadding(0, dp(8), 0, dp(8)); results.addView(t);
    }
    private void renderHistoryPreview() {
        try {
            JSONArray arr = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HISTORY, "[]"));
            int count = Math.min(6, arr.length());
            for (int i = 0; i < count; i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                Video v = new Video(o.optString("id"), o.optString("title"), o.optString("channel"), o.optString("thumbnail"));
                results.addView(videoRow(v));
            }
            if (count == 0) results.addView(text("还没有观看记录。搜索并播放影片后，会显示在这里。", 15, false));
        } catch (Exception ignored) { }
    }
    private void loadHomeFeed() {
        status.setText("正在加载热门视频…");
        io.execute(() -> {
            List<Video> found = new ArrayList<>(); String error = null; HttpURLConnection conn = null;
            try {
                String url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&regionCode=MY&maxResults=12&key=" + URLEncoder.encode(apiKey, "UTF-8");
                conn = (HttpURLConnection) new URL(url).openConnection(); conn.setConnectTimeout(8000); conn.setReadTimeout(10000); conn.setRequestMethod("GET");
                int code = conn.getResponseCode(); InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder body = new StringBuilder(); try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { String line; while ((line = br.readLine()) != null) body.append(line); }
                JSONObject json = new JSONObject(body.toString());
                if (code < 200 || code >= 300) { JSONObject e = json.optJSONObject("error"); error = e == null ? ("HTTP " + code) : e.optString("message", "HTTP " + code); }
                else {
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i); if (item == null) continue;
                        JSONObject sn = item.optJSONObject("snippet"); if (sn == null) continue;
                        String id = item.optString("id", ""); if (id.isEmpty()) continue;
                        String thumb = ""; JSONObject thumbs = sn.optJSONObject("thumbnails");
                        if (thumbs != null) { JSONObject im = thumbs.optJSONObject("high"); if (im == null) im = thumbs.optJSONObject("medium"); if (im == null) im = thumbs.optJSONObject("default"); if (im != null) thumb = im.optString("url", ""); }
                        found.add(new Video(id, sn.optString("title", "(无标题)"), sn.optString("channelTitle", "YouTube"), thumb));
                    }
                }
            } catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            finally { if (conn != null) conn.disconnect(); }
            final String err = error; final List<Video> out = found;
            main.post(() -> {
                if (isFinishing()) return;
                if (err != null) { status.setText("热门内容暂时无法加载：" + err); return; }
                status.setText("热门内容已更新 · " + out.size() + " 个视频");
                for (Video v : out) results.addView(videoRow(v));
            });
        });
    }
    private void editApiKey() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); input.setText(apiKey); input.setHint("YouTube Data API v3 Key");
        new AlertDialog.Builder(this).setTitle("设置 YouTube API Key").setMessage("在 Google Cloud Console 启用 YouTube Data API v3 并创建 API Key。请限制该 Key 的使用范围。")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("保存", (d,w) -> {
                    apiKey = input.getText().toString().trim(); getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_API, apiKey).apply();
                    status.setText(apiKey.isEmpty() ? "API Key 已清空。" : "API Key 已保存，可以搜索视频。");
                }).show();
    }
    private void accountInfo() {
        new AlertDialog.Builder(this).setTitle("YouTube 账号功能")
                .setMessage("当前版本使用官方嵌入式播放器和 Data API 搜索，并保存 Moviebox 本地观看历史。YouTube 登录、订阅与云端观看历史同步尚未接入；Google OAuth 需要正式的 OAuth 客户端、授权流程和政策审查，不能用嵌入式 WebView 假装登录。")
                .setPositiveButton("知道了", null).show();
    }
    private void search() {
        String q = query == null ? "" : query.getText().toString().trim();
        if (q.isEmpty()) { toast("请先输入搜索关键词"); return; }
        if (apiKey.isEmpty()) { editApiKey(); return; }
        status.setText("正在搜索…"); results.removeAllViews();
        io.execute(() -> {
            List<Video> found = new ArrayList<>(); String error = null;
            HttpURLConnection conn = null;
            try {
                String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=25&q=" + URLEncoder.encode(q, "UTF-8") + "&key=" + URLEncoder.encode(apiKey, "UTF-8");
                conn = (HttpURLConnection) new URL(url).openConnection(); conn.setConnectTimeout(8000); conn.setReadTimeout(10000); conn.setRequestMethod("GET");
                int code = conn.getResponseCode(); InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder body = new StringBuilder(); try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { String line; while ((line=br.readLine())!=null) body.append(line); }
                JSONObject json = new JSONObject(body.toString());
                if (code < 200 || code >= 300) { JSONObject e = json.optJSONObject("error"); error = e == null ? ("HTTP " + code) : e.optString("message", "HTTP " + code); }
                else { JSONArray items = json.optJSONArray("items"); if (items != null) for (int i=0;i<items.length();i++) { JSONObject item=items.optJSONObject(i); if(item==null) continue; JSONObject id=item.optJSONObject("id"), sn=item.optJSONObject("snippet"); if(id==null||sn==null) continue; String vid=id.optString("videoId", ""); if(!vid.isEmpty()) { String thumb=""; JSONObject thumbs=sn.optJSONObject("thumbnails"); if(thumbs!=null) { JSONObject image=thumbs.optJSONObject("high"); if(image==null) image=thumbs.optJSONObject("medium"); if(image==null) image=thumbs.optJSONObject("default"); if(image!=null) thumb=image.optString("url", ""); } found.add(new Video(vid, sn.optString("title", "(无标题)"), sn.optString("channelTitle", "YouTube"), thumb)); } } }
            } catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            finally { if(conn!=null) conn.disconnect(); }
            final String err=error; final List<Video> out=found;
            main.post(() -> { if (isFinishing()) return; if(err!=null) { status.setText("搜索失败："+err); return; } videos.clear(); videos.addAll(out); status.setText("找到 "+out.size()+" 个结果。选择一项开始播放。"); renderResults(out); });
        });
    }
    private void renderResults(List<Video> list) {
        results.removeAllViews();
        addSectionLabel("搜索结果 · " + list.size() + " 个视频");
        for (Video v : list) results.addView(videoRow(v));
        if (list.isEmpty()) results.addView(text("没有找到结果，请尝试其他关键词。", 16, false));
        if (!list.isEmpty()) results.getChildAt(1).requestFocus();
    }
    private View videoRow(Video v) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(12), dp(8)); row.setBackground(bg(0xFF17191D, false)); row.setFocusable(true); row.setClickable(true);
        row.setOnFocusChangeListener((view, focused) -> row.setBackground(bg(focused ? 0xFF292D33 : 0xFF17191D, focused)));
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackground(bg(0xFF292B30, false));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(220), dp(124)); ip.rightMargin = dp(18); row.addView(image, ip);
        if (v.thumbnail != null && !v.thumbnail.isEmpty()) loadThumbnail(v.thumbnail, image);
        LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(v.title, 17, true); title.setMaxLines(2); details.addView(title);
        TextView channel = text(v.channel, 14, false); channel.setTextColor(0xFFB9B4AA); LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, -2); chp.topMargin = dp(8); details.addView(channel, chp);
        TextView playLabel = text("▶  播放视频", 13, false); playLabel.setTextColor(0xFFF0A35C); LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2); plp.topMargin = dp(8); details.addView(playLabel, plp);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(view -> play(v)); row.setOnKeyListener((view, keyCode, event) -> { if (event.getAction()==KeyEvent.ACTION_UP && (keyCode==KeyEvent.KEYCODE_DPAD_CENTER || keyCode==KeyEvent.KEYCODE_ENTER)) { play(v); return true; } return false; });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.bottomMargin = dp(8); row.setLayoutParams(rp);
        return row;
    }
    private void loadThumbnail(String url, ImageView target) {
        io.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection(); conn.setConnectTimeout(6000); conn.setReadTimeout(8000); conn.setRequestProperty("User-Agent", "Moviebox/1.0");
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) main.post(() -> { if (!isFinishing()) target.setImageBitmap(bitmap); });
                }
            } catch (Exception ignored) { } finally { if (conn != null) conn.disconnect(); }
        });
    }
    private void play(Video v) {
        saveHistory(v); buildBase(v.title);
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); root.addView(top, new LinearLayout.LayoutParams(-1, dp(54)));
        top.addView(button("返回搜索", () -> { buildHome(); query.setText(""); }), new LinearLayout.LayoutParams(dp(160), -1));
        top.addView(button("观看历史", this::showHistory), new LinearLayout.LayoutParams(dp(150), -1));
        web = new WebView(this); WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setMediaPlaybackRequiresUserGesture(false); web.setWebChromeClient(new WebChromeClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden;}iframe{border:0;width:100%;height:100%;}</style></head><body><iframe src='https://www.youtube.com/embed/" + v.id + "?autoplay=1&playsinline=1&rel=0&controls=1&enablejsapi=1' allow='autoplay; encrypted-media; picture-in-picture; fullscreen' allowfullscreen></iframe></body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null);
    }
    private void showHistory() {
        if (web != null) { web.destroy(); web=null; }
        buildBase("YouTube · 观看历史");
        Button back=button("返回 YouTube", this::buildHome); root.addView(back, new LinearLayout.LayoutParams(dp(180), dp(52)));
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try {
            JSONArray arr=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_HISTORY,"[]"));
            for(int i=0;i<arr.length();i++){ JSONObject o=arr.optJSONObject(i); if(o==null) continue; Video v=new Video(o.optString("id"),o.optString("title"),o.optString("channel"),o.optString("thumbnail")); list.addView(videoRow(v)); }
            if(arr.length()==0) list.addView(text("还没有观看记录。",18,false));
        } catch(Exception e){ list.addView(text("无法读取历史记录。",18,false)); }
        list.setFocusable(true);
    }
    private void saveHistory(Video v) {
        try {
            android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE); JSONArray old=new JSONArray(p.getString(KEY_HISTORY,"[]")); JSONArray next=new JSONArray();
            JSONObject item=new JSONObject(); item.put("id",v.id); item.put("title",v.title); item.put("channel",v.channel); item.put("thumbnail",v.thumbnail == null ? "" : v.thumbnail); item.put("watchedAt",System.currentTimeMillis()); next.put(item);
            for(int i=0;i<old.length() && next.length()<50;i++){ JSONObject o=old.optJSONObject(i); if(o!=null && !v.id.equals(o.optString("id"))) next.put(o); }
            p.edit().putString(KEY_HISTORY,next.toString()).apply();
        } catch(Exception ignored) { }
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_BACK && web!=null) { web.destroy(); web=null; buildHome(); return true; }
        return super.onKeyDown(keyCode,event);
    }
    @Override protected void onDestroy() { if(web!=null) web.destroy(); io.shutdownNow(); super.onDestroy(); }
}
