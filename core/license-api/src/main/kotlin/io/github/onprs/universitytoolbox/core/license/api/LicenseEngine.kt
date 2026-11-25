package io.github.onprs.universitytoolbox.core.license.api

import kotlinx.coroutines.flow.StateFlow

/**
 * 公私双仓边界核心授权引擎接口。
 * 公开主仓库仅依赖此契约消费状态；正式加解密与验签逻辑由独立私有引擎或调试桩分别实现。
 */
interface LicenseEngine {
    val state: StateFlow<LicenseState>

    /** 获取当前设备特征质询码，用于向发卡端请求绑定本机的激活密文。 */
    suspend fun getDeviceChallenge(): String

    /** 验证并激活用户输入的原始激活密钥字符串。 */
    suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement>

    /** 注入来自可信 HTTPS 响应头的服务端 UTC 秒级时间，用于时钟回拨检测与有效期校验。 */
    suspend fun syncTrustedNetworkTime(serverEpochSec: Long)

    /** 更新远程吊销列表中的密钥序列号集合。 */
    suspend fun updateRevokedList(revokedIds: Set<String>)

    /** 清除本地授权并恢复至未激活锁定态。 */
    suspend fun clearLicense()
}
