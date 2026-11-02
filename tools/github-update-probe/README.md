# Public GitHub update probe

Compile `GithubUpdateProbe.java` together with the generated **release** `BuildConfig.java`,
`PreviewUpdateRules.java`, and `PreviewUpdateHttp.java`. The classpath needs the same
`org.json` and `jsoup` JAR versions as the app. Run
`com.audiobookdownloader.GithubUpdateProbe OUTPUT_DIR [EXPECTED_TAG]`.

This uses the exact production discovery/parser, URL policy, manual redirects, bounded reads,
size and SHA-256 download checks. It never reads GitHub credentials. Run `apksigner verify`
on the downloaded APK separately; Android's installed-package comparison and real package
replacement still require an Android device. The probe alone is not an installation test.
