package com.alan.moviebox;

// 一部影片的数据。以后接上接口，只要把接口返回的内容装进这个类就行，界面不用改。
public class Movie {
    public final String title;   // 片名
    public final String rating;  // 评分
    public final String tag;     // 角标
    public final int colorIdx;   // 主题色编号（没有真海报时用来配色）
    public final String meta;    // 类型、时长等一行信息
    public final String desc;    // 简介

    public Movie(String title, String rating, String tag, int colorIdx, String meta, String desc) {
        this.title = title;
        this.rating = rating;
        this.tag = tag;
        this.colorIdx = colorIdx;
        this.meta = meta;
        this.desc = desc;
    }
}
