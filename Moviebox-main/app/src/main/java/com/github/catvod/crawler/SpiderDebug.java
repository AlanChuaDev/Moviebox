package com.github.catvod.crawler;

import android.util.Log;

// 爬虫 jar 会调用它打日志。同样必须和影视仓的类名、方法签名一致。
public class SpiderDebug {
    public static void log(Throwable th) {
        Log.d("MovieBoxSpider", String.valueOf(th), th);
    }

    public static void log(String msg) {
        Log.d("MovieBoxSpider", String.valueOf(msg));
    }
}
