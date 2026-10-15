import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.simmer.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.simmer.app"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from env vars; when KEYSTORE_PATH is absent the release APK stays unsigned.
    val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
                keepRules {
                    files.add(project.file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime.ktx)
    implementation(libs.litertlm.android)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// A transitive dependency once pulled in an older kotlinx-coroutines than litertlm was compiled
// against, which crashed at runtime with NoSuchMethodError (#46). Fail if the resolved version
// drifts from the catalog.
val checkCoroutinesVersion by tasks.registering {
    val expected = libs.versions.coroutines.get()
    val resolved = configurations.named("debugRuntimeClasspath").flatMap { config ->
        config.incoming.resolutionResult.rootComponent.map { root ->
            val seen = mutableSetOf<String>()
            fun walk(c: ResolvedComponentResult) {
                c.moduleVersion?.let {
                    if (it.group == "org.jetbrains.kotlinx" && it.name.startsWith("kotlinx-coroutines-")) {
                        seen += "${it.name}:${it.version}"
                    }
                }
                c.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { d ->
                    if (seen.add("#" + d.selected.id.displayName)) walk(d.selected)
                }
            }
            walk(root)
            seen.filterNot { it.startsWith("#") }
        }
    }
    inputs.property("expected", expected)
    inputs.property("resolved", resolved)
    doLast {
        val stale = resolved.get().filterNot { it.endsWith(":$expected") }
        check(stale.isEmpty()) {
            "kotlinx-coroutines must resolve to $expected (litertlm needs it), found: ${stale.joinToString()}"
        }
    }
}
tasks.named("check") { dependsOn(checkCoroutinesVersion) }
