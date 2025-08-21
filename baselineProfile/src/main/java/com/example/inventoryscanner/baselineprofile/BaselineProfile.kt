package com.example.inventoryscanner.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        startActivityAndWait()
        device.waitForIdle()

        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            20
        )
        device.waitForIdle()
    }
}