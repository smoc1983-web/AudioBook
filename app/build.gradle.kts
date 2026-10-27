plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
val audiobookChannel=providers.gradleProperty("audiobookChannel").orElse("preview").get()
require(audiobookChannel in listOf("preview","stable")) { "Unknown Audiobook channel" }
val stableChannel=audiobookChannel=="stable"
android {
    namespace="com.audiobookdownloader"
    compileSdk=36
    defaultConfig {
        applicationId=if(stableChannel) "com.audiobookdownloader" else "com.audiobookdownloader.preview"
        minSdk=23;targetSdk=36
        versionCode=100000+(System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName="4.2."+(System.getenv("GITHUB_RUN_NUMBER") ?: "local")
        buildConfigField("String","PRODUCT_NAME","\"Audiobook\"")
        buildConfigField("String","UPDATE_ORIGIN","\"https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site\"")
        buildConfigField("String","UPDATE_PREFIX",if(stableChannel) "\"stable-v\"" else "\"preview-v\"")
        buildConfigField("String","UPDATE_APK",if(stableChannel) "\"Audiobook.apk\"" else "\"Audiobook-Preview.apk\"")
        buildConfigField("String","UPDATE_METADATA",if(stableChannel) "\"stable-update.json\"" else "\"preview-update.json\"")
        resValue("string","app_name","Audiobook")
        buildConfigField("String","LIBRARY_DIRECTORY",if(stableChannel) "\"AudioBooks\"" else "\"AudioBooks-Preview\"")
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose=true;buildConfig=true }
    signingConfigs {
        create("preview") {
            System.getenv("PREVIEW_KEYSTORE")?.let { storeFile=file(it) }
            storePassword="android";keyAlias="audiobook-preview";keyPassword="android"
        }
    }
    buildTypes {
        getByName("debug") { if(stableChannel) signingConfig=null else if(System.getenv("PREVIEW_KEYSTORE")!=null) signingConfig=signingConfigs.getByName("preview") }
        getByName("release") {
            isDebuggable=false
            if(!stableChannel && System.getenv("PREVIEW_KEYSTORE")!=null) signingConfig=signingConfigs.getByName("preview")
        }
    }
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
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach { testLogging { showStandardStreams=true; events("passed","failed","skipped") } }

// Read-only audit inventory of the actual resolved runtime graph; never included in APK.
tasks.register("auditRuntimeDependencies") {
    doLast {
        val modules = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id }
            .map { mapOf("name" to (it.group + ":" + it.name), "version" to it.version) }
            .distinct().sortedBy { it["name"] }
        val report = layout.buildDirectory.file("reports/security/runtime-dependencies.json").get().asFile
        report.parentFile.mkdirs()
        report.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(modules)) + "\n")
    }
}
