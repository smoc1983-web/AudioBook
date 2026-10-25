// Disposable, code-free old-package fixture for CI. Never shipped or installed on a user phone.
plugins { id("com.android.application") }
android {
 namespace="com.audiobookdownloader.legacyfixture"
 compileSdk=36
 defaultConfig { applicationId="com.audiobookdownloader";minSdk=23;targetSdk=36;versionCode=1;versionName="ci-fixture" }
 signingConfigs { create("fixture") { System.getenv("LEGACY_FIXTURE_KEYSTORE")?.let { storeFile=file(it) };storePassword="android";keyPassword="android";keyAlias="fixture" } }
 buildTypes { getByName("debug") { signingConfig=signingConfigs.getByName("fixture") };getByName("release") { isDebuggable=false;signingConfig=signingConfigs.getByName("fixture") } }
}
