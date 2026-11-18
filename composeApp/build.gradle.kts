plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    // Real devices only; the CI job packages an arm64 .ipa.
    iosArm64 {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.13.0-beta02")
            implementation("org.jetbrains.compose.foundation:foundation:1.13.0-beta02")
            implementation("org.jetbrains.compose.ui:ui:1.13.0-beta02")
            // shortcut: beta, because Expressive APIs are internal in the stable 1.9.0; move to the stable release once it ships.
            implementation("org.jetbrains.compose.material3:material3:1.13.0-beta02")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.6.1")
        }
        all { languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi") }
    }
}
