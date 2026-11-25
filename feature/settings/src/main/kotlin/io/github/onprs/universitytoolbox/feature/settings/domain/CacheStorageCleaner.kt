package io.github.onprs.universitytoolbox.feature.settings.domain

import java.io.File
import java.util.Locale

/**
 * 缓存目录大小统计与安全清理结果。
 */
data class CacheCleanupResult(val reclaimedBytes: Long, val remainingBytes: Long, val deletedFilesCount: Int)

/**
 * 缓存统计、清理与人类可读字节大小格式化器。
 */
object CacheStorageCleaner {
    private const val BYTES_PER_KIB = 1024.0
    private const val BYTES_PER_MIB = BYTES_PER_KIB * 1024.0

    /**
     * 递归统计指定目录集下全部普通文件的字节大小。
     */
    fun calculateTotalBytes(directories: Iterable<File>): Long =
        directories.sumOf { directory -> calculateDirectorySize(directory) }

    /**
     * 递归清理指定目录集的内容，保留根目录本身与名称在 [excludedNames] 中的条目。
     */
    fun clearDirectories(directories: Iterable<File>, excludedNames: Set<String> = emptySet()): CacheCleanupResult {
        var reclaimed = 0L
        var deletedCount = 0

        directories.forEach { root ->
            if (!root.exists() || !root.isDirectory) return@forEach
            val children = root.listFiles() ?: return@forEach
            children.forEach { child ->
                if (child.name in excludedNames) {
                    return@forEach
                }
                val result = deleteRecursivelyWithStats(child)
                reclaimed += result.first
                deletedCount += result.second
            }
        }

        val remaining = calculateTotalBytes(directories)
        return CacheCleanupResult(
            reclaimedBytes = reclaimed,
            remainingBytes = remaining,
            deletedFilesCount = deletedCount,
        )
    }

    /**
     * 格式化字节数为人类可读文本。
     */
    fun formatByteSize(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        return when {
            safeBytes < BYTES_PER_KIB -> "$safeBytes B"
            safeBytes < BYTES_PER_MIB -> String.format(Locale.US, "%.1f KB", safeBytes / BYTES_PER_KIB)
            else -> String.format(Locale.US, "%.2f MB", safeBytes / BYTES_PER_MIB)
        }
    }

    private fun calculateDirectorySize(file: File): Long {
        if (!file.exists()) {
            return 0L
        }
        if (file.isFile) {
            return file.length()
        }
        val children = file.listFiles() ?: return 0L
        return children.sumOf { calculateDirectorySize(it) }
    }

    private fun deleteRecursivelyWithStats(target: File): Pair<Long, Int> {
        if (!target.exists()) {
            return 0L to 0
        }
        if (target.isFile) {
            val length = target.length()
            return if (target.delete()) length to 1 else 0L to 0
        }
        var bytes = 0L
        var count = 0
        val children = target.listFiles() ?: emptyArray()
        for (child in children) {
            val (childBytes, childCount) = deleteRecursivelyWithStats(child)
            bytes += childBytes
            count += childCount
        }
        target.delete()
        return bytes to count
    }
}
