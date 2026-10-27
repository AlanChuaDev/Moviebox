package com.alan.moviebox;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

// 读取「type 1」接口（苹果CMS 的 JSON 接口）、解析片子列表，
// 以及识别影视仓那种线路索引 / 站点配置 JSON。
// 带 fetch / resolve 的方法会联网，必须在后台线程里调用。
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

    // 一集（或一个播放地址）。url 在 type 1 里就是真实地址，在 type 3 里是要交给爬虫换真实地址的编号。
    public static class Ep {
        public final String name;
        public final String url;

        public Ep(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    // 一条播放线路（播放源），里面有很多集
    public static class Line {
        public final String name;
        public final List<Ep> eps = new ArrayList<>();

        public Line(String name) {
            this.name = name;
        }
    }

    // 一部片的详细信息
    public static class Detail {
        public String title = "";
        public String meta = "";
        public String actor = "";
        public String director = "";
        public String desc = "";
        public final List<Line> lines = new ArrayList<>();
    }

    // 配置里的一个选项。线路索引里：url 是这条线路的配置地址；
    // 站点配置里：url 是 SiteSpec 转成的文字，选中后直接存为当前接口。
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
        public int unsupported;  // 看得到但暂时不能用的站点数（js / py 脚本、没有 jar 等）
    }

    // ---------- 识别一个地址里是什么 ----------

    // text 是下载到的内容，baseUrl 是它的地址（用来把 ./xxx.jar 这样的相对路径补成完整网址）
    public static Resolved resolve(String text, String baseUrl) throws Exception {
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
            String globalJar = jarUrl(baseUrl, str(o, "spider"));
            for (int i = 0; i < sites.length(); i++) {
                JSONObject s = sites.optJSONObject(i);
                if (s == null) continue;
                String api = str(s, "api");
                String name = str(s, "name");
                if (name.isEmpty()) name = str(s, "key");
                int type = s.optInt("type", -1);

                boolean searchable = s.optInt("searchable", 1) != 0;
                if (type == 1 && api.startsWith("http")) {
                    r.options.add(new Option(name, new SiteSpec(1, api, "", "", searchable).toString()));
                } else if (type == 3 && api.startsWith("csp_")) {
                    // 爬虫站点：站点自己可以指定 jar，没有就用配置顶部的 spider
                    String jar = jarUrl(baseUrl, str(s, "jar"));
                    if (jar.isEmpty()) jar = globalJar;
                    if (jar.isEmpty()) {
                        r.unsupported++;
                        continue;
                    }
                    Object ext = s.opt("ext");
                    String extStr = (ext == null || JSONObject.NULL.equals(ext)) ? "" : ext.toString();
                    r.options.add(new Option(name + "  [爬虫]",
                            new SiteSpec(3, api, jar, extStr, searchable).toString()));
                } else {
                    r.unsupported++;
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

    // 配置里的 jar 写法是「地址;md5;校验码」，只要第一段；相对路径按配置地址补全
    private static String jarUrl(String baseUrl, String spec) {
        if (spec == null) return "";
        String s = spec.trim();
        int semi = s.indexOf(';');
        if (semi >= 0) s = s.substring(0, semi).trim();
        if (s.isEmpty() || s.startsWith("img+")) return "";  // 图片伪装的 jar 暂不支持
        try {
            return new URL(new URL(baseUrl), s).toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- 读片子 ----------

    // type 1：typeId 为 null 表示「最新更新」（不分类）
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
        return parse(o, api);
    }

    // 把「class + list」格式的 JSON 解析成分类和片子。接口和爬虫返回的格式是一样的。
    // base 用来补全相对路径的海报地址，可以为 null。
    public static Page parse(JSONObject o, String base) {
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
                Movie m = toMovie(v, base);
                if (m != null) page.movies.add(m);
            }
        }
        return page;
    }

    // ---------- 详情 ----------

    // 解析详情 JSON（和列表一样是 list 数组）。wantedId 用来在 list 里挑出对应的那一部；
    // 挑不到就取第一部（有些接口不管传什么编号，返回的都是同一批）
    public static Detail parseDetail(JSONObject o, String base, String wantedId) throws Exception {
        JSONArray list = o.optJSONArray("list");
        if (list == null || list.length() == 0) throw new Exception("没有读到这部片子的详情");
        JSONObject v = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i);
            if (c != null && wantedId != null && wantedId.equals(first(c, "vod_id", "vodId", "id", "ids"))) {
                v = c;
                break;
            }
        }
        if (v == null) v = list.optJSONObject(0);
        if (v == null) throw new Exception("没有读到这部片子的详情");

        Detail d = new Detail();
        d.title = first(v, "vod_name", "name", "title", "vodName");
        d.meta = metaOf(v);
        d.actor = clean(first(v, "vod_actor", "actor", "actors"));
        d.director = clean(first(v, "vod_director", "director"));
        d.desc = clean(first(v, "vod_content", "content", "description", "desc", "vod_blurb"));
        d.lines.addAll(parseLines(first(v, "vod_play_from", "playFrom", "play_from"),
                first(v, "vod_play_url", "playUrl", "play_url")));
        return d;
    }

    // 播放线路和选集的写法：线路之间用 $$$ 隔开，一条线路里每集用 # 隔开，每集是「名字$地址」
    public static List<Line> parseLines(String from, String urls) {
        List<Line> out = new ArrayList<>();
        if (urls == null || urls.trim().isEmpty()) return out;
        String[] froms = from == null ? new String[0] : from.split("\\$\\$\\$");
        String[] groups = urls.split("\\$\\$\\$");
        for (int i = 0; i < groups.length; i++) {
            String name = i < froms.length ? froms[i].trim() : "";
            if (name.isEmpty()) name = "线路" + (i + 1);
            Line line = new Line(name);
            String[] items = groups[i].split("#");
            for (String item : items) {
                String it = item.trim();
                if (it.isEmpty()) continue;
                int p = it.indexOf('$');
                String epName = p >= 0 ? it.substring(0, p).trim() : "";
                String epUrl = p >= 0 ? it.substring(p + 1).trim() : it;
                if (epUrl.isEmpty()) continue;
                if (epName.isEmpty()) epName = "第" + (line.eps.size() + 1) + "集";
                line.eps.add(new Ep(epName, epUrl));
            }
            if (!line.eps.isEmpty()) out.add(line);
        }
        return out;
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

    private static Movie toMovie(JSONObject v, String base) {
        // TVBox/苹果 CMS/部分爬虫返回字段并不完全一致，主页和搜索统一兼容常见别名。
        String name = first(v, "vod_name", "name", "title", "vodName");
        if (name.isEmpty()) return null;

        String score = first(v, "vod_score", "score", "rating");
        if (score.equals("0") || score.equals("0.0")) score = "";

        String desc = clean(first(v, "vod_content", "content", "description", "desc", "vod_blurb"));
        if (desc.isEmpty()) desc = "暂无简介";

        String posterRaw = first(v, "vod_pic", "vod_pic_thumb", "pic", "cover", "poster", "image", "img");
        String id = first(v, "vod_id", "vodId", "id", "ids", "url");
        String play = first(v, "vod_play_url", "playUrl", "play_url", "url");
        int color = Math.abs(name.hashCode() % 10);
        return new Movie(name, score, first(v, "vod_remarks", "remarks", "state", "note"), color,
                metaOf(v), desc, absUrl(base, posterRaw), id, play);
    }

    private static String first(JSONObject o, String... keys) {
        for (String key : keys) {
            String value = str(o, key);
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    // 类型、年份、地区、备注，用 · 连成一行
    private static String metaOf(JSONObject v) {
        StringBuilder meta = new StringBuilder();
        String[][] fields = {
                {"type_name", "type", "category"},
                {"vod_year", "year", "release_year"},
                {"vod_area", "area", "country"},
                {"vod_remarks", "remarks", "state", "note"}
        };
        for (String[] aliases : fields) {
            String s = first(v, aliases);
            if (s.isEmpty()) continue;
            if (meta.length() > 0) meta.append(" · ");
            meta.append(s);
        }
        return meta.toString();
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

    // 海报地址可能是 // 开头、相对路径，或带「@Referer=…」这样的请求头后缀；统一成完整网址，不能用的返回 null
    private static String absUrl(String base, String s) {
        for (String mark : new String[]{"@Referer=", "@User-Agent=", "@Cookie="}) {
            int i = s.indexOf(mark);
            if (i > 0) s = s.substring(0, i);
        }
        if (s.isEmpty()) return null;
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        if (s.startsWith("//")) return "http:" + s;
        if (s.contains("://") || base == null) return null;  // mac:// 之类的特殊地址，或没有可参照的地址
        try {
            return new URL(new URL(base), s).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
