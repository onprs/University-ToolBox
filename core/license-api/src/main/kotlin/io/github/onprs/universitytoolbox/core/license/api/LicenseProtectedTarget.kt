package io.github.onprs.universitytoolbox.core.license.api

/**
 * 受授权门禁保护的目标能力描述契约。
 * 供 `:core:tool-api` 中的工具描述模型实现，从而消除 `:core:license-api` 与 `:core:tool-api` 之间的循环依赖。
 */
interface LicenseProtectedTarget {
    val id: String
    val requiredTier: LicenseTier
    val scopeId: String
}
