plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

extra["useProprietaryLicense"] = gradle.extra["useProprietaryLicense"]

val coreContractsBuild = gradle.includedBuild("core-contracts")

tasks.register("testCoreContracts") {
    group = "verification"
    description = "运行 core-contracts 叶子构建的单元测试"
    dependsOn(
        coreContractsBuild.task(":common:test"),
        coreContractsBuild.task(":license-api:test"),
    )
}

tasks.register("ktlintCoreContracts") {
    group = "verification"
    description = "运行 core-contracts 叶子构建的 Ktlint 检查"
    dependsOn(
        coreContractsBuild.task(":common:ktlintCheck"),
        coreContractsBuild.task(":license-api:ktlintCheck"),
    )
}

tasks.register("detektCoreContracts") {
    group = "verification"
    description = "运行 core-contracts 叶子构建的 Detekt 检查"
    dependsOn(
        coreContractsBuild.task(":common:detekt"),
        coreContractsBuild.task(":license-api:detekt"),
    )
}

tasks.register("checkCoreContracts") {
    group = "verification"
    description = "运行 core-contracts 叶子构建的测试与静态检查"
    dependsOn("testCoreContracts", "ktlintCoreContracts", "detektCoreContracts")
}
