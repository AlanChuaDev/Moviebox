package com.alan.moviebox;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

// 保存用户添加的接口（名称 + 地址），存在手机/电视本地的 SharedPreferences 里。
// 手机网页（后台线程）和电视界面（主线程）都会用到，所以方法都加了 synchronized。
public class SourceStore {
    public static class Source {
        public final String name;
        public final String url;

        public Source(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private static final String KEY = "sources";
    private final SharedPreferences sp;

    public SourceStore(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("moviebox", Context.MODE_PRIVATE);
    }

    public synchronized List<Source> list() {
        List<Source> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Source(o.optString("name"), o.optString("url")));
            }
        } catch (Exception e) {
            // 数据损坏时当作空列表
        }
        return out;
    }

    // 添加成功返回 null，失败返回要显示给用户的原因
    public synchronized String add(String name, String url) {
        url = url == null ? "" : url.trim();
        name = name == null ? "" : name.trim();
        if (url.isEmpty()) return "请输入接口地址";
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return "接口地址要以 http:// 或 https:// 开头";
        }
        if (url.length() > 2000) return "接口地址太长了";
        if (name.length() > 40) name = name.substring(0, 40);

        List<Source> all = list();
        for (Source s : all) {
            if (s.url.equals(url)) return "这个接口已经添加过了";
        }
        if (name.isEmpty()) name = "接口 " + (all.size() + 1);
        all.add(new Source(name, url));
        save(all);
        return null;
    }

    // 按地址删除（不按位置删，避免手机和电视同时操作时删错）
    public synchronized boolean remove(String url) {
        List<Source> all = list();
        boolean removed = false;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).url.equals(url)) {
                all.remove(i);
                removed = true;
            }
        }
        if (removed) save(all);
        return removed;
    }

    // ---------- 当前使用的接口（已经识别出来、可以直接读片子的那个地址） ----------

    public synchronized void setActive(String name, String api) {
        sp.edit().putString("activeName", name).putString("activeApi", api).apply();
    }

    public synchronized String getActiveApi() {
        String s = sp.getString("activeApi", "");
        return s.isEmpty() ? null : s;
    }

    public synchronized String getActiveName() {
        return sp.getString("activeName", "");
    }

    // ---------- 站点池：选中站点时，同一份配置里的所有站点，全站搜索时会逐个搜 ----------

    public synchronized void setPool(List<VodApi.Option> pool) {
        try {
            JSONArray a = new JSONArray();
            for (VodApi.Option o : pool) {
                JSONObject j = new JSONObject();
                j.put("name", o.name);
                j.put("spec", o.url);
                a.put(j);
            }
            sp.edit().putString("pool", a.toString()).apply();
        } catch (Exception e) {
            // 写入失败就保留旧的
        }
    }

    public synchronized List<VodApi.Option> getPool() {
        List<VodApi.Option> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("pool", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                out.add(new VodApi.Option(j.optString("name"), j.optString("spec")));
            }
        } catch (Exception e) {
            // 数据损坏时当作空
        }
        return out;
    }

    public synchronized void clearActive() {
        sp.edit().remove("activeName").remove("activeApi").apply();
    }

    private void save(List<Source> all) {
        try {
            JSONArray a = new JSONArray();
            for (Source s : all) {
                JSONObject o = new JSONObject();
                o.put("name", s.name);
                o.put("url", s.url);
                a.put(o);
            }
            sp.edit().putString(KEY, a.toString()).apply();
        } catch (Exception e) {
            // JSON 写入基本不会失败，忽略
        }
    }
}
