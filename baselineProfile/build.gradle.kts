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

    targetProjectPath = ":app"

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}