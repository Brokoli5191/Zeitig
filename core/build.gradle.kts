import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    id("com.android.lint")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// The CI task set names Android's unit-test task; let it cover this JVM module as well.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Alias for :core:test so the shared CI task list covers this JVM module."
    dependsOn(tasks.test)
}

kover {
    reports {
        verify {
            rule("Leave-time engine line coverage") {
                minBound(90)
            }
        }
    }
}

lint {
    warningsAsErrors = true
    abortOnError = true
}
