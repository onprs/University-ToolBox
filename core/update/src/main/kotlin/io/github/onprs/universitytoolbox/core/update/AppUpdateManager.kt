package io.github.onprs.universitytoolbox.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.core.common.dispatcher.ApplicationScope
import io.github.onprs.universitytoolbox.core.common.dispatcher.IoDispatcher
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.result.runCatchingApp
import io.github.onprs.universitytoolbox.core.network.RootHttpClient
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 应用自更新下载与安装状态模型。
 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long?) : UpdateDownloadState

    data class Verifying(val file: File) : UpdateDownloadState

    data class ReadyToInstall(val apkFile: File, val sha256: String) : UpdateDownloadState

    data object Installing : UpdateDownloadState

    data class InstallPendingUserAction(val statusMessage: String?) : UpdateDownloadState

    data object InstallSucceeded : UpdateDownloadState

    data class Failed(val error: DomainError) : UpdateDownloadState
}

/**
 * 应用内安全自更新管理器契约：负责安装包流式下载、SHA-256 校验、签名核验及会话安装。
 */
interface AppUpdateManager {
    val updateState: StateFlow<UpdateDownloadState>

    fun canRequestPackageInstalls(): Boolean

    suspend fun downloadAndVerifyUpdate(manifest: UpdateManifest): AppResult<File>

    suspend fun installVerifiedApk(apkFile: File): AppResult<Unit>

    suspend fun clearCachedUpdates()
}

/**
 * 基于 `OkHttp` + `PackageInstaller.Session` 的安全自更新实现。
 */
@Singleton
class AndroidAppUpdateManager internal constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val cacheRootDir: File,
    private val signatureVerifier: (File) -> AppResult<Unit>,
    private val allowCleartextForTesting: Boolean,
    applicationScope: CoroutineScope,
) : AppUpdateManager {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @RootHttpClient okHttpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : this(
        context = context,
        okHttpClient = okHttpClient,
        ioDispatcher = ioDispatcher,
        cacheRootDir = context.cacheDir,
        signatureVerifier = { file -> ApkSignatureVerifier.verifyArchiveMatchesHostSignature(context, file) },
        allowCleartextForTesting = false,
        applicationScope = applicationScope,
    )

    private val mutableUpdateState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    override val updateState: StateFlow<UpdateDownloadState> = mutableUpdateState.asStateFlow()

    init {
        applicationScope.launch {
            InstallResultBus.events.collect { event ->
                when (event) {
                    is InstallResultBus.InstallEvent.PendingUserAction -> {
                        mutableUpdateState.value = UpdateDownloadState.InstallPendingUserAction(event.statusMessage)
                    }
                    InstallResultBus.InstallEvent.Succeeded -> {
                        mutableUpdateState.value = UpdateDownloadState.InstallSucceeded
                    }
                    is InstallResultBus.InstallEvent.Failed -> {
                        mutableUpdateState.value = UpdateDownloadState.Failed(
                            DomainError.Unknown(event.message ?: "Package installation failed (${event.status})"),
                        )
                    }
                }
            }
        }
    }

    override fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    override suspend fun downloadAndVerifyUpdate(manifest: UpdateManifest): AppResult<File> {
        val urlValidation = validateManifestForDownload(manifest)
        if (urlValidation is AppResult.Failure) {
            mutableUpdateState.value = UpdateDownloadState.Failed(urlValidation.error)
            return urlValidation
        }
        val downloadUrl = (urlValidation as AppResult.Success).data

        val updatesDir = File(cacheRootDir, UPDATES_SUBDIR).apply { mkdirs() }
        val partFile = File(updatesDir, "update-${manifest.latestVersionCode}.apk.part")
        val targetFile = File(updatesDir, "update-${manifest.latestVersionCode}.apk")

        val downloadResult = withContext(ioDispatcher) {
            runCatchingApp(
                errorMapper = { throwable ->
                    DomainError.Network(
                        message = throwable.message ?: "Failed to download update package",
                        cause = throwable,
                    )
                },
            ) {
                streamDownloadWithDigest(downloadUrl, partFile)
            }
        }

        if (downloadResult is AppResult.Failure) {
            partFile.delete()
            mutableUpdateState.value = UpdateDownloadState.Failed(downloadResult.error)
            return downloadResult
        }

        val actualSha256 = (downloadResult as AppResult.Success).data
        return verifyAndFinalizeUpdate(partFile, targetFile, manifest.sha256, actualSha256)
    }

    private fun validateManifestForDownload(manifest: UpdateManifest): AppResult<String> {
        val parsedUrl = manifest.apkUrl.trim().toHttpUrlOrNull()
            ?: return AppResult.Failure(DomainError.Validation("Invalid APK download URL", field = "apkUrl"))
        if (!parsedUrl.isHttps && !allowCleartextForTesting) {
            return AppResult.Failure(DomainError.Security("APK download URL must use HTTPS"))
        }
        if (!Sha256Verifier.isValidSha256Hex(manifest.sha256)) {
            return AppResult.Failure(DomainError.Validation("Manifest SHA-256 digest is invalid", field = "sha256"))
        }
        return AppResult.Success(parsedUrl.toString())
    }

    private suspend fun verifyAndFinalizeUpdate(
        partFile: File,
        targetFile: File,
        expectedSha256: String,
        actualSha256: String,
    ): AppResult<File> {
        mutableUpdateState.value = UpdateDownloadState.Verifying(partFile)
        if (!Sha256Verifier.matchesSha256(expectedSha256, actualSha256)) {
            partFile.delete()
            targetFile.delete()
            val error = DomainError.Security("Downloaded APK SHA-256 digest mismatch")
            mutableUpdateState.value = UpdateDownloadState.Failed(error)
            return AppResult.Failure(error)
        }

        val moved = withContext(ioDispatcher) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            partFile.renameTo(targetFile)
        }
        if (!moved) {
            partFile.delete()
            val error = DomainError.Storage("Failed to finalize downloaded APK file")
            mutableUpdateState.value = UpdateDownloadState.Failed(error)
            return AppResult.Failure(error)
        }

        val signatureResult = withContext(ioDispatcher) {
            signatureVerifier(targetFile)
        }
        if (signatureResult is AppResult.Failure) {
            targetFile.delete()
            mutableUpdateState.value = UpdateDownloadState.Failed(signatureResult.error)
            return signatureResult
        }

        mutableUpdateState.value = UpdateDownloadState.ReadyToInstall(targetFile, actualSha256)
        return AppResult.Success(targetFile)
    }

    override suspend fun installVerifiedApk(apkFile: File): AppResult<Unit> {
        if (!apkFile.exists() || !apkFile.isFile) {
            val error = DomainError.NotFound("Verified APK file not found")
            mutableUpdateState.value = UpdateDownloadState.Failed(error)
            return AppResult.Failure(error)
        }
        if (!canRequestPackageInstalls()) {
            val error = DomainError.Unsupported("Install unknown apps permission is not granted")
            mutableUpdateState.value = UpdateDownloadState.Failed(error)
            return AppResult.Failure(error)
        }

        mutableUpdateState.value = UpdateDownloadState.Installing
        val installResult = withContext(ioDispatcher) {
            runCatchingApp(
                errorMapper = { throwable ->
                    DomainError.Storage(
                        message = throwable.message ?: "Failed to start package installation",
                        cause = throwable,
                    )
                },
            ) {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)
                try {
                    session.openWrite(SESSION_ENTRY_NAME, 0, apkFile.length()).use { out ->
                        apkFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                        session.fsync(out)
                    }
                    val callbackIntent = Intent(context, InstallResultReceiver::class.java)
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        callbackIntent,
                        flags,
                    )
                    session.commit(pendingIntent.intentSender)
                } finally {
                    session.close()
                }
            }
        }

        if (installResult is AppResult.Failure) {
            mutableUpdateState.value = UpdateDownloadState.Failed(installResult.error)
        }
        return installResult
    }

    override suspend fun clearCachedUpdates() {
        withContext(ioDispatcher) {
            val updatesDir = File(cacheRootDir, UPDATES_SUBDIR)
            if (updatesDir.exists()) {
                updatesDir.deleteRecursively()
            }
        }
        mutableUpdateState.value = UpdateDownloadState.Idle
    }

    private fun streamDownloadWithDigest(url: String, partFile: File): String {
        val request = Request.Builder().url(url).get().build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Unexpected HTTP ${response.code} when downloading APK")
            }
            val body = response.body
            val totalBytes = body.contentLength().takeIf { it > 0L }
            mutableUpdateState.value = UpdateDownloadState.Downloading(0L, totalBytes)
            copyBodyWithSha256(body.byteStream(), partFile, totalBytes)
        }
    }

    private fun copyBodyWithSha256(input: java.io.InputStream, partFile: File, totalBytes: Long?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            FileOutputStream(partFile).use { output ->
                transferAndDigest(stream, output, digest, totalBytes)
                output.flush()
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and BYTE_MASK)
        }
    }

    private fun transferAndDigest(
        stream: java.io.InputStream,
        output: FileOutputStream,
        digest: MessageDigest,
        totalBytes: Long?,
    ) {
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        var downloadedBytes = 0L
        var read = stream.read(buffer)
        while (read > 0) {
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            downloadedBytes += read
            mutableUpdateState.value = UpdateDownloadState.Downloading(downloadedBytes, totalBytes)
            read = stream.read(buffer)
        }
    }

    companion object {
        private const val UPDATES_SUBDIR = "updates"
        private const val SESSION_ENTRY_NAME = "update.apk"
        private const val DOWNLOAD_BUFFER_SIZE = 8192
        private const val BYTE_MASK = 0xFF
    }
}
