import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun configValue(name: String): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orElse("")
        .get()

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
