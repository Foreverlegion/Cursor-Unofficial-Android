plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register<Exec>("checkReleaseNotes") {
    workingDir = rootDir
    commandLine("bash", "scripts/check-release-notes.sh")
}

gradle.projectsEvaluated {
    subprojects.forEach { sub ->
        listOf("assembleRelease", "bundleRelease", "check", "lintRelease").forEach { name ->
            sub.tasks.findByName(name)?.dependsOn(tasks.named("checkReleaseNotes"))
        }
    }
}
