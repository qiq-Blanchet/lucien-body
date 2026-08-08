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
    gradle.startParameter.projectProperties[name]
        ?: providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)
        ?: ""

fun javaStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (character.code < 0x20) {
                append('\\')
                append(character.code.toString(8).padStart(3, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

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
        targetSdk = 34
        versionCode = 8
        versionName = "0.1.7"
        buildConfigField("String", "SUPABASE_URL", javaStringLiteral(configValue("SUPABASE_URL")))
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            javaStringLiteral(configValue("SUPABASE_PUBLISHABLE_KEY")),
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
