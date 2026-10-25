// Disposable, code-free old-package fixture for CI. Never shipped or installed on a user phone.
plugins { id("com.android.application") }
android {
 namespace="com.audiobookdownloader.legacyfixture"
 compileSdk=36
 defaultConfig { applicationId="com.audiobookdownloader";minSdk=23;targetSdk=36;versionCode=1;versionName="ci-fixture" }
 buildTypes { getByName("release") { isDebuggable=false;signingConfig=signingConfigs.getByName("debug") } }
}
