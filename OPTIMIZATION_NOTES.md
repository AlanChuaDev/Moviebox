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
