# MovieBox

学习用的电视端影视 App（仿影视仓的界面结构）。

## 阶段
1. 首页界面（当前）：标题、时钟、分类标签、功能按钮、海报墙，遥控器可操作
2. 详情页 + 播放器
3. 配置和接口层
4. 搜索、历史、收藏
5. 打磨

## 文件说明
- `app/src/main/java/com/alan/moviebox/MainActivity.java`：首页界面
- `app/src/main/java/com/alan/moviebox/Movie.java`：一部影片的数据
- `app/src/main/java/com/alan/moviebox/SampleData.java`：示例（假）数据
- `app/src/main/java/com/alan/moviebox/SettingsActivity.java`：设置页（二维码 + 接口列表）
- `app/src/main/java/com/alan/moviebox/ConfigServer.java`：电视上的小网页服务，手机扫码后在网页里添加 / 删除接口
- `app/src/main/java/com/alan/moviebox/SourceStore.java`：保存接口列表
- `app/src/main/assets/config.html`：手机上打开的网页
- `.github/workflows/build.yml`：在云端自动编译 APK

## 设置：扫码添加接口
首页右上角「设置」→ 手机和电视连同一个 Wi-Fi，用手机扫二维码 → 在网页里填接口地址。
二维码里带一次性口令，离开设置页或点「重新生成二维码」后旧码失效。
目前只负责保存接口，读取和使用接口是第 3 阶段的内容。
