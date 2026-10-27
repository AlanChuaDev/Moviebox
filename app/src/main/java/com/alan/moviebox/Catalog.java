package com.alan.moviebox;

import android.content.Context;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

// 统一的「读数据」入口：type 1 直接读接口，type 3 通过爬虫 jar 读。
// 爬虫返回的 JSON 格式和接口一样，所以都交给 VodApi 解析。
// 这里的方法都会联网，必须在后台线程调用。
public class Catalog {
    // 爬虫不一定能同时被多个线程调用，这里统一排队
    private static final Object SPIDER_LOCK = new Object();

    // 一个可以播放的地址，加上播放时要带的请求头
    public static class PlayInfo {
        public final String url;
        public final Map<String, String> headers;

        public PlayInfo(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }
    }

    // ---------- 列表 ----------

    // 首页：返回「最新更新」的片子和分类列表
    public static VodApi.Page home(Context c, SiteSpec s) throws Throwable {
        if (!s.isSpider()) return VodApi.fetch(s.api, null);
        synchronized (SPIDER_LOCK) {
            Object sp = SpiderRuntime.get(c.getApplicationContext(), s);
            VodApi.Page page = VodApi.parse(asJson(SpiderRuntime.call(sp, "homeContent", true)), null);
            if (page.movies.isEmpty()) {
                VodApi.Page more = VodApi.parse(asJson(SpiderRuntime.call(sp, "homeVideoContent")), null);
                page.movies.addAll(more.movies);
            }
            return page;
        }
    }

    // 某个分类的第一页
    public static VodApi.Page category(Context c, SiteSpec s, String typeId) throws Throwable {
        if (!s.isSpider()) return VodApi.fetch(s.api, typeId);
        synchronized (SPIDER_LOCK) {
            Object sp = SpiderRuntime.get(c.getApplicationContext(), s);
            Object r = SpiderRuntime.call(sp, "categoryContent", typeId, "1", true, new HashMap<String, String>());
            return VodApi.parse(asJson(r), null);
        }
    }

    // ---------- 搜索 ----------

    // 在一个站点里搜片名。全站搜索会同时搜很多站点，所以这里不排队，也不能抛错误给界面以外的地方。
    public static VodApi.Page search(Context c, SiteSpec s, String keyword) throws Throwable {
        if (!s.isSpider()) {
            String url = s.api + (s.api.contains("?") ? "&" : "?")
                    + "ac=detail&wd=" + URLEncoder.encode(keyword, "UTF-8");
            try {
                return VodApi.parse(new JSONObject(Net.getText(url)), s.api);
            } catch (JSONException e) {
                throw new Exception("接口返回的内容不是 JSON");
            }
        }
        Object sp = SpiderRuntime.get(c.getApplicationContext(), s);
        return VodApi.parse(asJson(SpiderRuntime.call(sp, "searchContent", keyword, false)), null);
    }

    // ---------- 详情 ----------

    // 一部片的详细信息，包括播放线路和每一集
    public static VodApi.Detail detail(Context c, SiteSpec s, String vodId) throws Throwable {
        return detail(c, s, vodId, "");
    }

    // 首次按首页条目的 ID 取详情；如果首页爬虫给的 ID 不完整或详情没有播放线路，
    // 再按片名搜索一次，用搜索结果里的真实 ID 重试，避免首页条目点进去直接失败。
    public static VodApi.Detail detail(Context c, SiteSpec s, String vodId, String title) throws Throwable {
        Throwable firstError = null;
        try {
            VodApi.Detail d = detailById(c, s, vodId);
            if (d != null && !d.lines.isEmpty()) return d;
        } catch (Throwable e) {
            firstError = e;
        }

        if (title != null && !title.trim().isEmpty()) {
            try {
                VodApi.Page found = search(c, s, title.trim());
                Movie best = null;
                for (Movie m : found.movies) {
                    if (m.title.equalsIgnoreCase(title.trim()) && m.vodId != null && !m.vodId.isEmpty()) {
                        best = m;
                        break;
                    }
                }
                if (best == null) {
                    for (Movie m : found.movies) {
                        if (m.vodId != null && !m.vodId.isEmpty()) { best = m; break; }
                    }
                }
                if (best != null) {
                    VodApi.Detail d = detailById(c, s, best.vodId);
                    if (d != null && !d.lines.isEmpty()) return d;
                    if (d != null) return d;
                }
            } catch (Throwable retryError) {
                if (firstError == null) firstError = retryError;
            }
        }

        if (firstError != null) throw firstError;
        return detailById(c, s, vodId);
    }

    private static VodApi.Detail detailById(Context c, SiteSpec s, String vodId) throws Throwable {
        if (vodId == null || vodId.trim().isEmpty()) throw new Exception("这个首页条目没有影片编号");
        JSONObject o;
        String base = null;
        if (!s.isSpider()) {
            String url = s.api + (s.api.contains("?") ? "&" : "?")
                    + "ac=detail&ids=" + URLEncoder.encode(vodId, "UTF-8");
            try {
                o = new JSONObject(Net.getText(url));
            } catch (JSONException e) {
                throw new Exception("接口返回的内容不是 JSON");
            }
            base = s.api;
        } else {
            synchronized (SPIDER_LOCK) {
                Object sp = SpiderRuntime.get(c.getApplicationContext(), s);
                o = asJson(SpiderRuntime.call(sp, "detailContent", new ArrayList<String>(Arrays.asList(vodId))));
            }
        }
        return VodApi.parseDetail(o, base, vodId);
    }

    // ---------- 播放地址 ----------

    // 把「某一集的编号」换成真正能播放的地址。flag 是线路名。
    public static PlayInfo play(Context c, SiteSpec s, String flag, String id) throws Throwable {
        if (!s.isSpider()) {
            if (!id.startsWith("http")) {
                throw new Exception("这个片源给的不是播放地址：" + shorten(id));
            }
            return new PlayInfo(id, new HashMap<String, String>());
        }

        JSONObject o;
        synchronized (SPIDER_LOCK) {
            Object sp = SpiderRuntime.get(c.getApplicationContext(), s);
            o = asJson(SpiderRuntime.call(sp, "playerContent", flag, id, new ArrayList<String>()));
        }

        String url = optStr(o, "url");
        int parse = o.optInt("parse", 0);
        if (url.isEmpty()) throw new Exception("爬虫没有返回播放地址");
        if (parse == 1) {
            throw new Exception("这个片源需要打开网页嗅探才能拿到地址（parse=1），暂时不支持，换一条线路试试");
        }
        String prefix = optStr(o, "playUrl");
        if (!prefix.isEmpty() && !url.startsWith("http")) url = prefix + url;

        Map<String, String> headers = new HashMap<>();
        Object h = o.opt("header");
        try {
            JSONObject ho = null;
            if (h instanceof JSONObject) {
                ho = (JSONObject) h;
            } else if (h instanceof String && ((String) h).trim().startsWith("{")) {
                ho = new JSONObject((String) h);
            }
            if (ho != null) {
                Iterator<String> it = ho.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    headers.put(k, ho.optString(k));
                }
            }
        } catch (JSONException e) {
            // 请求头写得不规范就忽略，不影响播放
        }
        return new PlayInfo(url, headers);
    }

    // ---------- 小工具 ----------

    private static JSONObject asJson(Object r) throws Exception {
        String s = r == null ? "" : r.toString().trim();
        if (s.isEmpty()) return new JSONObject("{}");
        try {
            return new JSONObject(s);
        } catch (JSONException e) {
            throw new Exception("爬虫返回的内容不是 JSON：" + shorten(s));
        }
    }

    private static String optStr(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    private static String shorten(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
