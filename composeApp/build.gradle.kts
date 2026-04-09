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


            implementation("io.github.kdroidfilter:composenativetray:1.1.0")

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

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            // NOTA: los DLLs de VLC no van como resources del JAR;
            // se incluyen via appResourcesRootDir para que queden junto al .exe
            dependencies {
                implementation(compose.desktop.currentOs){
                    exclude(group = "org.jetbrains.compose.material")
                }
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.composemediaplayer)
                implementation(libs.jnativehook)
                // Source: https://mvnrepository.com/artifact/net.java.dev.jna/jna
                implementation("net.java.dev.jna:jna:5.18.1")

                // Source: https://mvnrepository.com/artifact/net.java.dev.jna/jna-platform-jpms
                implementation("net.java.dev.jna:jna-platform-jpms:5.18.1")

                implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.34.0-253.31033.149")

                // Source: https://mvnrepository.com/artifact/org.jetbrains.runtime/jbr-api
                implementation("org.jetbrains.runtime:jbr-api:1.10.1")
//
//                // Optional, for custom decorated windows:
                implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.34.0-253.31033.149")

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
            packageVersion = "1.0.3"
            vendor = "Tarma"
            description = "Reproductor de música de escritorio"

            // Módulos específicos para evitar el peso excesivo de includeAllModules
            modules("java.base", "java.desktop", "java.sql", "java.management", "java.naming", "java.prefs", "java.xml", "jdk.unsupported", "java.instrument", "jdk.jfr", "jdk.crypto.ec")

            // Compose Desktop copia el contenido de windows/ (o common/) de este dir
            // a $APPDIR/resources/ durante el packaging.
            // La carpeta mpv-resources/windows/ debe contener libmpv-2.dll
            appResourcesRootDir.set(project.layout.projectDirectory.dir("../mpv-resources"))

            windows {
                packageName = "Melodist Beta"
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
