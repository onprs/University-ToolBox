package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学习通绑定账号本地存储实体。
 * 仅保存脱敏手机号、显示名、AES/AEAD 加密凭据密文与 Cookie 仓库分区引用，严禁明文保存密码。
 */
@Entity(tableName = "chaoxing_accounts")
data class ChaoxingAccountEntity(
    @PrimaryKey
    val accountId: String,
    val uid: String,
    val fid: String,
    val maskedAccount: String,
    val displayName: String,
    val schoolName: String,
    val avatarUrl: String,
    val encryptedPasswordCiphertext: String?,
    val encryptedCookieVaultRef: String,
    val isPrimary: Boolean,
    val isEnabled: Boolean,
    val sessionStatus: String,
    val lastVerifiedAtMs: Long,
    val createdAtMs: Long,
    val encryptedAccountInputCiphertext: String? = null,
    val deviceCode: String = "",
    val isUnifiedDeviceCode: Boolean = false,
    val clientUniqueId: String = "",
    val deviceModel: String = "",
)

/**
 * 学习通课程缓存实体（按账号与班级复合主键隔离）。
 */
@Entity(
    tableName = "chaoxing_courses",
    primaryKeys = ["accountId", "courseId", "clazzId"],
)
data class ChaoxingCourseEntity(
    val accountId: String,
    val courseId: Long,
    val clazzId: Long,
    val cpi: Long,
    val name: String,
    val className: String,
    val teacherName: String,
    val coverUrl: String,
    val studentCount: Int,
    val isArchived: Boolean,
    val isPinned: Boolean,
    val updatedAtMs: Long,
)

/**
 * 学习通本地签到审计明细实体。
 */
@Entity(tableName = "chaoxing_sign_records")
data class ChaoxingSignRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val accountId: String,
    val maskedAccount: String,
    val courseId: Long,
    val courseName: String,
    val clazzId: Long,
    val activeId: Long,
    val activityTitle: String,
    val signType: String,
    val outcome: String,
    val message: String,
    val timestampMs: Long,
)

/**
 * 用户保存的常用预设签到地点实体（经纬度统一采用超星位置签到接口要求的 BD-09 百度坐标系）。
 */
@Entity(tableName = "chaoxing_preset_locations")
data class ChaoxingPresetLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtMs: Long,
)

/**
 * 学习通模块自治键值配置实体（用于保存设备码图标显示开关及本机默认设备码缓存）。
 */
@Entity(tableName = "chaoxing_preferences")
data class ChaoxingPreferenceEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)
