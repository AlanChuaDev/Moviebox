# Moviebox YouTube page update

- Removed the YouTube Data API search flow, API-key entry dialog, and API-key dependent empty screen.
- YouTube tab now opens YouTube's own TV-oriented website in a WebView so the page can show real video thumbnails and content without a separately configured key.
- Added basic TV navigation controls (home, refresh, sign-in/Guest, back), cookies, and page-load/error status.
- The sign-in/Guest action tries to launch the official YouTube app; QR sign-in and Guest selection depend on that app being installed and supported. Google may block login inside embedded WebViews.
- Legacy locally stored Data API key is removed when this version opens.

Validation: source references and archive integrity checked. Android compilation and actual TV-device behavior still need verification through the project's GitHub Actions workflow and on-device testing.
