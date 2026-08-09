package example.nucleus.utils

import io.github.aakira.napier.Napier
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * Solicita a Windows reducir el working set del proceso (RAM residente) al mínimo, devolviendo las
 * páginas inactivas al sistema operativo — el mismo efecto que produce un "limpiador de RAM", pero
 * ejecutado por nosotros. Las páginas se cargan de nuevo bajo demanda, por lo que esto solo debe
 * llamarse cuando la app está inactiva (ej. minimizada en la bandeja) para evitar la leve
 * interrupción de volver a cargarlas. Gran parte del RSS de una app de Compose Desktop es memoria
 * nativa comprometida pero inactiva (superficies de Skia, pilas de hilos, buffers); esto evita que
 * permanezca residente mientras está oculta.
 *
 * Binding FFM (java.lang.foreign) en vez de JNA para que funcione en el binario GraalVM nativo.
 * No hace nada en plataformas que no sean Windows o si la llamada falla.
 */
object WorkingSetTrimmer {

    private val linker: Linker = Linker.nativeLinker()
    private val arena: Arena = Arena.ofShared()

    private val kernel32: SymbolLookup? = runCatching { SymbolLookup.libraryLookup("kernel32", arena) }
        .onFailure { Napier.w("[mem] kernel32 unavailable: ${it.message}") }
        .getOrNull()

    private val getCurrentProcess: MethodHandle?
    private val setProcessWorkingSetSize: MethodHandle?

    init {
        val k32 = kernel32
        if (k32 != null) {
            getCurrentProcess = runCatching {
                linker.downcallHandle(
                    k32.find("GetCurrentProcess").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS),
                )
            }.onFailure { Napier.w("[mem] GetCurrentProcess unavailable: ${it.message}") }.getOrNull()

            setProcessWorkingSetSize = runCatching {
                linker.downcallHandle(
                    k32.find("SetProcessWorkingSetSize").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
                )
            }.onFailure { Napier.w("[mem] SetProcessWorkingSetSize unavailable: ${it.message}") }.getOrNull()
        } else {
            getCurrentProcess = null
            setProcessWorkingSetSize = null
        }
    }

    /** Reduce el working set. Pasar (-1, -1) le indica a Windows que recupere todo lo posible. */
    fun trim() {
        val get = getCurrentProcess ?: return
        val set = setProcessWorkingSetSize ?: return
        runCatching {
            val proc = get.invokeWithArguments() as MemorySegment
            set.invokeWithArguments(proc, -1L, -1L)
            Napier.d("[mem] working-set trimmed")
        }.onFailure { Napier.w("[mem] working-set trim failed: ${it.message}") }
    }
}
