package example.nucleus.data.migration

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDataMigrationTest {

    private val tmp = Files.createTempDirectory("migration-test")

    private fun newDir(vararg parts: String): File {
        val dir = tmp.resolve(parts.joinToString("/").replace('\\', '/')).toFile()
        dir.mkdirs()
        return dir
    }

    private fun writeDb(dbFile: File, downloadedIds: List<String>) {
        dbFile.parentFile.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE Song (id TEXT PRIMARY KEY NOT NULL, isDownloaded INTEGER NOT NULL DEFAULT 0)")
                downloadedIds.forEach { id ->
                    st.execute("INSERT INTO Song (id, isDownloaded) VALUES ('$id', 1)")
                }
            }
        }
    }

    private fun setOfFiles(dir: File): Set<String> {
        if (!dir.exists()) return emptySet()
        return dir.walk().filter { it.isFile }.map { it.absolutePath }.toSet()
    }

    @Test
    fun `clean install - nothing to migrate`() {
        val roaming = newDir("roaming", "PaltaSound")
        val local = newDir("local", "PaltaSound")
        val result = AppDataMigration.run(roaming.absolutePath, local.absolutePath, "PaltaSound")
        assertEquals(AppDataMigration.MigrationResult.NOTHING_TO_MIGRATE, result)
    }

    @Test
    fun `already migrated - flag present`() {
        val roaming = newDir("roaming2", "PaltaSound")
        val local = newDir("local2", "PaltaSound")
        File(roaming, "migration.json").writeText("{}")
        val result = AppDataMigration.run(roaming.absolutePath, local.absolutePath, "PaltaSound")
        assertEquals(AppDataMigration.MigrationResult.ALREADY_MIGRATED, result)
    }

    @Test
    fun `coexistence - both folders with content - skipped`() {
        newDir("coex", "LyriK")
        val roaming = newDir("coex", "PaltaSound")
        val local = newDir("coex-local", "PaltaSound")
        File(roaming, "settings.properties").writeText("existing")
        val result = AppDataMigration.run(roaming.absolutePath, local.absolutePath, "PaltaSound")
        assertEquals(AppDataMigration.MigrationResult.SKIPPED_COEXISTENCE, result)
        assertTrue(File(roaming.parentFile, "LyriK").exists())
    }

    @Test
    fun `migrate - moves data, writes flag and schema_version, verifies songs`() {
        val oldRoaming = newDir("mig", "LyriK")
        val oldLocal = newDir("mig-local", "LyriK")
        writeDb(File(oldRoaming, "db/musicplayer.db"), listOf("abc", "def"))
        newDir("mig-local", "LyriK", "cache", "songs").apply {
            File(this, "abc.m4a").writeBytes(ByteArray(8))
            File(this, "def.webm").writeBytes(ByteArray(8))
        }

        val newRoaming = newDir("mig", "PaltaSound")
        val newLocal = newDir("mig-local", "PaltaSound")

        val result = AppDataMigration.run(newRoaming.absolutePath, newLocal.absolutePath, "PaltaSound")

        assertEquals(AppDataMigration.MigrationResult.MIGRATED, result)
        assertTrue(!oldRoaming.exists(), "carpeta roaming antigua debe haber sido movida")
        assertTrue(!oldLocal.exists(), "carpeta local antigua debe haber sido movida")
        assertTrue(File(newRoaming, "db/musicplayer.db").exists(), "DB en nueva ubicación")
        assertTrue(File(newRoaming, "migration.json").exists(), "flag de migración")
        assertEquals("5", File(newRoaming, "db/schema_version").readText().trim(), "schema_version compatible")
        assertTrue(File(newLocal, "cache/songs/abc.m4a").exists(), "canción migrada")
        assertTrue(File(newLocal, "cache/songs/def.webm").exists(), "canción migrada")
    }

    @Test
    fun `migrate - legacy db name normalized to musicplayer_db`() {
        val oldRoaming = newDir("legacy", "LyriK")
        val oldLocal = newDir("legacy-local", "LyriK")
        writeDb(File(oldRoaming, "db/melodist.db"), listOf("abc"))
        newDir("legacy-local", "LyriK", "cache", "songs").apply {
            File(this, "abc.m4a").writeBytes(ByteArray(8))
        }

        val newRoaming = newDir("legacy", "PaltaSound")
        val newLocal = newDir("legacy-local", "PaltaSound")

        val result = AppDataMigration.run(newRoaming.absolutePath, newLocal.absolutePath, "PaltaSound")

        assertEquals(AppDataMigration.MigrationResult.MIGRATED, result)
        assertTrue(File(newRoaming, "db/musicplayer.db").exists(), "DB renombrada a musicplayer_db")
        assertTrue(!File(newRoaming, "db/melodist.db").exists(), "melodist_db ya no debe existir")
    }

    @Test
    fun `verification failure - rolls back, old data intact`() {
        val oldRoaming = newDir("fail", "LyriK")
        val oldLocal = newDir("fail-local", "LyriK")
        // La DB declara una canción descargada, pero el archivo no existe -> verificación falla.
        writeDb(File(oldRoaming, "db/musicplayer.db"), listOf("abc"))

        val newRoaming = newDir("fail", "PaltaSound")
        val newLocal = newDir("fail-local", "PaltaSound")

        val result = AppDataMigration.run(newRoaming.absolutePath, newLocal.absolutePath, "PaltaSound")

        assertEquals(AppDataMigration.MigrationResult.FAILED_ROLLED_BACK, result)
        assertTrue(File(oldRoaming, "db/musicplayer.db").exists(), "roaming antigua intacta tras rollback")
        assertTrue(!newRoaming.exists(), "carpeta nueva eliminada tras rollback")
        assertTrue(!File(newRoaming, "migration.json").exists(), "no debe quedar flag")
    }
}
