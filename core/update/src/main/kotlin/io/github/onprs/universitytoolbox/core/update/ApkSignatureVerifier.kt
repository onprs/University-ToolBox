package io.github.onprs.universitytoolbox.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import java.io.File

/**
 * APK 签名证书公钥指纹校验器，阻止将第三方或篡改重签名的更新包安装到当前宿主应用。
 */
object ApkSignatureVerifier {
    /**
     * 校验证待安装 APK 文件的包名与签名证书 SHA-256 指纹是否与当前宿主一致。
     */
    fun verifyArchiveMatchesHostSignature(context: Context, apkFile: File): AppResult<Unit> {
        if (!apkFile.exists() || !apkFile.isFile) {
            return AppResult.Failure(DomainError.NotFound("Update package file not found"))
        }

        val packageManager = context.packageManager
        val hostInfo = loadInstalledPackageInfo(packageManager, context.packageName)
            ?: return AppResult.Failure(DomainError.Security("Unable to inspect host application signature"))

        val archiveInfo = loadArchivePackageInfo(packageManager, apkFile.absolutePath)
            ?: return AppResult.Failure(DomainError.Security("Unable to inspect downloaded APK package"))

        if (archiveInfo.packageName != context.packageName) {
            return AppResult.Failure(
                DomainError.Security("Update package identifier does not match current application"),
            )
        }

        val hostFingerprints = extractSigningCertFingerprints(hostInfo)
        val archiveFingerprints = extractSigningCertFingerprints(archiveInfo)

        return if (signaturesMatch(hostFingerprints, archiveFingerprints)) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(
                DomainError.Security("Update package signing certificate fingerprint mismatch"),
            )
        }
    }

    /** 判断宿主与候选安装包的证书指纹集合是否存在一致签名且均非空。 */
    fun signaturesMatch(hostFingerprints: Set<String>, candidateFingerprints: Set<String>): Boolean =
        hostFingerprints.isNotEmpty() &&
            candidateFingerprints.isNotEmpty() &&
            hostFingerprints.intersect(candidateFingerprints).isNotEmpty()

    /** 从 [PackageInfo] 提取全部签名证书的 SHA-256 指纹集合。 */
    fun extractSigningCertFingerprints(packageInfo: PackageInfo): Set<String> {
        val rawSignatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            if (signingInfo == null) {
                null
            } else if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        if (rawSignatures.isNullOrEmpty()) {
            return emptySet()
        }

        return rawSignatures
            .map { signature -> Sha256Verifier.computeSha256Hex(signature.toByteArray()) }
            .toSet()
    }

    private fun loadInstalledPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()

    private fun loadArchivePackageInfo(packageManager: PackageManager, archivePath: String): PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageArchiveInfo(
                    archivePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()
}
