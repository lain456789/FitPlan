import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(key: String, defaultValue: String): String {
    val raw = (localProps.getProperty(key) ?: System.getenv(key) ?: "").trim()
    return if (raw.isEmpty()) defaultValue else raw
}

fun buildConfigString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.example.fitplan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.fitplan"
        minSdk = 24
        targetSdk = 37
        versionCode = 19
        versionName = "0.6.9"

        buildConfigField("String", "GLM_API_KEY", buildConfigString(localProp("GLM_API_KEY", "")))
        buildConfigField(
            "String",
            "GLM_MODEL",
            buildConfigString(localProp("GLM_MODEL", "glm-4-flash"))
        )
        buildConfigField(
            "String",
            "GLM_ENDPOINT",
            buildConfigString(
                localProp(
                    "GLM_ENDPOINT",
                    "https://open.bigmodel.cn/api/paas/v4/chat/completions"
                )
            )
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.viewpager)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
