package example.nucleus.platform

data class ImageFileResult(
    val fileName: String,
    val bytes: ByteArray,
)

expect object ImageFilePicker {
    suspend fun pickImageFile(): ImageFileResult?
}
