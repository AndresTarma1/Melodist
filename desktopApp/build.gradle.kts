import dev.nucleusframework.desktop.application.dsl.NativeImageMarch
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    id("dev.nucleusframework") version "2.1.9"

}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":shared"))
    val nucleusVersion = "2.1.9"



    implementation("dev.nucleusframework:nucleus.nucleus-application:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.updater-runtime:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.decorated-window-tao:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.decorated-window-material3:2.1.9")
    // Add only the modules you need:
    implementation("dev.nucleusframework:nucleus.notification-common:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.global-hotkey:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.autolaunch:2.1.9")

    implementation("dev.nucleusframework:composenativetray:2.0.3")


    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)


//    implementation(libs.compose.native.tray)

    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.decompose)
    implementation(libs.decompose.compose)
    implementation(libs.decompose.compose.experimental)

    implementation(libs.kotlinx.serialization.core)

    implementation(libs.coil.compose.get().toString()) {
        exclude(group = "org.jetbrains.skiko")
    }
    implementation(libs.coil.network.ktor3.get().toString()) {
        exclude(group = "org.jetbrains.skiko")
    }

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.materialKolor)
    implementation(libs.kmpalette.core)
    implementation(libs.kmpalette.network)
    implementation(libs.kmpalette.extensions.file)


    implementation(libs.reorderable)

    implementation(libs.heze)
    implementation(libs.heze.blur)

    implementation("ir.mahozad.multiplatform:wavy-slider:2.2.0")

    implementation(libs.composeSettings.ui)
    implementation(libs.composeSettings.ui.expressive)
    implementation(libs.composeSettings.ui.extended)



    implementation(libs.kotlin.test)

    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.jnativehook)

    implementation(libs.jna)
    implementation(libs.jna.platform.jpms)

    implementation(libs.jewel.ui.standalone)
    implementation(libs.jewel.ui.decorated.window)
    implementation(libs.jbr)



    implementation(compose.desktop.currentOs)
}


nucleus.application {
    mainClass = "example.nucleus.MainKt"
    jvmArgs("-Xmx512m")


    nativeDistributions {
        enableAotCache = true
        appName = "PaltaSound"
        packageName = "PaltaSound"
        packageVersion = "0.7.1"
        vendor = "Tarma"
        homepage = "https://github.com/AndresTarma1/PaltaSound"
        targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)

        windows {
            upgradeUuid = "4A2F8B6C-1D3E-4F5A-B7C8-9D0E1F2A3B4C"
            menu = true
            perUserInstall = true
            iconFile.set(project.file("src/icons/PaltaSound.ico"))
        }

        linux {
            iconFile.set(project.file("src/icons/PaltaSound.png"))
            debMaintainer = "Andres Tarma <andrestormenta1@gmail.com>"
        }

        includeAllModules = true
        appResourcesRootDir.set(project.layout.projectDirectory.dir("../mpv-resources"))
    }

}