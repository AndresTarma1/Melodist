package com.example.melodist.player

import com.sun.jna.CallbackReference
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.WString
// Alias explícito para evitar conflicto con kotlin.Function
import com.sun.jna.Function as JnaFunction
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Integración con Windows System Media Transport Controls (SMTC).
 *
 * Permite que Windows reconozca Melodist como reproductor de medios y muestre:
 * - Nombre de la canción, artista y álbum en el overlay de volumen (Win10/11)
 * - Controles de reproducción en la pantalla de bloqueo
 * - Información en el Task View y Timeline
 */
class WindowsMediaSession {

    private val log = Logger.getLogger("WindowsMediaSession")

    // ── WinRT / combase.dll ────────────────────────────────────────────────────

    interface CombaseLib : Library {
        fun RoInitialize(initType: Int): Int
        fun RoGetActivationFactory(
            activatableClassId: Pointer,
            iid: ByteArray,
            factory: PointerByReference
        ): Int
        fun WindowsCreateString(sourceString: WString, length: Int, hstring: PointerByReference): Int
        fun WindowsDeleteString(hstring: Pointer): Int
    }

    interface Shell32Lib : Library {
        fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int
    }

    private val combase: CombaseLib? by lazy {
        try { Native.load("combase", CombaseLib::class.java) }
        catch (e: Exception) { log.warning("SMTC: combase.dll no disponible: ${e.message}"); null }
    }

    private val shell32: Shell32Lib? by lazy {
        try { Native.load("shell32", Shell32Lib::class.java) }
        catch (_: Exception) { null }
    }

    // ── Interfaces JNA Callback para el vtable COM ─────────────────────────────
    // Se usa StdCallLibrary.StdCallCallback para que JNA use la convención correcta
    // en Windows (stdcall / Microsoft x64). En x64 cdecl = stdcall, pero JNA necesita
    // la anotación correcta para el stub de callback nativo.

    interface ComQI : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, riid: Pointer, ppv: PointerByReference): Int
    }
    interface ComAddRef : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }
    interface ComRelease : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }
    interface ComGetIids : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, iidCount: IntByReference, iids: PointerByReference): Int
    }
    interface ComGetRuntimeClassName : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, className: PointerByReference): Int
    }
    interface ComGetTrustLevel : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, trustLevel: IntByReference): Int
    }
    interface HandlerInvoke : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, sender: Pointer, args: Pointer): Int
    }

    // ── Estado interno ─────────────────────────────────────────────────────────

    private var smtcPtr: Pointer? = null
    private var displayUpdaterPtr: Pointer? = null
    private var musicPropsPtr: Pointer? = null
    private var eventTokenMemory: Memory? = null

    // Referencias a callbacks (evitar recolección de basura):
    private var cbQI: ComQI? = null
    private var cbAddRef: ComAddRef? = null
    private var cbRelease: ComRelease? = null
    private var cbGetIids: ComGetIids? = null
    private var cbGetRuntimeClassName: ComGetRuntimeClassName? = null
    private var cbGetTrustLevel: ComGetTrustLevel? = null
    private var cbInvoke: HandlerInvoke? = null
    private var handlerVtable: Memory? = null
    private var handlerObj: Memory? = null

    /**
     * Cola de botones pulsados desde el overlay. El callback JNA solo encola un Int.
     * Un hilo separado lo procesa y llama a los callbacks del reproductor.
     * Esto evita llamar JNA desde dentro de un callback JNA (unsafe en x64).
     */
    private val buttonQueue = java.util.concurrent.LinkedBlockingQueue<Int>()
    private val buttonThread = Thread({
        try {
            while (!Thread.currentThread().isInterrupted) {
                val btn = buttonQueue.take()
                when (btn) {
                    0 -> onPlay?.invoke()
                    1 -> onPause?.invoke()
                    2 -> onStop?.invoke()
                    4 -> onNext?.invoke()       // FastForward → siguiente
                    5 -> onPrevious?.invoke()   // Rewind → anterior
                    6 -> onNext?.invoke()       // Next
                    7 -> onPrevious?.invoke()   // Previous
                }
            }
        } catch (_: InterruptedException) { }
    }, "smtc-button-dispatcher").also {
        it.isDaemon = true
        it.start()
    }

    // ── Acciones del reproductor ───────────────────────────────────────────────

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null

    private val S_OK = 0
    private val E_NOTIMPL = -2147467263 // 0x80004001

    fun setCallbacks(
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onStop: () -> Unit
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onStop = onStop
    }

    // ── GUIDs de las interfaces WinRT ──────────────────────────────────────────

    private val IID_SMTC_INTEROP = winrtGuid("ddb0472d", "c911", "4a1f", "86d9dc3d71a95f5a")
    private val IID_SMTC = winrtGuid("99fa3ff4", "1742", "42a6", "902e087d41f965ec")
    private val IID_URI_FACTORY = winrtGuid("44a9796f", "723e", "4fdf", "a218033e75b0c084")
    private val IID_STREAM_REF_STATICS = winrtGuid("587914c4", "2e7f", "4e8b", "a7fb9bee9e0dce03")

    private fun winrtGuid(d1: String, d2: String, d3: String, d4: String): ByteArray {
        fun intLE(hex: String): ByteArray {
            val v = hex.toLong(16).toInt()
            return byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
        }
        fun shortLE(hex: String): ByteArray {
            val v = hex.toInt(16)
            return byteArrayOf(v.toByte(), (v ushr 8).toByte())
        }
        val d4bytes = d4.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return intLE(d1) + shortLE(d2) + shortLE(d3) + d4bytes
    }

    // ── Helpers COM ────────────────────────────────────────────────────────────

    private fun createHString(s: String): Pointer? {
        val cb = combase ?: return null
        val ref = PointerByReference()
        return if (cb.WindowsCreateString(WString(s), s.length, ref) == 0) ref.value else null
    }

    private fun deleteHString(h: Pointer?) { h?.let { combase?.WindowsDeleteString(it) } }

    private fun comHR(iface: Pointer, idx: Int, vararg args: Any?): Int = try {
        val vtable = iface.getPointer(0) ?: return -1
        val methodPtr = vtable.getPointer(idx.toLong() * Native.POINTER_SIZE) ?: return -1
        val fn: JnaFunction = JnaFunction.getFunction(methodPtr, 0)
        val result: Any? = fn.invoke(java.lang.Integer::class.java, arrayOf(iface, *args))
        (result as? Int) ?: -1
    } catch (e: Throwable) {
        log.fine("comHR[$idx]: ${e.message}")
        -1
    }

    private fun comGetPtr(iface: Pointer, idx: Int, vararg extra: Any?): Pointer? {
        val ref = PointerByReference()
        return if (comHR(iface, idx, *extra, ref) == 0) ref.value else null
    }

    // ── Inicialización ─────────────────────────────────────────────────────────

    fun initialize(hwnd: Long) {
        if (!Platform.isWindows()) {
            log.info("SMTC: solo disponible en Windows, saltando.")
            return
        }
        val cb = combase ?: return

        try {
            val hr = shell32?.SetCurrentProcessExplicitAppUserModelID(WString("Melodist.MusicPlayer")) ?: -1
            if (hr == 0) log.info("AppUserModelID → Melodist.MusicPlayer")
        } catch (e: Throwable) {
            log.warning("No se pudo establecer AppUserModelID: ${e.message}")
        }

        try {
            cb.RoInitialize(1)

            val hstrClass = createHString("Windows.Media.SystemMediaTransportControls") ?: return
            val factoryRef = PointerByReference()
            val hr1 = cb.RoGetActivationFactory(hstrClass, IID_SMTC_INTEROP, factoryRef)
            deleteHString(hstrClass)

            if (hr1 != 0) return
            val factory = factoryRef.value ?: return

            val smtcRef = PointerByReference()
            val hr2 = comHR(factory, 6, Pointer(hwnd), IID_SMTC, smtcRef)
            comHR(factory, 2)

            if (hr2 != 0) return
            val smtc = smtcRef.value ?: return
            smtcPtr = smtc

            // ── Habilitar controles en el overlay ────────────────────────────
            // ISystemMediaTransportControls vtable (índices desde 6):
            //  7: put_PlaybackStatus,  8: get_DisplayUpdater
            // 11: put_IsEnabled,      13: put_IsPlayEnabled
            // 15: put_IsStopEnabled,  17: put_IsPauseEnabled
            // 21: put_IsFastForwardEnabled, 23: put_IsRewindEnabled
            // 25: put_IsNextEnabled,  27: put_IsPreviousEnabled
            // 32: add_ButtonPressed
            comHR(smtc, 11, 1.toByte())  // put_IsEnabled
            comHR(smtc, 13, 1.toByte())  // put_IsPlayEnabled
            comHR(smtc, 15, 1.toByte())  // put_IsStopEnabled
            comHR(smtc, 17, 1.toByte())  // put_IsPauseEnabled
            comHR(smtc, 21, 1.toByte())  // put_IsFastForwardEnabled
            comHR(smtc, 23, 1.toByte())  // put_IsRewindEnabled
            comHR(smtc, 25, 1.toByte())  // put_IsNextEnabled
            comHR(smtc, 27, 1.toByte())  // put_IsPreviousEnabled

            comHR(smtc, 7, 2) // put_PlaybackStatus = Stopped (2)

            buildAndRegisterButtonHandler(smtc)

            displayUpdaterPtr = comGetPtr(smtc, 8)?.also { updater ->
                comHR(updater, 7, 1) // MediaPlaybackType.Music (1)
                musicPropsPtr = comGetPtr(updater, 12) // get_MusicProperties
            }

            resetToIdle()

            log.info("SMTC inicializado correctamente para hwnd=0x${hwnd.toString(16)}")

        } catch (e: Throwable) {
            log.log(Level.WARNING, "Error inicializando SMTC", e)
        }
    }

    private fun buildAndRegisterButtonHandler(smtc: Pointer) {
        cbQI = object : ComQI {
            override fun invoke(self: Pointer, riid: Pointer, ppv: PointerByReference): Int {
                ppv.value = self
                return S_OK
            }
        }
        cbAddRef = object : ComAddRef { override fun invoke(self: Pointer): Int = 1 }
        cbRelease = object : ComRelease { override fun invoke(self: Pointer): Int = 1 }
        cbGetIids = object : ComGetIids {
            override fun invoke(self: Pointer, iidCount: IntByReference, iids: PointerByReference): Int {
                iidCount.value = 0
                iids.value = Pointer.NULL
                return S_OK
            }
        }
        cbGetRuntimeClassName = object : ComGetRuntimeClassName {
            override fun invoke(self: Pointer, className: PointerByReference): Int {
                className.value = Pointer.NULL
                return E_NOTIMPL
            }
        }
        cbGetTrustLevel = object : ComGetTrustLevel {
            override fun invoke(self: Pointer, trustLevel: IntByReference): Int {
                trustLevel.value = 0
                return S_OK
            }
        }
        cbInvoke = object : HandlerInvoke {
            override fun invoke(self: Pointer, sender: Pointer, args: Pointer): Int {
                log.info("SMTC: cbInvoke disparado desde Windows")
                try {
                    val vtable    = args.getPointer(0) ?: return 0
                    val getButPtr = vtable.getPointer(6L * Native.POINTER_SIZE) ?: return 0
                    val fn: JnaFunction = JnaFunction.getFunction(getButPtr, 0)
                    val btnOut = IntByReference()
                    fn.invoke(java.lang.Integer::class.java, arrayOf(args, btnOut))
                    log.info("SMTC: botón pulsado = ${btnOut.value}")
                    buttonQueue.offer(btnOut.value)
                } catch (e: Throwable) {
                    log.warning("SMTC: error en cbInvoke: ${e.message}")
                }
                return S_OK
            }
        }

        // WinRT delegates usan IInspectable ABI: QI, AddRef, Release,
        // GetIids, GetRuntimeClassName, GetTrustLevel, Invoke.
        handlerVtable = Memory(7L * Native.POINTER_SIZE).also { vtbl ->
            vtbl.setPointer(0L, CallbackReference.getFunctionPointer(cbQI!!))
            vtbl.setPointer(1L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cbAddRef!!))
            vtbl.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cbRelease!!))
            vtbl.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cbGetIids!!))
            vtbl.setPointer(4L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cbGetRuntimeClassName!!))
            vtbl.setPointer(5L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cbGetTrustLevel!!))
            vtbl.setPointer(6L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cbInvoke!!))
        }

        handlerObj = Memory(Native.POINTER_SIZE.toLong()).also { it.setPointer(0L, handlerVtable!!) }

        eventTokenMemory = Memory(8L)
        val hrBtn = comHR(smtc, 32, handlerObj!!, eventTokenMemory!!)
        if (hrBtn == 0) {
            log.info("SMTC: add_ButtonPressed registrado correctamente (token=${eventTokenMemory!!.getLong(0)})")
        } else {
            log.warning("SMTC: add_ButtonPressed FALLÓ hr=0x${Integer.toUnsignedString(hrBtn, 16)}")
        }
    }

    // ── Actualización de metadatos ─────────────────────────────────────────────

    private fun createStreamRefFromUrl(url: String): Pointer? {
        val cb = combase ?: return null
        return try {
            val hstrUriClass = createHString("Windows.Foundation.Uri") ?: return null
            val uriFactoryRef = PointerByReference()
            val hr1 = cb.RoGetActivationFactory(hstrUriClass, IID_URI_FACTORY, uriFactoryRef)
            deleteHString(hstrUriClass)
            if (hr1 != 0) return null
            val uriFactory = uriFactoryRef.value ?: return null

            val hstrUrl = createHString(url) ?: run { comHR(uriFactory, 2); return null }
            val uriRef = PointerByReference()
            val hr2 = comHR(uriFactory, 6, hstrUrl, uriRef)
            deleteHString(hstrUrl)
            comHR(uriFactory, 2)
            if (hr2 != 0) return null
            val uri = uriRef.value ?: return null

            val hstrStreamClass = createHString("Windows.Storage.Streams.RandomAccessStreamReference")
                ?: run { comHR(uri, 2); return null }
            val staticsRef = PointerByReference()
            val hr3 = cb.RoGetActivationFactory(hstrStreamClass, IID_STREAM_REF_STATICS, staticsRef)
            deleteHString(hstrStreamClass)
            if (hr3 != 0) { comHR(uri, 2); return null }
            val statics = staticsRef.value ?: run { comHR(uri, 2); return null }

            val streamRefOut = PointerByReference()
            val hr4 = comHR(statics, 7, uri, streamRefOut)
            comHR(statics, 2)
            comHR(uri, 2)
            if (hr4 != 0) return null

            streamRefOut.value
        } catch (e: Exception) {
            log.fine("createStreamRefFromUrl: ${e.message}")
            null
        }
    }

    fun updateMetadata(title: String, artist: String, album: String, thumbnailUrl: String? = null) {
        val updater = displayUpdaterPtr ?: return
        val musicProps = musicPropsPtr ?: return
        try {
            val hTitle  = createHString(title.ifBlank { "Melodist" })
            val hAlbum  = createHString(album)
            val hArtist = createHString(artist.ifBlank { "Artista desconocido" })

            hTitle?.let  { comHR(musicProps, 7,  it); deleteHString(it) }
            hAlbum?.let  { comHR(musicProps, 9,  it); deleteHString(it) }
            hArtist?.let { comHR(musicProps, 11, it); deleteHString(it) }

            if (!thumbnailUrl.isNullOrBlank()) {
                val streamRef = createStreamRefFromUrl(thumbnailUrl)
                if (streamRef != null) {
                    comHR(updater, 11, streamRef)
                    comHR(streamRef, 2)
                }
            }

            comHR(updater, 17) // Update()
        } catch (e: Exception) {
            log.log(Level.WARNING, "Error actualizando metadata SMTC", e)
        }
    }

    fun setPlaybackStatus(isPlaying: Boolean, isPaused: Boolean) {
        val smtc = smtcPtr ?: return
        val status = when {
            isPlaying -> 3  // Playing
            isPaused  -> 4  // Paused
            else      -> 2  // Stopped
        }
        comHR(smtc, 7, status)
    }

    fun resetToIdle() {
        updateMetadata(
            title = "Melodist",
            artist = "",
            album = "",
            thumbnailUrl = null,
        )
        setPlaybackStatus(isPlaying = false, isPaused = false)
    }

    // ── Limpieza ───────────────────────────────────────────────────────────────

    fun release() {
        buttonThread.interrupt()
        try {
            musicPropsPtr?.let     { comHR(it, 2) }
            displayUpdaterPtr?.let { comHR(it, 2) }
            smtcPtr?.let           { comHR(it, 2) }
        } catch (_: Throwable) { }
        smtcPtr = null
        displayUpdaterPtr = null
        musicPropsPtr = null
        eventTokenMemory = null
        handlerObj = null
        handlerVtable = null
        log.info("SMTC liberado")
    }
}