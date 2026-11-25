package io.github.onprs.universitytoolbox.core.common.time

/**
 * 可替换的时间源契约，便于统一计算秒级时间戳并在单元测试中模拟时间推进与回拨。
 */
fun interface TimeProvider {
    fun currentTimeMillis(): Long

    fun currentEpochSeconds(): Long = currentTimeMillis() / MILLIS_PER_SECOND

    companion object {
        private const val MILLIS_PER_SECOND = 1000L

        /** 基于系统挂钟的默认时间源。 */
        val System: TimeProvider = TimeProvider { java.lang.System.currentTimeMillis() }
    }
}
