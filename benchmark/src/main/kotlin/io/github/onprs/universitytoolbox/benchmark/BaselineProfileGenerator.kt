package io.github.onprs.universitytoolbox.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 录制器：覆盖冷启动、授权状态渲染与主界面初始交互热点路径。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE_NAME)), UI_WAIT_TIMEOUT_MS)
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE_NAME = "io.github.onprs.universitytoolbox"
        const val UI_WAIT_TIMEOUT_MS = 5_000L
    }
}
