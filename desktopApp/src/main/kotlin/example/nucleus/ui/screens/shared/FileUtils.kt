package example.nucleus.ui.screens.shared

import example.nucleus.platform.NativeDesktop
import java.io.File

fun openFolder(folder: File) {
    NativeDesktop.openFolder(folder)
}
