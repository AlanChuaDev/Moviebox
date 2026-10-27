package com.alan.moviebox;

import android.content.Context;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

// 爬虫运行环境：下载站点配置里的 jar，运行时加载，再用 jar 里的类去取数据。
// 所有方法都会联网或执行第三方代码，必须在后台线程调用。
public class SpiderRuntime {
    private static final Map<String, ClassLoader> LOADERS = new HashMap<>();
    private static final Map<String, Object> SPIDERS = new HashMap<>();

    // 取得（必要时下载、加载、初始化）一个站点对应的爬虫对象
    public static synchronized Object get(Context ctx, SiteSpec site) throws Throwable {
        if (site.jar.isEmpty()) throw new Exception("这个站点没有爬虫 jar 地址");
        String key = site.jar + "|" + site.api + "|" + site.ext;
        Object cached = SPIDERS.get(key);
        if (cached != null) return cached;

        ClassLoader cl = loader(ctx, site.jar);
        String cls = site.api.startsWith("csp_") ? site.api.substring(4) : site.api;
        Class<?> c = cl.loadClass("com.github.catvod.spider." + cls);
        Object sp = c.newInstance();
        call(sp, "init", ctx, site.ext);
        SPIDERS.put(key, sp);
        return sp;
    }

    // 按名字和参数个数，用反射调用爬虫的方法。爬虫自己抛出的错误原样抛出来。
    public static Object call(Object sp, String name, Object... args) throws Throwable {
        for (Method m : sp.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
                try {
                    return m.invoke(sp, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
            }
        }
        throw new NoSuchMethodException(sp.getClass().getName() + "." + name);
    }

    private static ClassLoader loader(Context ctx, String jarUrl) throws Throwable {
        ClassLoader cl = LOADERS.get(jarUrl);
        if (cl != null) return cl;

        byte[] data = Net.getBytes(jarUrl, 40 * 1024 * 1024);
        File dir = new File(ctx.getCodeCacheDir(), "spider");
        dir.mkdirs();
        File f = new File(dir, md5(jarUrl) + ".jar");
        if (f.exists()) {
            f.setWritable(true);
            f.delete();
        }
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        f.setReadOnly();  // Android 14 起，被加载的代码文件必须是只读的，否则会抛安全异常

        DexClassLoader dcl = new DexClassLoader(f.getAbsolutePath(), dir.getAbsolutePath(), null, ctx.getClassLoader());

        // 很多爬虫 jar 带一个 Init 类，要先初始化一次；没有就跳过
        try {
            Class<?> init = dcl.loadClass("com.github.catvod.spider.Init");
            init.getMethod("init", Context.class).invoke(null, ctx);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // 这个 jar 没有 Init，正常
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }

        LOADERS.put(jarUrl, dcl);
        return dcl;
    }

    private static String md5(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
