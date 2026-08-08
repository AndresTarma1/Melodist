package example.nucleus.data.migration

import example.nucleus.data.AppDirs
import example.nucleus.data.local.DatabaseDriverFactory
import example.nucleus.platform.AppPaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.sql.DriverManager
import java.time.Instant
import java.util.logging.Logger

/**
 * Migración one-shot de datos de la aplicación desde el nombre antiguo "LyriK"
 * hacia el nombre vigente "PaltaSound".
 *
 * Debe ejecutarse ANTES de [AppDirs.ensureDirectories] (si no, la creación de la
 * carpeta nueva impediría detectar que la migración es necesaria).
 *
 * Estrategia por volumen:
 *  - Mismo volumen:  rename atómico LyriK -> PaltaSound (no duplica espacio en disco
 *                    con las canciones descargadas; nada se elimina, solo se reubica).
 *  - Distinto volumen: copia completa -> verificación -> rename del origen a LyriK.bak
 *                    (red de seguridad). La copia es la única vía posible.
 *
 * Si la verificación de integridad falla en cualquier punto, se hace rollback de lo
 * ya movido y la carpeta antigua queda intacta.
 */
object AppDataMigration {

    private val log = Logger.getLogger("AppDataMigration")

    private const val OLD_APP_NAME = "LyriK"
    private const val MIGRATION_VERSION = 1
    private const val FLAG_FILE = "migration.json"
    private const val BACKUP_SUFFIX = ".bak"
    private const val DB_DIR = "db"
    private const val DB_FILE = "musicplayer.db"

    /** Nombres de archivo de DB usados por versiones anteriores de la app. */
    private val LEGACY_DB_NAMES = listOf("melodist.db", "lyrik.db")
    private const val SONGS_DIR = "cache/songs"
    private val SONG_EXTENSIONS = listOf("m4a", "webm", "ogg")

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    private data class MigrationFlag(
        val migratedFrom: String,
        val migrationVersion: Int,
        val migratedAt: String,
    )

    enum class MigrationResult {
        /** Ya se migró en una ejecución anterior (flag presente). */
        ALREADY_MIGRATED,

        /** No existe la carpeta antigua (instalación limpia). */
        NOTHING_TO_MIGRATE,

        /** Existen ambas carpetas con contenido — coexistencia; no se toca nada. */
        SKIPPED_COEXISTENCE,

        /** Migración completada y verificada. */
        MIGRATED,

        /** La migración falló y se revirtió; los datos antiguos quedan intactos. */
        FAILED_ROLLED_BACK,
    }

    fun runIfNeeded(): MigrationResult = run(AppPaths.roamingRoot, AppPaths.localRoot, AppPaths.appName)

    /**
     * Lógica de migración parametrizada por raíces, testable en aislamiento
     * (las raíces reales vienen de [AppPaths], que no es inyectable).
     */
    internal fun run(roamingRoot: String, localRoot: String, appName: String): MigrationResult {
        val newRoaming = File(roamingRoot)
        val newLocal = File(localRoot)
        val oldRoaming = File(newRoaming.parentFile, OLD_APP_NAME)
        val oldLocal = File(newLocal.parentFile, OLD_APP_NAME)

        val flag = File(newRoaming, FLAG_FILE)
        if (flag.exists()) {
            log.info("Migración ya realizada (${flag.absolutePath}) — skip.")
            return MigrationResult.ALREADY_MIGRATED
        }

        val oldExists = oldRoaming.exists() || oldLocal.exists()
        if (!oldExists) {
            log.info("No existe carpeta antigua de datos ($OLD_APP_NAME) — instalación limpia.")
            return MigrationResult.NOTHING_TO_MIGRATE
        }

        val newHasContent = (newRoaming.exists() && newRoaming.listFiles()?.isNotEmpty() == true) ||
            (newLocal.exists() && newLocal.listFiles()?.isNotEmpty() == true)
        if (newHasContent) {
            log.warning(
                "Existen datos nuevos (${newRoaming.path}) y antiguos ($OLD_APP_NAME) sin flag de " +
                    "migración. Se asume coexistencia/instalación paralela y NO se toca nada. " +
                    "Si deseas migrar, elimina manualmente la carpeta nueva y reintenta."
            )
            return MigrationResult.SKIPPED_COEXISTENCE
        }

        log.info(
            "Iniciando migración de datos: $OLD_APP_NAME -> $appName" +
                "\n  roaming: ${oldRoaming.path} -> ${newRoaming.path}" +
                "\n  local:   ${oldLocal.path} -> ${newLocal.path}"
        )

        // Fase 1: mover (o copiar) roaming y local, registrando qué se movió vs copió.
        val moved = mutableListOf<Pair<File, File>>()
        val copied = mutableListOf<Pair<File, File>>()
        try {
            if (oldRoaming.exists()) {
                val wasCopy = moveOrCopy(oldRoaming, newRoaming)
                if (wasCopy) copied += oldRoaming to newRoaming else moved += oldRoaming to newRoaming
            }
            if (oldLocal.exists()) {
                val wasCopy = moveOrCopy(oldLocal, newLocal)
                if (wasCopy) copied += oldLocal to newLocal else moved += oldLocal to newLocal
            }
        } catch (e: Exception) {
            log.severe("Fallo moviendo datos: ${e.message}")
            rollback(moved, copied)
            return MigrationResult.FAILED_ROLLED_BACK
        }

        // Fase 1b: normalizar el nombre del archivo de DB (versiones antiguas usaban
        // melodist.db / lyrik.db; el driver actual espera musicplayer.db).
        try {
            normalizeDatabaseFileName(newRoaming)
        } catch (e: Exception) {
            log.severe("Fallo normalizando nombre de DB: ${e.message}")
            rollback(moved, copied)
            return MigrationResult.FAILED_ROLLED_BACK
        }

        // Fase 2: verificar integridad.
        val problems = verify(newRoaming, newLocal)
        if (problems.isNotEmpty()) {
            log.severe("Verificación de migración FALLÓ:\n" + problems.joinToString("\n  - ") { "  - $it" })
            rollback(moved, copied)
            return MigrationResult.FAILED_ROLLED_BACK
        }

        // Fase 3: commit — flag de versión + schema_version (evita que el driver borre la DB) + backup.
        try {
            writeFlag(flag)
            writeSchemaVersion(newRoaming)
            backupCopiedSources(copied)
        } catch (e: Exception) {
            log.severe("Migración movida pero falló el commit final: ${e.message}. Datos en la nueva ubicación.")
            return MigrationResult.FAILED_ROLLED_BACK
        }

        log.info("Migración completada exitosamente de $OLD_APP_NAME a $appName.")
        return MigrationResult.MIGRATED
    }

    // ─── Fase de movimiento ──────────────────────────────────────────────

    /**
     * Intenta rename atómico (mismo volumen). Si falla por cambio de volumen o por
     * no soporte atómico, hace copia recursiva completa. Devuelve `true` si se copió
     * (origen intacto, candidato a .bak) o `false` si se movió (origen ya no existe).
     */
    private fun moveOrCopy(src: File, dst: File): Boolean {
        dst.parentFile?.mkdirs()
        if (!src.exists()) return false

        try {
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
            log.info("Moved (atomic rename): ${src.path} -> ${dst.path}")
            return false
        } catch (_: AtomicMoveNotSupportedException) {
        } catch (_: FileSystemException) {
        }

        return try {
            Files.move(src.toPath(), dst.toPath())
            log.info("Moved (rename): ${src.path} -> ${dst.path}")
            false
        } catch (_: Exception) {
            log.info("Rename no disponible (¿otro volumen?). Copiando: ${src.path} -> ${dst.path}")
            copyRecursively(src, dst)
            true
        }
    }

    private fun copyRecursively(src: File, dst: File) {
        if (src.isDirectory && !dst.exists()) dst.mkdirs()
        Files.walkFileTree(src.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
            override fun preVisitDirectory(dir: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                val rel = src.toPath().relativize(dir)
                Files.createDirectories(dst.toPath().resolve(rel))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                val rel = src.toPath().relativize(file)
                Files.copy(file, dst.toPath().resolve(rel), StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /** Renombra los orígenes copiados a `<nombre>.bak` como red de seguridad. */
    private fun backupCopiedSources(copied: List<Pair<File, File>>) {
        copied.forEach { (old, _) ->
            val bak = File(old.parentFile, old.name + BACKUP_SUFFIX)
            if (old.exists()) {
                if (bak.exists()) {
                    log.warning("Ya existe ${bak.path} — no se sobrescribe.")
                } else if (old.renameTo(bak)) {
                    log.info("Origen renombrado a red de seguridad: ${bak.path}")
                } else {
                    log.warning("No se pudo renombrar ${old.path} a ${bak.path}. Se mantiene la carpeta original.")
                }
            }
        }
    }

    /** Revierte los movimientos/copias ya hechos para dejar la carpeta antigua intacta. */
    private fun rollback(moved: List<Pair<File, File>>, copied: List<Pair<File, File>>) {
        // Los movidos se devuelven a su ubicación original; los copiados se borran (el origen sigue intacto).
        moved.forEach { (old, new) ->
            if (new.exists()) {
                try {
                    Files.move(new.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    log.info("Rollback: ${new.path} -> ${old.path}")
                } catch (e: Exception) {
                    log.severe("Rollback fallido para ${new.path}: ${e.message}")
                }
            }
        }
        copied.forEach { (_, new) ->
            if (new.exists()) {
                try {
                    new.deleteRecursively()
                    log.info("Rollback: copia eliminada ${new.path}")
                } catch (e: Exception) {
                    log.severe("Rollback fallido eliminando copia ${new.path}: ${e.message}")
                }
            }
        }
    }

    // ─── Fase de verificación ────────────────────────────────────────────

    /**
     * Si la DB migrada conserva un nombre de una versión anterior (melodist.db, lyrik.db),
     * lo renombra a [DB_FILE] para que [DatabaseDriverFactory] la encuentre.
     */
    private fun normalizeDatabaseFileName(newRoaming: File) {
        val dbDir = File(newRoaming, DB_DIR)
        if (!dbDir.exists()) return
        val target = File(dbDir, DB_FILE)
        if (target.exists()) {
            log.info("DB ya tiene el nombre esperado: ${target.name}")
            return
        }
        val legacy = LEGACY_DB_NAMES
            .map { File(dbDir, it) }
            .firstOrNull { it.exists() }
        if (legacy == null) return

        if (legacy.renameTo(target)) {
            log.info("DB renombrada: ${legacy.name} -> ${target.name}")
        } else {
            throw Exception("No se pudo renombrar ${legacy.name} a ${target.name}")
        }
    }

    /** Devuelve una lista vacía si la migración es íntegra; si no, los problemas encontrados. */
    private fun verify(newRoaming: File, newLocal: File): List<String> {
        val problems = mutableListOf<String>()

        val dbFile = File(File(newRoaming, DB_DIR), DB_FILE)
        if (!dbFile.exists()) {
            problems += "DB no encontrada tras migrar: ${dbFile.path}"
            return problems
        }

        // 1) Abrir la DB migrada y listar las canciones descargadas.
        val downloadedIds: List<String> = try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT id FROM Song WHERE isDownloaded = 1").use { rs ->
                        buildList { while (rs.next()) add(rs.getString("id")) }
                    }
                }
            }
        } catch (e: Exception) {
            problems += "No se pudo abrir la DB migrada (${dbFile.path}): ${e.message}"
            return problems
        }

        // 2) Cada canción descargada debe existir como archivo en la carpeta de descargas migrada.
        val songsDir = File(newLocal, SONGS_DIR)
        if (downloadedIds.isNotEmpty()) {
            if (!songsDir.exists()) {
                problems += "Hay ${downloadedIds.size} canciones descargadas pero la carpeta de descargas no existe: ${songsDir.path}"
            } else {
                val missing = downloadedIds.filter { id ->
                    SONG_EXTENSIONS.none { ext -> File(songsDir, "$id.$ext").exists() }
                }
                if (missing.isNotEmpty()) {
                    problems += "Faltan ${missing.size} de ${downloadedIds.size} archivos descargados (ej: ${missing.take(5).joinToString(", ")})"
                }
            }
        }

        if (problems.isEmpty()) {
            log.info("Verificación OK: DB abierta, ${downloadedIds.size} canción(es) descargada(s) verificada(s) en ${songsDir.path}.")
        }
        return problems
    }

    // ─── Fase de commit ──────────────────────────────────────────────────

    private fun writeFlag(flag: File) {
        val flagContent = json.encodeToString(
            MigrationFlag(
                migratedFrom = OLD_APP_NAME,
                migrationVersion = MIGRATION_VERSION,
                migratedAt = Instant.now().toString(),
            )
        )
        flag.parentFile?.mkdirs()
        flag.writeText(flagContent)
        log.info("Flag de migración escrito: ${flag.path}")
    }

    /**
     * Asegura que el archivo schema_version de la carpeta migrada coincida con la versión
     * que espera [DatabaseDriverFactory]. Si quedara con una versión menor o ausente, el
     * driver recrearía (borrando) la DB migrada — exactamente lo que hay que evitar.
     */
    private fun writeSchemaVersion(newRoaming: File) {
        try {
            val versionFile = File(File(newRoaming, DB_DIR), "schema_version")
            if (versionFile.exists() && versionFile.readText().trim().toLong() >= DatabaseDriverFactory.APP_SCHEMA_VERSION) {
                log.info("schema_version de la DB migrada ya es compatible (${versionFile.readText().trim()}).")
                return
            }
            versionFile.writeText(DatabaseDriverFactory.APP_SCHEMA_VERSION.toString())
            log.info("schema_version actualizado a ${DatabaseDriverFactory.APP_SCHEMA_VERSION} tras migrar.")
        } catch (e: Exception) {
            log.warning("No se pudo ajustar schema_version: ${e.message}")
        }
    }
}
