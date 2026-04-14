import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.conveyor)
}

kotlin {
    jvm()

    jvmToolchain(21) // Esto fuerza a Gradle a buscar/descargar un JDK completo (v21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)


            implementation(libs.compose.native.tray)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.kotlinx.serialization.core)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            implementation(libs.materialKolor)
            implementation(libs.kmpalette.core)
            implementation(libs.kmpalette.network)
            implementation(libs.kmpalette.extensions.file)
            implementation(libs.conveyor.control)

            implementation(libs.reorderable)

            implementation(libs.heze)


        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs){
                    exclude(group = "org.jetbrains.compose.material")
                }
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.jnativehook)

                implementation(libs.jna)
                implementation(libs.jna.platform.jpms)


                implementation(libs.jewel.ui.standalone)
                implementation(libs.jewel.ui.decorated.window)
                implementation(libs.jbr)

            }
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.example.melodist.MainKt"

        jvmArgs(
            "--add-modules=java.sql",
            "--enable-native-access=ALL-UNNAMED",
            "-Dorg.sqlite.tmpdir=${System.getProperty("user.home")}/.melodist/tmp",
            "-XX:+EnableDynamicAgentLoading",
            "-Xmx512m",                           // 512MB es el "sweet spot" para apps ligeras
            "-Xms128m",                           // Iniciar con algo más de aire
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=50",            // Un poco más de margen para que el GC termine
            "-XX:+UseStringDeduplication",
            "-XX:+UseCompressedOops",
            "-XX:MaxMetaspaceSize=128m",          // Dale un poco más de espacio a las clases
            "-XX:+ExitOnOutOfMemoryError"         // Mejor que la app cierre a que se quede congelada
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Melodist"
            packageVersion = "1.0.5"
            vendor = "Tarma"
            description = "Reproductor de música de escritorio"

            // Módulos específicos para evitar el peso excesivo de includeAllModules
            modules("java.base", "java.desktop", "java.sql", "java.management", "java.naming", "java.prefs", "java.xml", "jdk.unsupported", "java.instrument", "jdk.jfr", "jdk.crypto.ec")

            // Compose Desktop copia el contenido de windows/ (o common/) de este dir
            // a $APPDIR/resources/ durante el packaging.
            // La carpeta mpv-resources/windows/ debe contener libmpv-2.dll
            appResourcesRootDir.set(project.layout.projectDirectory.dir("../mpv-resources"))

            windows {
                packageName = "Melodist"
                menu = true
                menuGroup = "Melodist"
                shortcut = true
                dirChooser = true // Esto es lo que más suelen pedir los usuarios
                perUserInstall = true // Recomendado para evitar problemas de permisos
                upgradeUuid = "4A2F8B6C-1D3E-4F5A-B7C8-9D0E1F2A3B4C"
            }
        }
    }
}
