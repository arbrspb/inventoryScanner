package com.example.inventoryscanner.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generate() = baselineRule.collect(
        packageName = "com.example.inventoryscanner" // это должен быть applicationId из app/defaultConfig
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        // добавь здесь действия по прогреву экранов/прокруток
    }
}