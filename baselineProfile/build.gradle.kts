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

    // Для какого модуля генерим профиль
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
    // Обязательная зависимость: даёт BaselineProfileRule/startActivityAndWait/device
    implementation(libs.androidx.profileinstaller) // вместо хардкода "1.3.1"
    // Для UiDevice и взаимодействия с экраном
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    // при необходимости:
    // implementation("androidx.test:runner:1.5.2")
}