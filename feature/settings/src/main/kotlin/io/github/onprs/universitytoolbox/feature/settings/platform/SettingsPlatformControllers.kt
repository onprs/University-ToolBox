package io.github.onprs.universitytoolbox.feature.settings.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.core.datastore.AppLanguage
import io.github.onprs.universitytoolbox.feature.settings.domain.CacheCleanupResult
import io.github.onprs.universitytoolbox.feature.settings.domain.CacheStorageCleaner
import io.github.onprs.universitytoolbox.feature.settings.domain.PermissionKind
import io.github.onprs.universitytoolbox.feature.settings.domain.PermissionStatusItem
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 宿主版本元数据模型。
 */
data class AppVersionInfo(val versionName: String, val versionCode: Long)

/**
 * 读取当前宿主版本号的平台抽象，便于单元测试替换。
 */
interface AppVersionInfoProvider {
    fun getVersionInfo(): AppVersionInfo
}

/**
 * 核验运行时权限状态的平台抽象。
 */
interface PermissionStatusProvider {
    fun checkPermissions(): List<PermissionStatusItem>
}

/**
 * 控制应用内语言 Locale 的平台抽象。
 */
interface AppLocaleController {
    fun applyLanguage(language: AppLanguage)
}

/**
 * 应用缓存读写清理的平台抽象。
 */
interface AppCacheController {
    fun measureCacheBytes(): Long

    fun clearCache(): CacheCleanupResult
}

/**
 * 聚合平台版本、权限、语言与缓存控制器，避免 ViewModel 构造函数过长。
 */
@Singleton
class SettingsPlatformFacade @Inject constructor(
    val versionInfoProvider: AppVersionInfoProvider,
    val permissionStatusProvider: PermissionStatusProvider,
    val appLocaleController: AppLocaleController,
    val appCacheController: AppCacheController,
)

@Singleton
class AndroidAppVersionInfoProvider @Inject constructor(@param:ApplicationContext private val context: Context) :
    AppVersionInfoProvider {
    override fun getVersionInfo(): AppVersionInfo = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        AppVersionInfo(
            versionName = packageInfo.versionName ?: DEFAULT_VERSION_NAME,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        )
    }.getOrDefault(AppVersionInfo(versionName = DEFAULT_VERSION_NAME, versionCode = DEFAULT_VERSION_CODE))

    private companion object {
        const val DEFAULT_VERSION_NAME = "1.0.0"
        const val DEFAULT_VERSION_CODE = 1_000_000L
    }
}

@Singleton
class AndroidPermissionStatusProvider @Inject constructor(@param:ApplicationContext private val context: Context) :
    PermissionStatusProvider {
    override fun checkPermissions(): List<PermissionStatusItem> {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        val installGranted = runCatching {
            context.packageManager.canRequestPackageInstalls()
        }.getOrDefault(false)

        return listOf(
            PermissionStatusItem(
                kind = PermissionKind.CAMERA,
                isGranted = cameraGranted,
                requiresSystemSettings = !cameraGranted,
            ),
            PermissionStatusItem(
                kind = PermissionKind.LOCATION,
                isGranted = locationGranted,
                requiresSystemSettings = !locationGranted,
            ),
            PermissionStatusItem(
                kind = PermissionKind.NOTIFICATIONS,
                isGranted = notificationsGranted,
                requiresSystemSettings = true,
            ),
            PermissionStatusItem(
                kind = PermissionKind.INSTALL_UNKNOWN_APPS,
                isGranted = installGranted,
                requiresSystemSettings = true,
            ),
        )
    }
}

@Singleton
class AndroidAppLocaleController @Inject constructor() : AppLocaleController {
    override fun applyLanguage(language: AppLanguage) {
        val localeList = when (val tag = language.languageTag) {
            null -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}

@Singleton
class AndroidAppCacheController @Inject constructor(@param:ApplicationContext private val context: Context) :
    AppCacheController {
    private fun cacheDirs(): List<File> = listOfNotNull(
        context.cacheDir,
        context.externalCacheDir,
    )

    override fun measureCacheBytes(): Long = CacheStorageCleaner.calculateTotalBytes(cacheDirs())

    override fun clearCache(): CacheCleanupResult = CacheStorageCleaner.clearDirectories(cacheDirs())
}
