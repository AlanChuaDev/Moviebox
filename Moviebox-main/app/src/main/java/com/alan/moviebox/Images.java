package com.alan.moviebox;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 海报图片加载：独立后台线程池、按尺寸缓存、同一图片请求合并。
// 必须在主线程调用 load；加载成功后回调也始终在主线程执行。
public class Images {
    public interface Callback {
        void onLoaded(Bitmap b);
    }

    // 图片下载与接口请求分池，避免大量海报占满 Net.POOL。
    private static final ExecutorService IMAGE_POOL = Executors.newFixedThreadPool(3);
    private static final Object LOCK = new Object();
    private static final ConcurrentHashMap<String, List<Callback>> IN_FLIGHT = new ConcurrentHashMap<>();

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    // 不同尺寸使用不同缓存键，避免小尺寸先加载后被大海报复用而模糊。
    private static String cacheKey(String url, int reqW) {
        int bucket = Math.max(64, ((Math.max(1, reqW) + 63) / 64) * 64);
        return url + "#w=" + bucket;
    }

    public static void load(final String url, final int reqW, final Callback cb) {
        if (url == null || url.trim().isEmpty() || cb == null) return;
        final String key = cacheKey(url, reqW);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            Net.MAIN.post(() -> cb.onLoaded(hit));
            return;
        }

        synchronized (LOCK) {
            List<Callback> waiting = IN_FLIGHT.get(key);
            if (waiting != null) {
                waiting.add(cb);
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(cb);
            IN_FLIGHT.put(key, waiting);
        }

        IMAGE_POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                byte[] data = Net.getBytes(url, 4 * 1024 * 1024);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int targetW = Math.max(1, reqW);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            } catch (Throwable ignored) {
                // 下载或解码失败时保留原有的占位海报，不中断页面。
            }

            final Bitmap result = bmp;
            final List<Callback> callbacks;
            synchronized (LOCK) {
                callbacks = IN_FLIGHT.remove(key);
            }
            if (result != null) {
                CACHE.put(key, result);
                if (callbacks != null) {
                    Net.MAIN.post(() -> {
                        for (Callback callback : callbacks) {
                            try { callback.onLoaded(result); } catch (Throwable ignored) { }
                        }
                    });
                }
            }
        });
    }
}
