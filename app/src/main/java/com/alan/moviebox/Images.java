package com.alan.moviebox;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

// 海报图片加载：后台下载 + 缩小到够用的尺寸 + 内存缓存。必须在主线程调用 load。
public class Images {
    public interface Callback {
        void onLoaded(Bitmap b);
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    // reqW：需要的宽度（像素）。图片比这大很多时会缩小后再解码，省内存。
    public static void load(final String url, final int reqW, final Callback cb) {
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            cb.onLoaded(hit);
            return;
        }
        Net.POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                byte[] data = Net.getBytes(url, 4 * 1024 * 1024);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int s = 1;
                while (o.outWidth / (s * 2) >= reqW) s *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = s;
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            } catch (Throwable e) {
                // 图片下载或解码失败：继续显示图案海报
            }
            final Bitmap result = bmp;
            if (result != null) {
                CACHE.put(url, result);
                Net.MAIN.post(() -> cb.onLoaded(result));
            }
        });
    }
}
