plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    // Явно укажем версию плагина, чтобы не зависеть от catalogs
    id("androidx.baselineprofile") version "1.3.3"
}

android {
    namespace = "com.arbrspb.inventoryscanner.baselineprofile"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ВАЖНО: ссылаемся на целевой модуль приложения
    targetProjectPath = ":app"

    testOptions {
        animationsDisabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.test:runner:1.5.2")
    implementation("androidx.test:rules:1.5.0")
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.uiautomator:uiautomator:2.2.0")

    // Для сбора baseline профиля через macrobenchmark
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}