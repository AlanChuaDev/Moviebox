# Moviebox optimization patch

This patch is intentionally small and keeps the existing UI and source grouping.

## Changes

- Poster downloads now use a dedicated 3-thread executor instead of competing with API requests in `Net.POOL`.
- Concurrent requests for the same poster at the same target size are merged into one download and all waiting views receive the bitmap.
- Poster cache keys include a width bucket so a small decoded bitmap is not reused for a larger poster.
- Search result rendering is bounded to 12 results per site and 180 results overall (previously 20 per site and 300 overall) to reduce view creation, bitmap pressure, and scrolling overhead.
- Existing source grouping is retained.

## Validation

The source patch was inspected, but a Gradle build was not run in this environment because the `gradle` command and Gradle wrapper are not present in the supplied project. Please use the repository's GitHub Actions build workflow after uploading and check the build result before installing the APK.

## Next recommended step

The homepage category mismatch needs to be debugged against the actual API configuration and its returned JSON. Different APIs use different category names and sometimes different response structures, so do not hard-code a category mapping without checking the configured API's response.
