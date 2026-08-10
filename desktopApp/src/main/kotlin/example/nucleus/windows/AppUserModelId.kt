package example.nucleus.windows

import io.github.aakira.napier.Napier
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets

/**
 * Registra el AppUserModelID del proceso (shell32.SetCurrentProcessExplicitAppUserModelID).
 *
 * Windows solo muestra el panel de medios del Sistema (SMTC / volumen / centro de notificaciones) para
 * aplicaciones de escritorio no empaquetadas si el proceso tiene un AppUserModelID explícito.
 *
 * Binding FFM (java.lang.foreign) en vez de JNA para que funcione en el binario GraalVM nativo.
 */
object AppUserModelId {
    private const val AUMID = "Tarma.MusicPlayer"

    private val linker: Linker = Linker.nativeLinker()
    private val arena: Arena = Arena.ofShared()

    private val setCurrentProcessExplicitAppUserModelId: MethodHandle? = runCatching {
        val shell32 = SymbolLookup.libraryLookup("shell32", arena)
        linker.downcallHandle(
            shell32.find("SetCurrentProcessExplicitAppUserModelID").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
        )
    }.onFailure { Napier.w("[appuserid] shell32 unavailable: ${it.message}") }.getOrNull()

    /** Codifica [s] como una C-string UTF-16LE NUL-terminada (LPCWSTR) en [allocator]. */
    private fun SegmentAllocator.utf16(s: String): MemorySegment {
        val bytes = s.toByteArray(StandardCharsets.UTF_16LE)
        val seg = allocate(bytes.size + 2L)
        bytes.forEachIndexed { i, b -> seg.set(ValueLayout.JAVA_BYTE, i.toLong(), b) }
        seg.set(ValueLayout.JAVA_BYTE, bytes.size.toLong(), 0.toByte())
        seg.set(ValueLayout.JAVA_BYTE, bytes.size.toLong() + 1, 0.toByte())
        return seg
    }

    /** Llama SetCurrentProcessExplicitAppUserModelID; no lanza si falla (la app sigue funcionando). */
    fun register() {
        // No-op temporal: un AUMID custom no registrado hace que el panel de medios muestre
        // "Aplicación Desconocida". Sin AUMID, Windows usa la identidad del ejecutable
        // (ProductName = PaltaSound). Si la sesión desaparece, restaurar.
        return
    }
}
