package com.alan.moviebox;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

// 读取「type 1」接口（苹果CMS 的 JSON 接口），以及识别影视仓那种线路索引 / 配置 JSON。
// 所有方法都会联网，必须在后台线程里调用。
public class VodApi {

    public static class Category {
        public final String id;
        public final String name;

        public Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class Page {
        public final List<Movie> movies = new ArrayList<>();
        public final List<Category> categories = new ArrayList<>();
    }

    // 配置里的一个选项：线路索引里的一条线路，或配置里的一个站点
    public static class Option {
        public final String name;
        public final String url;

        public Option(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    // 识别结果：kind 是 "direct"（本身就是接口）、"lines"（线路索引）、"sites"（站点配置）
    public static class Resolved {
        public String kind;
        public final List<Option> options = new ArrayList<>();
        public int totalSites;
    }

    // ---------- 识别一个地址里是什么 ----------

    public static Resolved resolve(String text) throws Exception {
        JSONObject o;
        try {
            o = new JSONObject(text);
        } catch (Exception e) {
            throw new Exception("返回的内容不是 JSON");
        }
        Resolved r = new Resolved();

        JSONArray urls = o.optJSONArray("urls");
        if (urls != null) {
            r.kind = "lines";
            for (int i = 0; i < urls.length(); i++) {
                JSONObject u = urls.optJSONObject(i);
                if (u == null) continue;
                String url = str(u, "url");
                if (url.isEmpty()) continue;
                String name = str(u, "name");
                r.options.add(new Option(name.isEmpty() ? url : name, url));
            }
            return r;
        }

        JSONArray sites = o.optJSONArray("sites");
        if (sites != null) {
            r.kind = "sites";
            r.totalSites = sites.length();
            for (int i = 0; i < sites.length(); i++) {
                JSONObject s = sites.optJSONObject(i);
                if (s == null) continue;
                String api = str(s, "api");
                // 只有 type 1（JSON 接口）并且是网址的站点能直接读取
                if (s.optInt("type", -1) == 1 && api.startsWith("http")) {
                    String name = str(s, "name");
                    r.options.add(new Option(name.isEmpty() ? str(s, "key") : name, api));
                }
            }
            return r;
        }

        if (o.optJSONArray("list") != null || o.optJSONArray("class") != null) {
            r.kind = "direct";
            return r;
        }
        throw new Exception("无法识别这个地址的内容，它不是接口，也不是线路配置");
    }

    // ---------- 读片子 ----------

    // typeId 为 null 表示「最新更新」（不分类）
    public static Page fetch(String api, String typeId) throws Exception {
        String url = api + (api.contains("?") ? "&" : "?") + "ac=detail&pg=1";
        if (typeId != null && !typeId.isEmpty()) {
            url += "&t=" + URLEncoder.encode(typeId, "UTF-8");
        }
        JSONObject o;
        try {
            o = new JSONObject(Net.getText(url));
        } catch (org.json.JSONException e) {
            throw new Exception("接口返回的内容不是 JSON");
        }

        Page page = new Page();
        JSONArray cls = o.optJSONArray("class");
        if (cls != null) {
            for (int i = 0; i < cls.length(); i++) {
                JSONObject c = cls.optJSONObject(i);
                if (c == null) continue;
                page.categories.add(new Category(str(c, "type_id"), str(c, "type_name")));
            }
        }
        JSONArray list = o.optJSONArray("list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject v = list.optJSONObject(i);
                if (v == null) continue;
                Movie m = toMovie(v, api);
                if (m != null) page.movies.add(m);
            }
        }
        return page;
    }

    // 「电影」「剧集」「综艺」三个标签，在接口的分类里按名字找对应的分类编号；找不到返回 null
    public static String matchCategory(List<Category> cats, int tab) {
        String[][] keys = {{}, {"电影", "影片"}, {"剧"}, {"综艺"}};
        if (tab < 0 || tab >= keys.length) return null;
        for (String k : keys[tab]) {
            for (Category c : cats) {
                if (c.name.contains(k)) return c.id;
            }
        }
        return null;
    }

    private static Movie toMovie(JSONObject v, String api) {
        String name = str(v, "vod_name");
        if (name.isEmpty()) return null;

        String score = str(v, "vod_score");
        if (score.equals("0") || score.equals("0.0")) score = "";

        StringBuilder meta = new StringBuilder();
        for (String k : new String[]{"type_name", "vod_year", "vod_area", "vod_remarks"}) {
            String s = str(v, k);
            if (s.isEmpty()) continue;
            if (meta.length() > 0) meta.append(" · ");
            meta.append(s);
        }

        String desc = clean(str(v, "vod_content"));
        if (desc.isEmpty()) desc = "暂无简介";

        int color = Math.abs(name.hashCode() % 10);  // 没有主题色时，按片名固定挑一个
        return new Movie(name, score, str(v, "vod_remarks"), color, meta.toString(), desc,
                absUrl(api, str(v, "vod_pic")), str(v, "vod_id"), str(v, "vod_play_url"));
    }

    // JSON 里的 null 在 optString 里会变成字符串 "null"，这里统一处理成空字符串
    private static String str(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    // 简介里常有 HTML 标签和转义字符，去掉后再显示
    private static String clean(String s) {
        s = s.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    // 海报地址可能是 // 开头或相对路径，统一成完整网址；不能用的返回 null
    private static String absUrl(String base, String s) {
        if (s.isEmpty()) return null;
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        if (s.startsWith("//")) return "http:" + s;
        if (s.contains("://")) return null;  // mac:// 之类的特殊地址
        try {
            return new URL(new URL(base), s).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
