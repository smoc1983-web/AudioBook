# Source resilience patch — not published

Base: eaa44d2 / public 4.2.95. Owner chose ordinary checks, without ub-mode.

- GET: two retries, 500/1000 ms backoff, bounded Retry-After, 20-second playlist deadline.
  POST/auth/TLS/malformed data are not automatically retried. WebView retries cancel on navigation/disposal.
- DownloadManager: unavailable cursor preserves active job and progress; unknown counters retain known values.
  Actual missing jobs and terminal failures still show an error.
- HLS/DASH URLs, declared MIME and manifest contents result in explicit unsupported-stream feedback.
  An unsupported playlist cannot fall back to a single audio sample.
- Updater checks retained: channel/tag/version, independent GitHub digest, size/SHA-256,
  Android-returned package/version/certificate, rehashed private installer snapshot.
  Regression assertions added for metadata certificate and versionName mismatch.

Final CI43061821679 SUCCESS, source8ee96ae22eaaf7d0da99e249ca1af098ea5ecdee:
16 JS test files, Debug/Release compilation and unit tests, AndroidTest Kotlin compilation,
lintDebug/lintRelease. First CI43060865604 failed lint on a restored literal BOM;
it was replaced by its escaped form, without suppressing lint or changing baselines.

No new APK published or installed on a device. Remote HTTP503/DNS/blocked sites are
not claimed repaired. Exact production diff: source-resilience.patch (tests/generated JS excluded).
