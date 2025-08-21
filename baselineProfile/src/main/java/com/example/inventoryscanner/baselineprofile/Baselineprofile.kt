package com.example.inventoryscanner.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ВАЖНО: замените значение APPLICATION_ID на applicationId из модуля :app (defaultConfig.applicationId).
 * Иначе профиль не соберётся (не найдёт пакет).
 */
private const val APPLICATION_ID = "com.example.inventoryscanner" // TODO: заменить если отличается

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfile {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    /**
     * Собираем baseline-профиль для:
     * - холодного старта
     * - лёгкого скролла списка
     * - открытия экрана "Сверка" (добавите селекторы)
     */
    @Test
    fun startup_scroll_openVerification() = baselineRule.collectPackageBaselineProfile(
        packageName = APPLICATION_ID
    ) {
        // 1. Холодный старт главного Activity
        startActivityAndWait()
        device.waitForIdle()

        // 2. Прогрев списка (если список на стартовом экране)
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            20
        )
        device.waitForIdle()

        // 3. Открытие экрана "Сверка"
        // Раскомментируйте и адаптируйте под ваши ID или текст.
        // Примеры (нужен импорт androidx.test.uiautomator.By):
        //
        // val verifyButton = device.findObject(
        //     By.res(APPLICATION_ID, "btn_verify")
        // ) ?: device.findObject(By.text("Сверка"))
        // verifyButton?.click()
        // device.waitForIdle()

        // 4. (Опционально) Дополнительная навигация / назад / ещё один скролл
    }
}