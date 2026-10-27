package com.alan.moviebox;

import org.json.JSONObject;

// 一个站点的完整规格。type 1 只需要 api 网址；
// type 3（爬虫站点）还需要 jar 地址和 ext 参数。会被存成一段 JSON 文字。
public class SiteSpec {
    public final int type;
    public final String api;   // type 1：接口网址；type 3：jar 里的类名，例如 csp_Xxx
    public final String jar;   // type 3：爬虫 jar 的完整下载地址
    public final String ext;   // type 3：传给爬虫的参数
    public final boolean searchable;  // 配置里标明可以搜索的站点才参加全站搜索

    public SiteSpec(int type, String api, String jar, String ext) {
        this(type, api, jar, ext, true);
    }

    public SiteSpec(int type, String api, String jar, String ext, boolean searchable) {
        this.type = type;
        this.api = api == null ? "" : api;
        this.jar = jar == null ? "" : jar;
        this.ext = ext == null ? "" : ext;
        this.searchable = searchable;
    }

    public boolean isSpider() {
        return type == 3;
    }

    @Override
    public String toString() {
        try {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("api", api);
            o.put("jar", jar);
            o.put("ext", ext);
            o.put("searchable", searchable);
            return o.toString();
        } catch (Exception e) {
            return api;
        }
    }

    // 兼容旧数据：不是 JSON 的文字，当作 type 1 的接口网址
    public static SiteSpec parse(String s) {
        if (s != null && s.startsWith("{")) {
            try {
                JSONObject o = new JSONObject(s);
                return new SiteSpec(o.optInt("type", 1), o.optString("api"), o.optString("jar"),
                        o.optString("ext"), o.optBoolean("searchable", true));
            } catch (Exception e) {
                // 解析失败就按普通网址处理
            }
        }
        return new SiteSpec(1, s, "", "");
    }
}
