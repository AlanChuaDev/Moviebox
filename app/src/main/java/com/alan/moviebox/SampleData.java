package com.alan.moviebox;

import java.util.ArrayList;
import java.util.List;

// 第 1 阶段用的示例数据（假数据）。第 3 阶段会换成从接口读取。
public class SampleData {

    // 海报底色：每个 {左上颜色, 右下颜色}
    public static final int[][] COLORS = {
        {0xFF3A1C71, 0xFFD76D77},
        {0xFF134E5E, 0xFF71B280},
        {0xFF42275A, 0xFF734B6D},
        {0xFF232526, 0xFF5A5C5E},
        {0xFF283E51, 0xFF4B79A1},
        {0xFF614385, 0xFF516395},
        {0xFF642B73, 0xFFC6426E},
        {0xFF0F2027, 0xFF2C5364},
        {0xFF654EA3, 0xFFEAAFC8},
        {0xFF8E0E00, 0xFF1F1C18}
    };

    static final String[] MOVIE_NAMES = {
        "大雄兔", "辛特尔", "钢铁之泪", "大象之梦", "月球旅行记",
        "诺斯费拉图", "大都会", "将军号", "示例电影 09", "示例电影 10"
    };
    static final String[] MOVIE_RATINGS = {
        "8.2", "7.9", "6.8", "6.5", "7.6", "7.9", "8.1", "8.0", "7.0", "7.5"
    };

    static List<Movie> movies() {
        List<Movie> list = new ArrayList<>();
        for (int i = 0; i < MOVIE_NAMES.length; i++) {
            list.add(new Movie(MOVIE_NAMES[i], MOVIE_RATINGS[i], "示例", i));
        }
        return list;
    }

    static List<Movie> series() {
        List<Movie> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String n = i + 1 < 10 ? "0" + (i + 1) : String.valueOf(i + 1);
            list.add(new Movie("示例剧集 " + n, "7." + i, "示例", i + 3));
        }
        return list;
    }

    static List<Movie> variety() {
        List<Movie> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String n = i + 1 < 10 ? "0" + (i + 1) : String.valueOf(i + 1);
            list.add(new Movie("示例综艺 " + n, "6." + i, "示例", i + 6));
        }
        return list;
    }

    // 主页：电影和剧集各取前 5 个
    static List<Movie> home() {
        List<Movie> list = new ArrayList<>();
        list.addAll(movies().subList(0, 5));
        list.addAll(series().subList(0, 5));
        return list;
    }

    // 按标签页编号取数据：0 主页，1 热门电影，2 热播剧集，3 热播综艺
    public static List<Movie> forTab(int tab) {
        switch (tab) {
            case 1: return movies();
            case 2: return series();
            case 3: return variety();
            default: return home();
        }
    }
}
