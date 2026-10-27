package com.alan.moviebox;

import java.util.ArrayList;
import java.util.List;

// 示例（假）数据。第 3 阶段会换成从接口读取。
public class SampleData {

    // 每个主题：{海报底色, 海报图案色, 大背景太阳色, 远山色, 近山色}
    public static final int[][] THEMES = {
        {0xFF3A5A6A, 0xFFE0803A, 0xFFF0A35C, 0xFF12262D, 0xFF0B171B},
        {0xFF5A2F2A, 0xFFF0A35C, 0xFFE8736A, 0xFF2A1618, 0xFF160B0D},
        {0xFF1F2A44, 0xFFE0803A, 0xFF7FA6FF, 0xFF141C33, 0xFF0A0F1E},
        {0xFF2F4A3D, 0xFFD8D3C8, 0xFFB8E0A0, 0xFF14281F, 0xFF0A150F},
        {0xFF3D3A52, 0xFFF5F1EA, 0xFFD9C8FF, 0xFF1B1930, 0xFF0D0C1A},
        {0xFF4A2F55, 0xFFFFB3D6, 0xFFFF8FC0, 0xFF241530, 0xFF12091A},
        {0xFF2A4F5A, 0xFF9FF0E4, 0xFF7FE8D8, 0xFF10282E, 0xFF081419},
        {0xFF5A4A2A, 0xFFFFD49A, 0xFFFFC870, 0xFF2A2210, 0xFF16110A},
        {0xFF3A2F5A, 0xFFA9BAFF, 0xFF8FA0FF, 0xFF1A1634, 0xFF0C0A1B},
        {0xFF5A2A3A, 0xFFFFB0A0, 0xFFFF9A8A, 0xFF2A1219, 0xFF15080C}
    };

    public static int[] theme(int idx) {
        return THEMES[Math.abs(idx) % THEMES.length];
    }

    static final String PLACEHOLDER = "这是一条示例数据，接入接口后这里会显示真实的简介。";

    static List<Movie> movies() {
        List<Movie> l = new ArrayList<>();
        l.add(new Movie("大雄兔", "8.2", "示例", 0, "动画 · 短片 · 10 分钟",
                "一只温和的大兔子，和三个爱捣蛋的小家伙之间的故事。"));
        l.add(new Movie("辛特尔", "7.9", "示例", 1, "动画 · 短片 · 15 分钟",
                "一个女孩和一条小龙，穿越雪山与荒原的旅程。"));
        l.add(new Movie("钢铁之泪", "6.8", "示例", 2, "科幻 · 短片 · 12 分钟",
                "在未来的城市里，一群人试图弥补过去留下的遗憾。"));
        l.add(new Movie("大象之梦", "6.5", "示例", 3, "动画 · 短片 · 11 分钟",
                "两个同伴，在一座奇异的机械世界里展开冒险。"));
        l.add(new Movie("月球旅行记", "7.6", "示例", 4, "奇幻 · 默片 · 14 分钟",
                "一群学者乘着炮弹飞向月球的经典默片。"));
        l.add(new Movie("诺斯费拉图", "7.9", "示例", 5, "恐怖 · 默片 · 94 分钟",
                "一部影响深远的经典恐怖默片。"));
        l.add(new Movie("大都会", "8.1", "示例", 6, "科幻 · 默片 · 148 分钟",
                "一座分成上下两层的未来都市里发生的故事。"));
        l.add(new Movie("将军号", "8.0", "示例", 7, "喜剧 · 默片 · 78 分钟",
                "一位火车司机为了爱情，追回被抢走的火车。"));
        l.add(new Movie("示例电影 09", "7.0", "示例", 8, "示例 · 占位", PLACEHOLDER));
        l.add(new Movie("示例电影 10", "7.5", "示例", 9, "示例 · 占位", PLACEHOLDER));
        return l;
    }

    static List<Movie> series() {
        List<Movie> l = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String n = i + 1 < 10 ? "0" + (i + 1) : String.valueOf(i + 1);
            l.add(new Movie("示例剧集 " + n, "7." + i, "示例", i + 3, "剧集 · 全 12 集", PLACEHOLDER));
        }
        return l;
    }

    static List<Movie> variety() {
        List<Movie> l = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String n = i + 1 < 10 ? "0" + (i + 1) : String.valueOf(i + 1);
            l.add(new Movie("示例综艺 " + n, "6." + i, "示例", i + 6, "综艺 · 每周更新", PLACEHOLDER));
        }
        return l;
    }

    // 主页：电影和剧集各取前 5 个
    static List<Movie> home() {
        List<Movie> l = new ArrayList<>();
        l.addAll(movies().subList(0, 5));
        l.addAll(series().subList(0, 5));
        return l;
    }

    // 按标签页编号取数据：0 主页，1 电影，2 剧集，3 综艺
    public static List<Movie> forTab(int tab) {
        switch (tab) {
            case 1: return movies();
            case 2: return series();
            case 3: return variety();
            default: return home();
        }
    }
}
