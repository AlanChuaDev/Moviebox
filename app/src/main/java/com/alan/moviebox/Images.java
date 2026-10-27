package com.alan.moviebox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 海报加载：独立线程池、请求合并、内存缓存 + 磁盘缓存。图片失败不影响其他影片。
public class Images {
    public interface Callback {
        void onLoaded(Bitmap b);
    }

    private static final ExecutorService IMAGE_POOL = Executors.newFixedThreadPool(3);
    private static final Object LOCK = new Object();
    private static final ConcurrentHashMap<String, List<Callback>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    private static String cacheKey(String url, int reqW) {
        int bucket = Math.max(64, ((Math.max(1, reqW) + 63) / 64) * 64);
        return url + "#w=" + bucket;
    }

    public static void load(Context context, final String url, final int reqW, final Callback cb) {
        if (context == null || url == null || url.trim().isEmpty() || cb == null) return;
        final Context app = context.getApplicationContext();
        final String key = cacheKey(url, reqW);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            Net.MAIN.post(() -> cb.onLoaded(hit));
            return;
        }

        synchronized (LOCK) {
            List<Callback> waiting = IN_FLIGHT.get(key);
            if (waiting != null) { waiting.add(cb); return; }
            waiting = new ArrayList<>();
            waiting.add(cb);
            IN_FLIGHT.put(key, waiting);
        }

        IMAGE_POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                File file = diskFile(app, url);
                byte[] data = file.isFile() ? readFile(file, 4 * 1024 * 1024) : null;
                if (data == null || data.length == 0) {
                    data = Net.getBytes(url, 4 * 1024 * 1024);
                    writeFile(file, data);
                } else {
                    file.setLastModified(System.currentTimeMillis());
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                int targetW = Math.max(1, reqW);
                int sample = 1;
                while (options.outWidth > 0 && options.outWidth / (sample * 2) >= targetW) sample *= 2;
                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            } catch (Throwable ignored) {
                // 保留占位海报；其他图片仍会继续加载。
            }

            final Bitmap result = bmp;
            final List<Callback> callbacks;
            synchronized (LOCK) { callbacks = IN_FLIGHT.remove(key); }
            if (result != null) {
                CACHE.put(key, result);
                if (callbacks != null) Net.MAIN.post(() -> {
                    for (Callback callback : callbacks) {
                        try { callback.onLoaded(result); } catch (Throwable ignored) { }
                    }
                });
            }
        });
    }

    private static File diskFile(Context context, String url) throws Exception {
        File dir = new File(context.getCacheDir(), "movie-posters");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, md5(url) + ".img");
    }

    private static byte[] readFile(File file, int maxBytes) {
        if (!file.isFile() || file.length() <= 0 || file.length() > maxBytes) {
            if (file.exists()) file.delete();
            return null;
        }
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > maxBytes) return null;
            }
            return out.toByteArray();
        } catch (Exception e) { return null; }
    }

    private static void writeFile(File file, byte[] data) {
        if (data == null || data.length == 0 || data.length > 4 * 1024 * 1024) return;
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
            out.flush();
        } catch (Exception e) { temp.delete(); return; }
        if (!temp.renameTo(file)) {
            try {
                if (file.exists()) file.delete();
                temp.renameTo(file);
            } catch (Exception ignored) { }
        }
        trimDiskCache(file.getParentFile(), 48L * 1024L * 1024L);
    }

    private static void trimDiskCache(File dir, long maxBytes) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) if (f.isFile() && !f.getName().endsWith(".tmp")) total += f.length();
        if (total <= maxBytes) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            if (total <= maxBytes) break;
            if (!f.isFile() || f.getName().endsWith(".tmp")) continue;
            long size = f.length();
            if (f.delete()) total -= size;
        }
    }

    private static String md5(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("MD5").digest(value.getBytes("UTF-8"));
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
}
