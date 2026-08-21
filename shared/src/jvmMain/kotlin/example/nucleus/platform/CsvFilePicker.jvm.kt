package example.nucleus.platform

import java.io.File

actual object CsvFilePicker {
    actual suspend fun pickAndReadCsvFile(): CsvFileResult? {
        // No usar java.awt.FileDialog: en GraalVM Windows falla con
        // NoSuchFieldError: sun.awt.windows.WFileDialogPeer.parent
        val path = NativeDesktop.pickOpenFile(
            title = "Importar CSV",
            filterDescription = "CSV",
            extensions = listOf("csv"),
        ) ?: return null
        val csvFile = File(path)
        if (!csvFile.isFile) return null
        return CsvFileResult(
            fileName = csvFile.nameWithoutExtension,
            content = csvFile.readText(),
        )
    }
}
