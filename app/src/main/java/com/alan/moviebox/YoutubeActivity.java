package com.alan.moviebox;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
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
        String id, title, channel;
        Video(String id, String title, String channel) { this.id=id; this.title=title; this.channel=channel; }
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
        TextView hint = text("使用 YouTube 官方搜索 API；首次使用需要填写自己的 API Key。", 15, false);
        hint.setTextColor(0xFFB9B4AA); hint.setPadding(0, dp(8), 0, dp(10)); root.addView(hint);
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); root.addView(controls, new LinearLayout.LayoutParams(-1, dp(58)));
        query = new EditText(this); query.setSingleLine(true); query.setTextColor(Color.WHITE); query.setHintTextColor(0xFFB9B4AA); query.setHint("搜索 YouTube 视频"); query.setTextSize(18); query.setImeOptions(3); query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setBackground(bg(0xFF202126, false)); query.setPadding(dp(12), 0, dp(12), 0); query.setFocusable(true);
        controls.addView(query, new LinearLayout.LayoutParams(0, -1, 1));
        Button search = button("搜索", this::search); controls.addView(search, new LinearLayout.LayoutParams(dp(116), -1));
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); root.addView(actions, new LinearLayout.LayoutParams(-1, dp(56)));
        actions.addView(button("观看历史", this::showHistory), new LinearLayout.LayoutParams(dp(150), -1));
        actions.addView(button("设置 API Key", this::editApiKey), new LinearLayout.LayoutParams(dp(180), -1));
        actions.addView(button("账号说明", this::accountInfo), new LinearLayout.LayoutParams(dp(150), -1));
        status = text(apiKey.isEmpty() ? "尚未设置 API Key。点击「设置 API Key」后即可搜索。" : "已设置 API Key。输入关键词后搜索。", 15, false);
        status.setTextColor(0xFFF0A35C); status.setPadding(0, dp(10), 0, dp(8)); root.addView(status);
        ScrollView scroll = new ScrollView(this); results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        query.setOnEditorActionListener((v, id, event) -> { search(); return true; });
        query.requestFocus();
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
                else { JSONArray items = json.optJSONArray("items"); if (items != null) for (int i=0;i<items.length();i++) { JSONObject item=items.optJSONObject(i); if(item==null) continue; JSONObject id=item.optJSONObject("id"), sn=item.optJSONObject("snippet"); if(id==null||sn==null) continue; String vid=id.optString("videoId", ""); if(!vid.isEmpty()) found.add(new Video(vid, sn.optString("title", "(无标题)"), sn.optString("channelTitle", "YouTube"))); } }
            } catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            finally { if(conn!=null) conn.disconnect(); }
            final String err=error; final List<Video> out=found;
            main.post(() -> { if (isFinishing()) return; if(err!=null) { status.setText("搜索失败："+err); return; } videos.clear(); videos.addAll(out); status.setText("找到 "+out.size()+" 个结果。选择一项开始播放。"); renderResults(out); });
        });
    }
    private void renderResults(List<Video> list) {
        results.removeAllViews();
        for (Video v : list) {
            Button row = button(v.title + "\n" + v.channel, () -> play(v)); row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); row.setPadding(dp(14), dp(4), dp(14), dp(4));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(68)); p.bottomMargin=dp(6); results.addView(row,p);
        }
        if (!list.isEmpty()) results.getChildAt(0).requestFocus();
    }
    private void play(Video v) {
        saveHistory(v); buildBase(v.title);
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); root.addView(top, new LinearLayout.LayoutParams(-1, dp(54)));
        top.addView(button("返回搜索", () -> { buildHome(); query.setText(""); }), new LinearLayout.LayoutParams(dp(160), -1));
        top.addView(button("观看历史", this::showHistory), new LinearLayout.LayoutParams(dp(150), -1));
        web = new WebView(this); WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setMediaPlaybackRequiresUserGesture(false); web.setWebChromeClient(new WebChromeClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0;background:#000;height:100%;}iframe{border:0;width:100%;height:100%;}</style></head><body><iframe src='https://www.youtube.com/embed/" + v.id + "?autoplay=1&playsinline=1&rel=0' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe></body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null);
    }
    private void showHistory() {
        if (web != null) { web.destroy(); web=null; }
        buildBase("YouTube · 观看历史");
        Button back=button("返回 YouTube", this::buildHome); root.addView(back, new LinearLayout.LayoutParams(dp(180), dp(52)));
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try {
            JSONArray arr=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_HISTORY,"[]"));
            for(int i=0;i<arr.length();i++){ JSONObject o=arr.optJSONObject(i); if(o==null) continue; Video v=new Video(o.optString("id"),o.optString("title"),o.optString("channel")); Button b=button(v.title+"\n"+v.channel,()->play(v)); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(68)); p.bottomMargin=dp(6); list.addView(b,p); }
            if(arr.length()==0) list.addView(text("还没有观看记录。",18,false));
        } catch(Exception e){ list.addView(text("无法读取历史记录。",18,false)); }
        list.setFocusable(true);
    }
    private void saveHistory(Video v) {
        try {
            android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE); JSONArray old=new JSONArray(p.getString(KEY_HISTORY,"[]")); JSONArray next=new JSONArray();
            JSONObject item=new JSONObject(); item.put("id",v.id); item.put("title",v.title); item.put("channel",v.channel); item.put("watchedAt",System.currentTimeMillis()); next.put(item);
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
