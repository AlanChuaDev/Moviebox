package com.alan.moviebox;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketTimeoutException;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 网络小工具：后台线程池 + 下载文字 / 字节 + 回到主线程。
// 网络请求一律放到 POOL 里跑，结果用 MAIN 送回界面线程。
public class Net {
    private static final String TAG = "MovieBoxNet";
    // Keep network work separate from image decoding; a few more workers prevent one slow call
    // from blocking every other home/search request.
    public static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    public static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String UA = "Mozilla/5.0 (Linux; Android 10; TV) MovieBox/0.1";

    public static String getText(String url) throws IOException {
        byte[] b = getBytes(url, 5 * 1024 * 1024);
        String s = new String(b, "UTF-8");
        if (s.startsWith("﻿")) s = s.substring(1);  // 去掉文件开头的 BOM
        return s;
    }

    // 下载一个地址；手动跟随重定向（包括 http 和 https 之间的跳转）。
    // 最多重试一次短暂网络错误，且每次请求都有明确超时，避免永久卡在“加载中”。
    public static byte[] getBytes(String url, int maxBytes) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            long started = System.currentTimeMillis();
            try {
                byte[] result = getBytesOnce(url, maxBytes);
                Log.d(TAG, "OK " + (System.currentTimeMillis() - started) + "ms " + safeUrl(url));
                return result;
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "FAIL attempt=" + (attempt + 1) + " "
                        + (System.currentTimeMillis() - started) + "ms " + safeUrl(url) + ": " + e.getMessage());
                // Retry only connection/read timeouts and connection failures; don't repeat HTTP 4xx/5xx.
                boolean retryable = e instanceof SocketTimeoutException
                        || e instanceof java.net.ConnectException
                        || e instanceof java.net.UnknownHostException;
                if (attempt == 0 && retryable) {
                    try { Thread.sleep(250L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("请求已取消", interrupted);
                    }
                    continue;
                }
                throw e;
            }
        }
        throw last == null ? new IOException("网络请求失败") : last;
    }

    private static byte[] getBytesOnce(String url, int maxBytes) throws IOException {
        String cur = url;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json,text/plain,*/*");
            int code;
            try {
                code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) throw new IOException("重定向没有给出新地址");
                    cur = new URL(new URL(cur), loc).toString();
                    continue;
                }
                if (code != 200) throw new IOException("服务器返回 " + code);
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
                }
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("重定向次数太多");
    }

    private static String safeUrl(String url) {
        if (url == null) return "(null)";
        // Avoid filling logcat with long query strings that may contain tokens.
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

}
