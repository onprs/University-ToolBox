package io.github.onprs.universitytoolbox.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 安装会话异步结果事件总线，桥接 [InstallResultReceiver] 与 [AppUpdateManager]。
 */
object InstallResultBus {
    sealed interface InstallEvent {
        data object Succeeded : InstallEvent

        data class PendingUserAction(val sessionId: Int, val statusMessage: String?) : InstallEvent

        data class Failed(val status: Int, val message: String?) : InstallEvent
    }

    private val mutableEvents = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<InstallEvent> = mutableEvents.asSharedFlow()

    internal fun publish(event: InstallEvent) {
        mutableEvents.tryEmit(event)
    }
}

/**
 * 接收 `PackageInstaller.Session` 提交后的系统安装状态回调广播。
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmIntent) }
                }
                InstallResultBus.publish(
                    InstallResultBus.InstallEvent.PendingUserAction(
                        sessionId = sessionId,
                        statusMessage = message,
                    ),
                )
            }

            PackageInstaller.STATUS_SUCCESS -> {
                InstallResultBus.publish(InstallResultBus.InstallEvent.Succeeded)
            }

            else -> {
                InstallResultBus.publish(
                    InstallResultBus.InstallEvent.Failed(
                        status = status,
                        message = message,
                    ),
                )
            }
        }
    }
}
