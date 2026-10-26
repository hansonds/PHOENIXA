import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "org.phoenicx"
version = "1.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna:5.15.0")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}

compose.desktop {
    application {
        mainClass = "org.phoenicx.nova.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "PHOENIXA"
            packageVersion = "1.1.0"
            description = "PHOENIXA - Jellyfin client"
            vendor = "Hanson"
            modules(
                "java.instrument", "java.logging", "java.management", "java.naming",
                "java.net.http", "java.prefs", "java.sql", "jdk.crypto.ec", "jdk.unsupported",
            )
            windows {
                iconFile.set(project.file("icons/phoenixa.ico"))
                menuGroup = "PHOENIXA"
                upgradeUuid = "7d3f8a52-6c1e-4b8e-9a3d-2f5b1c9e4a71"
            }
        }
    }
}
