package com.alan.moviebox;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 网络小工具：后台线程池 + 下载文字 / 字节 + 回到主线程。
// 网络请求一律放到 POOL 里跑，结果用 MAIN 送回界面线程。
public class Net {
    public static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    public static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String UA = "Mozilla/5.0 (Linux; Android 10; TV) MovieBox/0.1";

    public static String getText(String url) throws IOException {
        byte[] b = getBytes(url, 5 * 1024 * 1024);
        String s = new String(b, "UTF-8");
        if (s.startsWith("﻿")) s = s.substring(1);  // 去掉文件开头的 BOM
        return s;
    }

    // 下载一个地址；手动跟随重定向（包括 http 和 https 之间的跳转）
    public static byte[] getBytes(String url, int maxBytes) throws IOException {
        String cur = url;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("重定向没有给出新地址");
                cur = new URL(new URL(cur), loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("服务器返回 " + code);
            }
            InputStream in = c.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > maxBytes) throw new IOException("内容太大");
                }
                return out.toByteArray();
            } finally {
                in.close();
                c.disconnect();
            }
        }
        throw new IOException("重定向次数太多");
    }
}
