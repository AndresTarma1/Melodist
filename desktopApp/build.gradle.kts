import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    id("dev.nucleusframework") version "2.3.2"

}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":shared"))
    val nucleusVersion = "2.3.2"



    implementation("dev.nucleusframework:nucleus.nucleus-application:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.updater-runtime:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.decorated-window-tao:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.decorated-window-material3:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.graalvm-runtime:${nucleusVersion}")
    // Add only the modules you need:
    implementation("dev.nucleusframework:nucleus.notification-common:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.global-hotkey:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.autolaunch:${nucleusVersion}")

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

    // Perfil de memoria: SerialGC (footprint mínimo, sin card-tables/regiones de G1) + heap
    // moderado. Devuelve heap al OS vía Min/MaxHeapFreeRatio en cada GC full (el FrameWatcher
    // de Skiko lo dispara periódicamente). Ver Oracle GC tuning + Skiko configuration.
    jvmArgs(
        // Heap
        "-Xms64m",
        "-Xmx320m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=100",
        // G1PeriodicGC libera periódicamente el heap Y los objetos nativos de Skia (imágenes):
        // sin GC completo periódico, la memoria nativa de Skia/Compose se acumula (peak 740MB).
        "-XX:G1PeriodicGCInterval=10000",
        "-XX:G1PeriodicGCSystemLoadThreshold=0.0",
        "-XX:+UseStringDeduplication",
        // Encoger el heap y devolverlo al OS cuando sobra (default 40/70 retiene demasiado).
        "-XX:MinHeapFreeRatio=10",
        "-XX:MaxHeapFreeRatio=30",
        // No-heap acotado (hoy sin tope; Compose+Ktor+Coil cargan muchas clases).
        "-XX:MaxMetaspaceSize=192m",
        "-XX:CompressedClassSpaceSize=64m",
        "-XX:ReservedCodeCacheSize=128m",
        "-XX:CICompilerCount=2",
        // Limita los "cores vistos" por la JVM: reduce ForkJoinPool/Dispatchers.Default (32->4),
        // el dispatcher de OkHttp y los hilos de GC. Para un reproductor bastan 4 workers CPU.
        "-XX:ActiveProcessorCount=4",
        // Pilas de hilo más pequeñas (muchos hilos IO/render).
        "-Xss768k",
        // Pool IO de coroutines: default real es 64 hilos (~1MB stack c/u).
        "-Dkotlinx.coroutines.io.parallelism=16",
        // Skiko: render en GPU (Direct3D) pero con caché de recursos acotada (default ilimitada).
        "-Dskiko.gpu.resourceCacheLimit=128M",
        "-Dskiko.buffering=DOUBLE",
        "-Dskiko.vsync.enabled=true",
    )


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

    // ── GraalVM Native Image (EXPERIMENTO) ──────────────────────────────────
    // Compila la app a un binario nativo sin JVM. Nucleus genera los metadatos de
    // reflexión/recursos/JNI automáticamente (5 niveles) y descarga GraalVM CE.
    // Tareas: packageGraalvmNative, runGraalvmNative, runWithNativeAgent.
    graalvm {
        isEnabled = true
        imageName = "paltasound"
        // GUI desktop: AWT no-headless explícito para native-image.
        buildArgs.add("-Djava.awt.headless=false")
        // SLF4J termina en el image heap durante el build (doc de Nucleus: paquete completo).
        buildArgs.add("--initialize-at-build-time=org.slf4j")
        // Iconos del thumbbar: incluir los .ico como recursos del image heap para que
        // getResourceAsStream("/thumbbar/...") funcione en el binario nativo.
        buildArgs.add("-H:IncludeResources=thumbbar/.*")
        // Heap del binario nativo acotado (objetivo RAM); Serial GC por defecto.
        maxHeapSize = "320m"
    }

}