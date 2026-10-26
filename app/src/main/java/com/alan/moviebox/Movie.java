package com.alan.moviebox;

// 一部影片的数据：标题、评分、角标、海报颜色编号
public class Movie {
    public final String title;
    public final String rating;
    public final String tag;
    public final int colorIdx;

    public Movie(String title, String rating, String tag, int colorIdx) {
        this.title = title;
        this.rating = rating;
        this.tag = tag;
        this.colorIdx = colorIdx;
    }
}
