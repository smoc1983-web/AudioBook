// Standalone, same-signed CI instrumentation for actual old/new APKs. Never shipped.
plugins { id("com.android.test") }
android {
    namespace="com.audiobookdownloader.preview.upgradeprobe"
    compileSdk=36
    targetProjectPath=":app"
    defaultConfig {
        minSdk=36;targetSdk=36
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig=false }
    signingConfigs {
        create("preview") {
            System.getenv("PREVIEW_KEYSTORE")?.let { storeFile=file(it) }
            storePassword="android";keyAlias="audiobook-preview";keyPassword="android"
        }
    }
    buildTypes {
        getByName("debug") {
            if(System.getenv("PREVIEW_KEYSTORE")!=null)signingConfig=signingConfigs.getByName("preview")
        }
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled=false
    }
    sourceSets.getByName("main").java.srcDir(layout.buildDirectory.dir("generated/probeSources"))
}
val syncProbeSources by tasks.registering(Copy::class) {
    from("../app/src/androidTest/java") {
        include("**/UpgradeCandidateTest.java","**/PermissionSurfaceTest.java")
    }
    into(layout.buildDirectory.dir("generated/probeSources"))
}
tasks.named("preBuild").configure { dependsOn(syncProbeSources) }
dependencies {
    implementation("androidx.test:core:1.6.1")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
