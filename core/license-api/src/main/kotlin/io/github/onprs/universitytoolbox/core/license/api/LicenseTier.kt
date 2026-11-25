package io.github.onprs.universitytoolbox.core.license.api

/**
 * 授权等级枚举，数值越大权限越高。
 */
enum class LicenseTier(val level: Int) {
    FREE(0),
    STANDARD(1),
    PRO(2),
    FULL(255),
    ;

    /** 判断当前授权等级是否满足目标等级要求。 */
    fun satisfies(required: LicenseTier): Boolean = level >= required.level
}
