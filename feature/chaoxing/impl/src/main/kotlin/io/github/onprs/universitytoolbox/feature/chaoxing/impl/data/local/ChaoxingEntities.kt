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
