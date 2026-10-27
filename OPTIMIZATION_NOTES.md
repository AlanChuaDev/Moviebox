# Moviebox YouTube page update

- Removed the YouTube Data API search flow, API-key entry dialog, and API-key dependent empty screen.
- YouTube tab now opens YouTube's own TV-oriented website in a WebView so the page can show real video thumbnails and content without a separately configured key.
- Added basic TV navigation controls (home, refresh, sign-in/Guest, back), cookies, and page-load/error status.
- The sign-in/Guest action tries to launch the official YouTube app; QR sign-in and Guest selection depend on that app being installed and supported. Google may block login inside embedded WebViews.
- Legacy locally stored Data API key is removed when this version opens.

Validation: source references and archive integrity checked. Android compilation and actual TV-device behavior still need verification through the project's GitHub Actions workflow and on-device testing.

## 本次更新（2026-10-28）
- 设置页加入电视遥控器可操作的手动接口添加对话框，继续复用 `SourceStore.add()` 做地址校验和去重。
- YouTube 页面加入 `WebChromeClient` 自定义全屏视图回调，支持网页视频全屏和返回退出。
- 尚未在 Android SDK / 设备上完成构建与实机验证；Moviebox ExoPlayer 不直接解析 YouTube 网页流。


## 本次主页接口修复（2026-10-28）
- 首页/搜索统一兼容常见影片字段别名（片名、海报、编号、简介、评分和播放线路），降低不同接口字段差异导致海报为空的情况。
- 首页影片缺少编号时，点击改为按片名进入搜索补全，不再直接失败。
- 详情页先按原编号读取；若详情没有播放线路或读取失败，会按片名搜索站点结果并用真实编号重试。
- 首页本地缓存键升级，避免旧版本缓存里没有海报的影片列表长期覆盖新解析结果。
- 海报增加磁盘缓存（约 48 MB 上限）并继续保留内存缓存和并发请求合并，后续进入首页可减少重复下载。
- YouTubeActivity.java 未作改动。
- 仍需通过 GitHub Actions 构建，并在电视实机检查实际接口；部分站点可能要求特定请求头或网页嗅探，当前播放器不一定支持所有线路。
