import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        kotlin {
            // The playlist / Xtream / EPG code is shared with the Android TV app as-is; the few
            // android.* classes it touches are provided by small stand-ins under src/main/kotlin/android.
            srcDir("../app/src/main/java/dev/kutluhan/iptv/data")
            exclude("**/Storage.kt")
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.json:json:20250517")
    implementation("net.sf.kxml:kxml2:2.3.0")
    implementation("uk.co.caprica:vlcj:4.8.3")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
}

compose.desktop {
    application {
        mainClass = "dev.kutluhan.iptv.desktop.MainKt"
        jvmArgs += listOf("-Xmx1g")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "IPTV"
            packageVersion = (System.getenv("DESKTOP_VERSION") ?: "1.0.0")
            description = "IPTV oynatıcı"
            vendor = "Kutluhan"
            modules("java.naming", "java.sql")
            macOS {
                bundleID = "dev.kutluhan.iptv.desktop"
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                menuGroup = "IPTV"
                shortcut = true
                dirChooser = true
                upgradeUuid = "6b7c1f8e-2f4d-4a51-9d0e-3c6a1b2e9f40"
                iconFile.set(project.file("icons/icon.ico"))
            }
        }
    }
}
