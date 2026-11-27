import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

/**
 * Typed view over the Groovy `ext` maps written by scripts/internal.gradle or scripts/official.gradle.
 * Every lookup falls back to a safe default so a partially filled channel config never breaks the build.
 */
class ChannelConfig(private val values: Map<String, Any?>) {
    fun text(key: String, fallback: String = ""): String = values[key]?.toString() ?: fallback

    fun number(key: String, fallback: Int): Int = when (val raw = values[key]) {
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull() ?: fallback
        else -> fallback
    }
}

fun channelConfig(section: String): ChannelConfig {
    val raw = rootProject.findProperty(section) as? Map<*, *> ?: emptyMap<Any?, Any?>()
    return ChannelConfig(raw.entries.associate { (key, value) -> key.toString() to value })
}

fun flag(name: String, fallback: Boolean): Boolean =
    providers.gradleProperty(name).orNull?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: fallback

/** Secrets resolve from Gradle properties first, then from the CI environment. */
fun secret(name: String): String =
    (rootProject.findProperty(name)?.toString() ?: System.getenv(name)).orEmpty().trim()

fun String.asBuildConfigLiteral(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun String.asFileSegment(fallback: String): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.', '-').ifEmpty { fallback }

/** Reads the package declared by a flavour's google-services.json so applicationId always matches Firebase. */
fun firebasePackage(flavor: String): String? {
    val json = file("src/$flavor/google-services.json").takeIf { it.isFile } ?: return null
    val root = groovy.json.JsonSlurper().parse(json) as? Map<*, *> ?: return null
    val client = (root["client"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
    val info = (client["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>
    return info?.get("package_name")?.toString()
}

/**
 * Streaming catalog credentials ship inside the APK as an availability fallback until Remote Config
 * delivers fresh ones. CI can override each list through environment variables of the same name.
 */
object SourceDefaults {
    const val JAMENDO_CLIENT_IDS = "617fcfac,20b76c90"
    const val AUDIUS_API_KEYS =
        "c53b3d1db49ff769804066f25b888bd357e834c4,707211e30a70f8c6f31d396d3cdff01069399fd7"
    const val AUDIUS_BEARER_TOKENS =
        "EjrggU1WfiJnQS1Ivn9PJEOkFnZvcmOmOo-r2kwtlOI=,x0EOhgSCurVAb2rRSVHDb6H2FdhHcNU9NNVO3JqijbA="
}

fun sourceCredential(envName: String, fallback: String): String =
    providers.environmentVariable(envName).orNull?.trim()?.takeIf(String::isNotEmpty) ?: fallback

val app = channelConfig("app")
val analytics = channelConfig("analytics")
val legal = channelConfig("legal")

val appVersionName = app.text("versionName", "1.0.0")
val appVersionCode = app.number("versionCode", 1)
val fallbackPackage = app.text("applicationId", "com.streamplay.music")

/**
 * Alias and passwords the AAB workflow commits next to the keystore it generates. Explicit Gradle
 * properties / environment variables still win, so a local or one-off override keeps working.
 */
val releaseSigningDir = file("src/google")
val releaseSigningProps = Properties().apply {
    releaseSigningDir.resolve("google-release-signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(envName: String, propKey: String, fallback: String): String =
    secret(envName).ifEmpty { releaseSigningProps.getProperty(propKey)?.trim().orEmpty() }.ifEmpty { fallback }

val releaseKeystore = secret("ANDROID_SIGNING_STORE_FILE").let { path ->
    val committedStore = releaseSigningProps.getProperty("storeFile")?.trim().orEmpty()
    when {
        // project.file() resolves relative paths from app/, matching the workflow's KEYSTORE_GRADLE_PATH.
        path.isNotEmpty() -> file(path)
        committedStore.isNotEmpty() -> releaseSigningDir.resolve(committedStore)
        else -> releaseSigningDir.resolve("google-release.keystore")
    }
}
val releaseStorePassword = signingValue("ANDROID_SIGNING_STORE_PASSWORD", "storePassword", "google123456")
val releaseKeyAlias = signingValue("ANDROID_SIGNING_KEY_ALIAS", "keyAlias", "google")
val releaseKeyPassword = signingValue("ANDROID_SIGNING_KEY_PASSWORD", "keyPassword", "google123456")
val releaseSigningReady = releaseKeystore.isFile && releaseKeystore.length() > 0L

// Only artifact-producing GoogleRelease tasks demand a real keystore; metadata helpers do not.
val buildsSignedRelease = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':').lowercase() }
    .any { task -> "googlerelease" in task && listOf("assemble", "bundle", "package", "publish").any(task::startsWith) }

val minifyRelease = flag("android.release.minifyEnabled", true)
val shrinkReleaseResources = flag("android.release.shrinkResourcesEnabled", false)
val optimizeRelease = flag("android.release.optimizeEnabled", minifyRelease)

val bundleFileName: String = run {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(ZonedDateTime.now(ZoneOffset.UTC))
    val project = rootProject.name.asFileSegment("android_app")
    "${project}_google_release_v${appVersionName.asFileSegment("unknown")}_${stamp}_UTC.aab"
}

android {
    namespace = "com.streamplay.music"
    compileSdk = 36

    defaultConfig {
        minSdk = app.number("minSdk", 26)
        targetSdk = app.number("targetSdk", 35)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "USER_CHANNEL_DEFAULT", analytics.text("defaultUserChannel", "default").asBuildConfigLiteral())
        buildConfigField(
            "String",
            "SOURCE_JAMENDO_IDS",
            sourceCredential("MUSIC_JAMENDO_CLIENT_IDS", SourceDefaults.JAMENDO_CLIENT_IDS).asBuildConfigLiteral(),
        )
        buildConfigField(
            "String",
            "SOURCE_AUDIUS_KEYS",
            sourceCredential("MUSIC_AUDIUS_API_KEYS", SourceDefaults.AUDIUS_API_KEYS).asBuildConfigLiteral(),
        )
        buildConfigField(
            "String",
            "SOURCE_AUDIUS_TOKENS",
            sourceCredential("MUSIC_AUDIUS_BEARER_TOKENS", SourceDefaults.AUDIUS_BEARER_TOKENS).asBuildConfigLiteral(),
        )
        buildConfigField("String", "LEGAL_PRIVACY_URL", legal.text("privacyPolicyUrl").asBuildConfigLiteral())
        buildConfigField("String", "LEGAL_TERMS_URL", legal.text("termsOfServiceUrl").asBuildConfigLiteral())
    }

    flavorDimensions += "channel"
    productFlavors {
        register("google") {
            dimension = "channel"
            applicationId = firebasePackage("google") ?: fallbackPackage
            versionCode = appVersionCode
            versionName = appVersionName
        }
        register("local") {
            dimension = "channel"
            applicationId = firebasePackage("local") ?: fallbackPackage
            versionCode = appVersionCode
            versionName = "$appVersionName-local"
            isDefault = true
        }
    }

    signingConfigs {
        register("googleRelease") {
            if (releaseSigningReady) {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            if (buildsSignedRelease && !releaseSigningReady) {
                throw GradleException(
                    "Google release signing is not configured. Provide app/src/google/google-release.keystore or the " +
                        "ANDROID_SIGNING_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD variables.",
                )
            }
            if (releaseSigningReady || buildsSignedRelease) {
                signingConfig = signingConfigs.getByName("googleRelease")
            }
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease && shrinkReleaseResources
            proguardFiles(
                getDefaultProguardFile(
                    if (minifyRelease && optimizeRelease) "proguard-android-optimize.txt" else "proguard-android.txt",
                ),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.version",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Exported schemas are versioned so future AutoMigrations are verified at compile time.
    schemaDirectory("$projectDir/schemas")
}

configurations.configureEach {
    exclude(group = "com.google.firebase", module = "protolite-well-known-types")
}

tasks.register("printGoogleReleaseVersionName") {
    group = "help"
    description = "Prints the versionName used for google release builds."
    doLast { println(appVersionName) }
}

tasks.register("printGoogleReleaseAabName") {
    group = "help"
    description = "Prints the expected output file name for google release AAB builds."
    doLast { println(bundleFileName) }
}

dependencies {
    implementation(project(":music-source"))
    implementation(libs.remax.core)

    // Platform
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Jetpack Compose + Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Media, storage, images
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
