# Moviebox network stability patch

This patch focuses on the slow or inconsistent interface-loading behavior while preserving the existing TV UI and source grouping.

## Changes

- HTTP requests now use a 5-second connect timeout and an 8-second read timeout, instead of waiting longer on a stalled endpoint.
- Transient connection, DNS, and socket-timeout errors are retried once after a short pause. HTTP error responses are not blindly retried.
- Network request duration and failures are written to Android Logcat under the `MovieBoxNet` tag, with query strings omitted from logged URLs.
- The general network worker pool has six threads; poster downloads remain in their own pool so image work does not directly occupy the same executor as home-page loading.
- Home/category lists are saved in app preferences. On a later launch, a saved list can appear immediately while a background request refreshes it. If refresh fails, the app keeps showing the saved list instead of replacing it with an empty screen.
- Home category metadata is saved with the home cache, helping category tabs avoid an extra home request after a restart.
- Search progress now reports how many sites failed, rather than silently hiding all site errors.
- Existing image cache/request de-duplication and bounded search result counts are retained.

## Validation

The ZIP integrity check passed. This environment does not have Gradle or an Android SDK configured, so an Android build was not run here. Upload the ZIP through the repository's existing GitHub Actions workflow and inspect the build result before installing the APK.
