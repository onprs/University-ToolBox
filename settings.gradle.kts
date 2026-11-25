pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "University-ToolBox"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val privateDirOverride = providers.gradleProperty("toolbox.privateLicenseDir")
    .orElse(providers.environmentVariable("TOOLBOX_PRIVATE_LICENSE_DIR"))
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

val isExplicitlyDisabled = privateDirOverride != null &&
    privateDirOverride.lowercase() in setOf("none", "disabled", "false")

val candidatePrivateDirs = when {
    isExplicitlyDisabled -> emptyList()
    privateDirOverride != null -> listOf(file(privateDirOverride))
    else -> listOf(
        file("private/Onprs-Encryption"),
        file("../Onprs-Encryption"),
    )
}

val isRootBuild = gradle.parent == null

val activePrivateDir = if (isRootBuild) {
    candidatePrivateDirs.firstOrNull { dir ->
        dir.resolve("settings.gradle.kts").exists()
    }
} else {
    null
}
val hasPrivateLicenseEngine = activePrivateDir != null && isRootBuild

includeBuild("core-contracts") {
    dependencySubstitution {
        substitute(module("io.github.onprs.universitytoolbox:common"))
            .using(project(":common"))
        substitute(module("io.github.onprs.universitytoolbox:license-api"))
            .using(project(":license-api"))
    }
}

if (activePrivateDir != null && isRootBuild) {
    includeBuild(activePrivateDir) {
        dependencySubstitution {
            substitute(module("io.github.onprs.encryption:license-engine-android"))
                .using(project(":license-engine-android"))
        }
    }
}

gradle.extra["useProprietaryLicense"] = hasPrivateLicenseEngine

val plannedModules = listOf(
    ":app",
    ":benchmark",
    ":core:license-stub",
    ":core:tool-api",
    ":core:designsystem",
    ":core:ui",
    ":core:network",
    ":core:datastore",
    ":core:security",
    ":core:database",
    ":core:scanner",
    ":core:update",
    ":feature:unlock",
    ":feature:home",
    ":feature:settings",
    ":feature:chaoxing:api",
    ":feature:chaoxing:impl",
    ":feature:gpa:api",
    ":feature:gpa:impl",
    ":feature:qrcode-studio:api",
    ":feature:qrcode-studio:impl",
)

plannedModules.forEach { modulePath ->
    val moduleDir = file(modulePath.removePrefix(":").replace(':', '/'))
    if (moduleDir.resolve("build.gradle.kts").exists()) {
        include(modulePath)
    }
}
