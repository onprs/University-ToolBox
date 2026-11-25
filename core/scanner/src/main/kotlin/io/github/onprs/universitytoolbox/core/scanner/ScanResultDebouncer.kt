package io.github.onprs.universitytoolbox.core.scanner

import io.github.onprs.universitytoolbox.core.common.time.TimeProvider

/**
 * 连续扫码去重与防抖控制器：避免相机连续帧在短时间窗口内重复回调相同的条码结果。
 */
class ScanResultDebouncer(
    private val debounceWindowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS,
    private val timeProvider: TimeProvider = TimeProvider.System,
) {
    private val lock = Any()
    private var lastRawValue: String? = null
    private var lastFormat: BarcodeFormat? = null
    private var lastEmittedAtMs: Long = Long.MIN_VALUE

    /**
     * 过滤候选识别结果。若当前为空白内容或与时间窗口内上一次结果完全一致，则返回 null。
     */
    fun filterEmit(candidate: BarcodeScanResult): BarcodeScanResult? {
        if (candidate.rawValue.isBlank()) {
            return null
        }
        val nowMs = timeProvider.currentTimeMillis()
        synchronized(lock) {
            val isSamePayload = candidate.rawValue == lastRawValue && candidate.format == lastFormat
            val elapsedMs = if (lastEmittedAtMs == Long.MIN_VALUE) {
                Long.MAX_VALUE
            } else {
                nowMs - lastEmittedAtMs
            }
            if (isSamePayload && elapsedMs in 0 until debounceWindowMs) {
                return null
            }
            lastRawValue = candidate.rawValue
            lastFormat = candidate.format
            lastEmittedAtMs = nowMs
            return candidate
        }
    }

    /** 重置防抖记忆窗口。 */
    fun reset() {
        synchronized(lock) {
            lastRawValue = null
            lastFormat = null
            lastEmittedAtMs = Long.MIN_VALUE
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_WINDOW_MS = 1500L
    }
}
