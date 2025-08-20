package com.example.inventoryscanner.baselineprofile

import androidx.benchmark.macro.BaselineProfileRule
import androidx.benchmark.macro.StartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.example.inventoryscanner",
        includeInStartupProfile = true,
        startupMode = StartupMode.COLD,
        maxIterations = 3
    ) {
        // Холодный старт главной Activity
        startActivityAndWait()

        // Небольшое взаимодействие: прокрутка списка
        device.waitForIdle()
        val w = device.displayWidth / 2
        val y1 = (device.displayHeight * 0.8).toInt()
        val y2 = (device.displayHeight * 0.2).toInt()
        device.swipe(w, y1, w, y2, 30)
        device.waitForIdle()
    }
}