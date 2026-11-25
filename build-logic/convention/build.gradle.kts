import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.sam.with.receiver)
}

group = "io.github.onprs.universitytoolbox.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "universitytoolbox.android.application"
            implementationClass = "io.github.onprs.universitytoolbox.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "universitytoolbox.android.library"
            implementationClass = "io.github.onprs.universitytoolbox.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "universitytoolbox.android.compose"
            implementationClass = "io.github.onprs.universitytoolbox.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "universitytoolbox.android.hilt"
            implementationClass = "io.github.onprs.universitytoolbox.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "universitytoolbox.android.feature"
            implementationClass = "io.github.onprs.universitytoolbox.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "universitytoolbox.android.room"
            implementationClass = "io.github.onprs.universitytoolbox.buildlogic.AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "universitytoolbox.jvm.library"
            implementationClass = "io.github.onprs.universitytoolbox.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
