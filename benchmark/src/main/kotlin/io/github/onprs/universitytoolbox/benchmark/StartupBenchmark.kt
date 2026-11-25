package io.github.onprs.universitytoolbox.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 宿主应用冷启动耗时基准测试（Macrobenchmark），验证首帧渲染（TTID）性能预算。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupColdCompilationPartial() = startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    private companion object {
        const val TARGET_PACKAGE_NAME = "io.github.onprs.universitytoolbox"
        const val DEFAULT_ITERATIONS = 5
    }
}
