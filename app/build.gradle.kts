plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace="com.audiobookdownloader"
    compileSdk=36
    defaultConfig {
        applicationId="com.audiobookdownloader.preview"
        minSdk=23;targetSdk=36
        versionCode=100000+(System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName="4.2-preview."+(System.getenv("GITHUB_RUN_NUMBER") ?: "local")
        buildConfigField("String","LIBRARY_DIRECTORY","\"AudioBooks-Preview\"")
    }
    buildFeatures { compose=true;buildConfig=true }
    signingConfigs {
        create("preview") {
            System.getenv("PREVIEW_KEYSTORE")?.let { storeFile=file(it) }
            storePassword="android";keyAlias="audiobook-preview";keyPassword="android"
        }
    }
    buildTypes { getByName("debug") { if(System.getenv("PREVIEW_KEYSTORE")!=null) signingConfig=signingConfigs.getByName("preview") } }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
    sourceSets {
        getByName("main") { java.srcDirs("../current/app/src/main/java",layout.buildDirectory.dir("generated/currentSources"));res.srcDirs("../current/app/src/main/res") }
    }
}
val syncCurrentSources by tasks.registering(Copy::class) {
    from("../current") { include("MainActivity.kt","PlaybackService.kt") }
    into(layout.buildDirectory.dir("generated/currentSources"))
}
tasks.named("preBuild").configure { dependsOn(syncCurrentSources) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach { testLogging { showStandardStreams=true; events("passed","failed","skipped") } }
