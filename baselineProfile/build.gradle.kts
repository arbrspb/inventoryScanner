plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.example.inventoryscanner.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Целевой модуль для генерации профиля
    targetProjectPath = ":app"

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Макробенчмарк для сбора baseline-профиля
    implementation(libs.androidx.benchmark.macro.junit4)
    // Для взаимодействия с экраном
    implementation(libs.androidx.uiautomator)
    // Установщик профиля (в APK — присутствует и в app)
    implementation(libs.androidx.profileinstaller)
}