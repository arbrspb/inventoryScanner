package com.example.inventoryscanner.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
// import androidx.test.uiautomator.By // раскомментируй, когда добавишь поиск кнопок

/**
 * APPLICATION_ID должен совпадать с defaultConfig.applicationId в :app
 */
private const val APPLICATION_ID = "com.example.inventoryscanner"

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfile {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun startup_scroll_openVerification() = baselineRule.collectPackageBaselineProfile(
        packageName = APPLICATION_ID
    ) {
        // 1. Холодный старт
        startActivityAndWait()
        device.waitForIdle()

        // 2. Лёгкий скролл (если есть список)
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            20
        )
        device.waitForIdle()

        // 3. Открытие экрана "Сверка" (добавишь позже)
        // val btn = device.findObject(By.res(APPLICATION_ID, "btn_verify"))
        //     ?: device.findObject(By.text("Сверка"))
        // btn?.click()
        // device.waitForIdle()
    }
}