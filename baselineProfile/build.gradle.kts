plugins {
    alias(libs.plugins.android.test)          // com.android.test
    alias(libs.plugins.kotlin.android)        // org.jetbrains.kotlin.android
    alias(libs.plugins.baselineprofile)       // androidx.baselineprofile
}

android {
    namespace = "com.example.inventoryscanner.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Целевой модуль приложения
    targetProjectPath = ":app"

    // Добавляем buildType benchmark, т.к. ты его уже выбирал
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        animationsDisabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation("androidx.test:runner:1.5.2")
    implementation("androidx.test:rules:1.5.0")
    implementation("androidx.test.ext:junit:1.1.5")
    implementation(libs.androidx.profileinstaller)
}