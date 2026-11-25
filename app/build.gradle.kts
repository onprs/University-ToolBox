val useProprietaryLicense = gradle.extra["useProprietaryLicense"] as? Boolean ?: false

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val releaseKeystoreFile = releaseKeystorePath?.let(::file)?.takeIf { it.exists() }

plugins {
    alias(libs.plugins.universitytoolbox.android.application)
    alias(libs.plugins.universitytoolbox.android.compose)
    alias(libs.plugins.universitytoolbox.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.onprs.universitytoolbox"

    defaultConfig {
        applicationId = "io.github.onprs.universitytoolbox"
        // 遵循 MAJOR * 1000000 + MINOR * 10000 + PATCH * 100
        versionCode = 1_000_000
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val kotlinCompilerVersion = libs.versions.kotlin.get()
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
            useVersion(kotlinCompilerVersion)
        }
    }
}

// 安全门禁：禁止在缺失闭源密钥引擎下构建正式 Release 包
tasks.matching { it.name.contains("Release", ignoreCase = true) }.configureEach {
    doFirst {
        check(useProprietaryLicense) {
            "Release 构建必须挂载私有仓库 Onprs-Encryption，严禁在缺失闭源密钥引擎下打包！"
        }
    }
}

dependencies {
    implementation(libs.universitytoolbox.core.common)
    implementation(libs.universitytoolbox.core.license.api)
    implementation(projects.core.toolApi)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.network)
    implementation(projects.core.datastore)
    implementation(projects.core.security)
    implementation(projects.core.database)
    implementation(projects.core.scanner)
    implementation(projects.core.update)

    implementation(projects.feature.unlock)
    implementation(projects.feature.home)
    implementation(projects.feature.settings)
    implementation(projects.feature.chaoxing.api)
    implementation(projects.feature.chaoxing.impl)
    implementation(projects.feature.gpa.api)
    implementation(projects.feature.gpa.impl)
    implementation(projects.feature.qrcodeStudio.api)
    implementation(projects.feature.qrcodeStudio.impl)

    if (useProprietaryLicense) {
        implementation("io.github.onprs.encryption:license-engine-android")
    } else {
        debugImplementation(projects.core.licenseStub)
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
