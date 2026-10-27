package com.alan.moviebox;

import android.content.Context;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;

// 统一的「读片子」入口：type 1 直接读接口，type 3 通过爬虫 jar 读。
// 爬虫返回的 JSON 格式和接口一样，所以都交给 VodApi.parse 解析。
public class Catalog {
    // 爬虫不一定能同时被多个线程调用，这里统一排队
    private static final Object SPIDER_LOCK = new Object();

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

    private static JSONObject asJson(Object r) throws Exception {
        String s = r == null ? "" : r.toString().trim();
        if (s.isEmpty()) return new JSONObject("{}");
        try {
            return new JSONObject(s);
        } catch (JSONException e) {
            throw new Exception("爬虫返回的内容不是 JSON：" + (s.length() > 80 ? s.substring(0, 80) + "…" : s));
        }
    }
}
