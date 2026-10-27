package com.alan.moviebox;

import android.content.Context;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

// 电视上的小网页服务：手机扫码后打开这里的网页，在网页里添加 / 删除接口。
// 每次启动都会生成一个随机口令（token），二维码里带着它；没有口令的请求一律拒绝，
// 这样同一个 Wi-Fi 下的其他人猜不到地址也改不了你的接口。
public class ConfigServer extends NanoHTTPD {
    public interface Listener {
        void onChanged(String msg);
    }

    private static final int FIRST_PORT = 8899;

    private final Context ctx;
    private final SourceStore store;
    private final String token;
    private final Listener listener;
    private String page;

    private ConfigServer(int port, Context ctx, SourceStore store, String token, Listener l) {
        super(port);
        this.ctx = ctx.getApplicationContext();
        this.store = store;
        this.token = token;
        this.listener = l;
    }

    // 从 8899 开始找一个没被占用的端口启动；全都失败返回 null
    public static ConfigServer startNew(Context c, SourceStore st, String token, Listener l) {
        for (int p = FIRST_PORT; p < FIRST_PORT + 10; p++) {
            ConfigServer s = new ConfigServer(p, c, st, token, l);
            try {
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                return s;
            } catch (IOException e) {
                // 端口被占用，换下一个
            }
        }
        return null;
    }

    // 电视在局域网里的 IPv4 地址；没连网返回 null
    public static String localIp() {
        try {
            String fallback = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        String n = ni.getName();
                        if (n.startsWith("wlan") || n.startsWith("eth")) return a.getHostAddress();
                        if (fallback == null) fallback = a.getHostAddress();
                    }
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            if (session.getMethod() == Method.POST) {
                session.parseBody(new HashMap<String, String>());
            }
            Map<String, String> q = session.getParms();
            if (!token.equals(q.get("k"))) {
                return text(Response.Status.FORBIDDEN, "二维码已失效，请在电视上重新扫码。");
            }

            String uri = session.getUri();
            boolean post = session.getMethod() == Method.POST;
            if ("/".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page());
            }
            if ("/api/list".equals(uri)) {
                JSONObject o = new JSONObject();
                JSONArray a = new JSONArray();
                for (SourceStore.Source s : store.list()) {
                    JSONObject i = new JSONObject();
                    i.put("name", s.name);
                    i.put("url", s.url);
                    a.put(i);
                }
                o.put("ok", true);
                o.put("items", a);
                return json(o);
            }
            if (post && "/api/add".equals(uri)) {
                String err = store.add(q.get("name"), q.get("url"));
                if (err != null) return json(result(false, err));
                listener.onChanged("手机已添加一个接口");
                return json(result(true, "已添加到电视"));
            }
            if (post && "/api/delete".equals(uri)) {
                boolean done = store.remove(q.get("url") == null ? "" : q.get("url"));
                if (done) listener.onChanged("手机已删除一个接口");
                return json(result(done, done ? "已删除" : "这个接口已经不存在了"));
            }
            return text(Response.Status.NOT_FOUND, "Not found");
        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "出错了：" + e.getMessage());
        }
    }

    private Response text(Response.IStatus status, String s) {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", s);
    }

    private Response json(JSONObject o) {
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString());
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private JSONObject result(boolean ok, String msg) throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", ok);
        o.put("msg", msg);
        return o;
    }

    // 网页放在 assets/config.html 里，第一次用到时读进内存
    private synchronized String page() throws IOException {
        if (page == null) {
            InputStream in = ctx.getAssets().open("config.html");
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                page = out.toString("UTF-8");
            } finally {
                in.close();
            }
        }
        return page;
    }
}
