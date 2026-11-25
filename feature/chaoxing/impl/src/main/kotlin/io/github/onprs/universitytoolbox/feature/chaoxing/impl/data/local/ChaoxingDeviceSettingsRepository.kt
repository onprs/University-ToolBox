package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device.ChaoxingDeviceIdentityHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 学习通设备码显示开关与本机固定设备码访问契约。
 */
interface ChaoxingDeviceSettingsRepository {
    val hideDeviceCodeBadge: Flow<Boolean>

    suspend fun setHideDeviceCodeBadge(hide: Boolean)

    suspend fun getLocalMachineDeviceCode(): Pair<String, Boolean>

    fun getLocalDeviceModel(): String
}

/**
 * 基于自治 `chaoxing_preferences` 表的默认实现。
 */
@Singleton
class RoomChaoxingDeviceSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferenceDao: ChaoxingPreferenceDao,
) : ChaoxingDeviceSettingsRepository {

    override val hideDeviceCodeBadge: Flow<Boolean> = preferenceDao.observeValue(KEY_HIDE_DEVICE_BADGE)
        .map { value -> value == "true" }

    override suspend fun setHideDeviceCodeBadge(hide: Boolean) {
        preferenceDao.upsert(ChaoxingPreferenceEntity(key = KEY_HIDE_DEVICE_BADGE, value = hide.toString()))
    }

    override suspend fun getLocalMachineDeviceCode(): Pair<String, Boolean> {
        val deterministic = ChaoxingDeviceIdentityHelper.resolveLocalMachineDeviceCode(context)
        if (deterministic.isNotEmpty()) {
            return deterministic to true
        }
        val cached = preferenceDao.getValue(KEY_LOCAL_FALLBACK_DEVICE_CODE).orEmpty()
        if (cached.isNotEmpty()) {
            return cached to true
        }
        val generated = ChaoxingDeviceIdentityHelper.generateRandomDeviceCode()
        preferenceDao.upsert(ChaoxingPreferenceEntity(key = KEY_LOCAL_FALLBACK_DEVICE_CODE, value = generated))
        return generated to true
    }

    override fun getLocalDeviceModel(): String = ChaoxingDeviceIdentityHelper.localDeviceModel()

    companion object {
        private const val KEY_HIDE_DEVICE_BADGE = "hide_device_code_badge"
        private const val KEY_LOCAL_FALLBACK_DEVICE_CODE = "local_machine_device_code"
    }
}

/**
 * 面向单元测试的内存默认桩实现。
 */
class InMemoryChaoxingDeviceSettingsRepository(
    private val fixedLocalDeviceCode: String = "LOCAL_UNIFIED_DEVICE_CODE_STUB",
    private val fixedLocalModel: String = "TestModel",
) : ChaoxingDeviceSettingsRepository {
    private val mutableHide = MutableStateFlow(false)
    override val hideDeviceCodeBadge: Flow<Boolean> = mutableHide

    override suspend fun setHideDeviceCodeBadge(hide: Boolean) {
        mutableHide.value = hide
    }

    override suspend fun getLocalMachineDeviceCode(): Pair<String, Boolean> = fixedLocalDeviceCode to true

    override fun getLocalDeviceModel(): String = fixedLocalModel
}
