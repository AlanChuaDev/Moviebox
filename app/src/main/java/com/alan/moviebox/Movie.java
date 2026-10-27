package com.alan.moviebox;

// 一部影片的数据。从接口读到的内容装进这个类，界面只认这个类。
public class Movie {
    public final String title;   // 片名
    public final String rating;  // 评分（没有就是空字符串）
    public final String tag;     // 角标
    public final int colorIdx;   // 主题色编号（没有真海报时用来配色，也用来给背景配色）
    public final String meta;    // 类型、年份、地区等一行信息
    public final String desc;    // 简介
    public final String poster;  // 海报图片地址（示例数据没有，为 null）
    public final String vodId;   // 接口里的影片编号（第 2 阶段做详情页要用）
    public final String playUrl; // 接口里的播放地址串（第 2 阶段做播放器要用）

    public Movie(String title, String rating, String tag, int colorIdx, String meta, String desc) {
        this(title, rating, tag, colorIdx, meta, desc, null, "", "");
    }

    public Movie(String title, String rating, String tag, int colorIdx, String meta, String desc,
                 String poster, String vodId, String playUrl) {
        this.title = title;
        this.rating = rating;
        this.tag = tag;
        this.colorIdx = colorIdx;
        this.meta = meta;
        this.desc = desc;
        this.poster = poster;
        this.vodId = vodId;
        this.playUrl = playUrl;
    }
}
