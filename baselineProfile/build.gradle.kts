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
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Строим тесты против нужного buildType приложения (обычно release или benchmark)
    targetProjectPath = ":app"

    // Дополнительный buildType в ТЕСТОВОМ модуле: достаточно isDebuggable
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            // matchingFallbacks не всегда обязателен; если в целевом app есть release —
            // Gradle сам подберёт. Оставим на всякий случай:
            matchingFallbacks += listOf("release")
        }
    }

    testOptions {
        animationsDisabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)

    // Явные зависимости на runner/rules/junit-ext (можно оставить; они не дублируют критично)
    implementation("androidx.test:runner:1.5.2")
    implementation("androidx.test:rules:1.5.0")
    implementation("androidx.test.ext:junit:1.1.5")

    // profileinstaller в тестовом модуле не обязателен (нужен в app). Можно убрать,
    // но если оставишь — не сломает.
    implementation(libs.androidx.profileinstaller)
}