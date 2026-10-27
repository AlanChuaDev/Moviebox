package com.github.catvod.crawler;

import android.content.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 爬虫基类。影视仓用的爬虫 jar 都是继承这个类写的，jar 里不带它，需要宿主 App 提供。
// 包名、类名、方法签名都必须和影视仓一致，所以这里不能改。
public abstract class Spider {
    public void init(Context context) throws Exception {
    }

    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    public String homeContent(boolean filter) throws Exception {
        return "";
    }

    public String homeVideoContent() throws Exception {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }

    public boolean manualVideoCheck() {
        return false;
    }

    public boolean isVideoFormat(String url) {
        return false;
    }

    public Object[] proxyLocal(Map<String, String> params) {
        return null;
    }

    public void destroy() {
    }
}
