import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun configValue(name: String): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orElse(localProperties.getProperty(name).orEmpty())
        .get()

val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val keyAliasValue = providers.environmentVariable("ANDROID_KEY_ALIAS")
val keyPasswordValue = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val releaseSigningReady = listOf(
    keystorePath,
    keystorePassword,
    keyAliasValue,
    keyPasswordValue,
).all { it.orNull?.isNotBlank() == true }

android {
    namespace = "com.luc.body"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luc.body"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${configValue("SUPABASE_URL")}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${configValue("SUPABASE_PUBLISHABLE_KEY")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = file(keystorePath.get())
                storePassword = keystorePassword.get()
                keyAlias = keyAliasValue.get()
                keyPassword = keyPasswordValue.get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.json)
}
