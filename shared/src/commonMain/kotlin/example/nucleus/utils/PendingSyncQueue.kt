package example.nucleus.utils

import example.nucleus.data.account.AccountManager
import example.nucleus.data.repository.UserPreferencesRepository
import com.metrolist.innertube.YouTube
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

/**
 * Una acción remota de YouTube Music que falló al enviarse mientras estaba offline (o con el modo
 * offline activado) y debe reintentarse una vez que la conectividad vuelva **y** haya sesión YT.
 * El estado local (BD, UI) siempre se aplica inmediatamente sin importar la red — esto solo rastrea
 * el lado *remoto* de la acción.
 *
 * Para soportar un nuevo tipo de acción: agregar una variante aquí, una rama en [PendingSyncQueue.flush],
 * y encolarla donde actualmente el envío en vivo solo registra y descarta en caso de fallo.
 */
@Serializable
sealed class PendingAction {
    @Serializable
    data class LikeSong(val songId: String, val liked: Boolean) : PendingAction()

    @Serializable
    data class SubscribeArtist(val channelId: String, val subscribed: Boolean) : PendingAction()
}

/**
 * Cola persistida de [PendingAction]s que no pudieron enviarse a YouTube mientras estaba offline
 * o la API falló con sesión activa. Solo hace flush cuando hay **login + red**; sin sesión no se
 * reintentan (evita spam de likes/subs en cada ciclo de 30 s). Al iniciar sesión se intenta un flush.
 */
class PendingSyncQueue(private val preferencesRepository: UserPreferencesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            while (true) {
                if (canFlush() && preferencesRepository.pendingActions.first().isNotEmpty()) {
                    flush()
                }
                delay(30_000.milliseconds)
            }
        }
        // Al pasar a logueado, drenar la cola en cuanto haya red (no esperar al tick de 30 s).
        scope.launch {
            var wasLoggedIn = AccountManager.isLoggedIn
            AccountManager.loginState.collect { loggedIn ->
                if (loggedIn && !wasLoggedIn && NetworkMonitor.isOnline() &&
                    preferencesRepository.pendingActions.first().isNotEmpty()
                ) {
                    Napier.i("PendingSyncQueue: session available, flushing pending actions")
                    flush()
                }
                wasLoggedIn = loggedIn
            }
        }
    }

    private fun canFlush(): Boolean =
        AccountManager.isLoggedIn && !YouTube.cookie.isNullOrBlank()

    suspend fun enqueue(action: PendingAction) {
        // Sin sesión no tiene sentido encolar para YouTube remoto.
        if (!AccountManager.isLoggedIn) {
            Napier.d("PendingSyncQueue: skip enqueue (not logged in): $action")
            return
        }
        preferencesRepository.addPendingAction(action)
    }

    suspend fun flush() {
        if (!canFlush()) {
            Napier.d("PendingSyncQueue: skip flush (no YT session)")
            return
        }
        if (!NetworkMonitor.isOnline()) {
            Napier.d("PendingSyncQueue: skip flush (offline)")
            return
        }
        val pending = preferencesRepository.pendingActions.first()
        if (pending.isEmpty()) return

        for (action in pending) {
            if (!canFlush()) break
            val result = runCatching {
                when (action) {
                    is PendingAction.LikeSong -> YouTube.likeVideo(action.songId, action.liked).getOrThrow()
                    is PendingAction.SubscribeArtist ->
                        YouTube.subscribeChannel(action.channelId, action.subscribed).getOrThrow()
                }
            }
            if (result.isSuccess) {
                preferencesRepository.removePendingAction(action)
                Napier.d("PendingSyncQueue: flushed $action")
            } else {
                Napier.w("PendingSyncQueue: retry failed for $action: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
