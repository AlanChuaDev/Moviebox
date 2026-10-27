# MovieBox

学习用的电视端影视 App（仿影视仓的界面结构）。

## 阶段
1. 首页界面（当前）：标题、时钟、分类标签、功能按钮、海报墙，遥控器可操作
2. 详情页 + 播放器（已完成，待实机测试）
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
在电视上选中一个接口按「确定键」使用，长按「确定键」删除（连续长按两次）。

## 接口怎么读
- 支持 **type 1**（苹果CMS 的 JSON 接口，`.../api.php/provide/vod/` 这种）。
- 添加的地址可以是：接口本身、影视仓那种站点配置（`sites`）、或线路索引（`urls`）。
  选中后程序会自动识别：线路索引先选线路，再从里面选一个 type 1 的站点。
- 支持 **type 3 中用 jar 的爬虫站点**（`api` 是 `csp_` 开头）：运行时下载配置里的 `spider` jar，
  用 `SpiderRuntime.java` 加载，再通过 `Catalog.java` 调用爬虫的首页 / 分类方法。
  `com/github/catvod/crawler/` 下的 `Spider`、`SpiderDebug` 是影视仓约定的基类，包名和签名不能改。
  这一块是最小验证，只读首页和分类；详情、搜索、播放还没做。
- 用 js / py 脚本的 type 3 站点暂时不支持。
- 加载失败时首页会弹出对话框，写明具体原因（比如缺某个类），方便排查。
- 首页的「电影 / 剧集 / 综艺」标签，按接口返回的分类名字自动对应。
- 相关文件：`VodApi.java`（读取和解析）、`Net.java`（联网）、`Images.java`（海报图片）。

## 测试接口
`testapi/vod.json` 是一个格式和苹果CMS 一样的测试接口，里面是 Blender 基金会的开源短片。
上传到仓库后，在设置里添加下面这个地址就能测试：
`https://raw.githubusercontent.com/AlanChuaDev/Moviebox/main/testapi/vod.json`

## 详情页和播放器（第 2 阶段）
- 首页点海报或「详情」进入详情页；点「播放」进入详情页并自动开始播第一集。
- 详情页：海报、信息、简介、播放线路、选集（每行 6 集），确定键选择。
- 播放器（Media3/ExoPlayer）：支持常见渐进式媒体与 HLS，并加入 DASH、SmoothStreaming 模块；实际可播放格式仍取决于视频源、编码和设备解码能力。
- 遥控器：确定暂停 / 继续，左右快退 / 快进，上下切换集；菜单键或长按确定可打开播放器设置。
- 播放器设置：画面适应 / 固定宽高 / 拉伸、音轨选择、字幕轨选择、播放速度（0.5×–2×）和播放信息。
- type 1 的播放地址直接播放；type 3 通过爬虫的 playerContent 换取真实地址（含请求头）。
- 需要网页嗅探的片源（parse=1）暂时不支持，会提示换线路。
- 相关文件：`DetailActivity.java`、`PlayerActivity.java`、`UiActivity.java`（两个页面共用的小工具）。

## 搜索
- 首页「搜索」标签打开搜索页：电视上直接输入，或手机扫页面上的二维码，在网页里输入片名发送到电视。
- 全站搜索：在设置里选定站点时，同一份配置里的所有站点会一起存下来；搜索时当前站点排第一，
  其他标明 `searchable` 的站点一起搜，结果下面标出来自哪个站点，点进去用它所属站点的详情和播放。
- 豆瓣热播榜这类条目（`vod_id` 以 `msearch:` 开头）不是真正的片子，点它会直接用片名去搜索。
- type 1 用 `ac=detail&wd=关键词`，type 3 调用爬虫的 `searchContent`。
- 首页栏目标题后面会显示当前使用的站点名。

## YouTube Stage 3 (Guest UI + playback improvements)

- YouTube 首页改成大屏布局：搜索框、分类快捷入口、本机最近观看记录；配置 API Key 后尝试显示马来西亚热门视频。
- 搜索结果读取 YouTube Data API v3 返回的缩略图，并显示封面、标题、频道名称。
- 影片通过 YouTube 官方嵌入式播放器播放；部分视频可能禁用嵌入播放，仍可能无法在应用内播放。
- 最近观看记录最多保存 50 条，使用本机 SharedPreferences，不会因共用 API Key 自动共享给其他设备。
- 当前开发版仍需要在「API 设置（开发版）」配置 Key。正式发布若要让所有顾客免申请 Key，需要由应用维护者统一配置并做好 API Key 限制；不能把真实 Key 直接提交到公开仓库。
- YouTube 登录、订阅与云端观看历史同步未实现，需要单独的 OAuth 授权流程。

## Build / validation

ZIP 文件结构和 Java 源码已进行静态检查；当前执行环境没有 Gradle/Android SDK，无法在此直接编译 APK。请通过仓库已有的 GitHub Actions 工作流完成实际编译，并在 Android TV 设备上验证遥控器、播放器轨道选择和 YouTube 嵌入播放。
