package example.nucleus.platform

import java.io.File
import javax.imageio.ImageIO

actual object ImageFilePicker {
    actual suspend fun pickImageFile(): ImageFileResult? {
        // Evitar AWT FileDialog en native-image (WFileDialogPeer JNI incompleto).
        val path = NativeDesktop.pickOpenFile(
            title = "Seleccionar imagen",
            filterDescription = "Images",
            extensions = listOf("jpg", "jpeg", "png"),
        ) ?: return null
        val imageFile = File(path)
        if (!imageFile.isFile) return null
        val image = ImageIO.read(imageFile) ?: return null
        if (image.width < 320 || image.height < 320) return null
        return ImageFileResult(
            fileName = imageFile.nameWithoutExtension,
            bytes = imageFile.readBytes(),
        )
    }
}
