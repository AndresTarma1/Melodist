package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel

open class ApplicationViewModel : ViewModel() {
    private var disposed = false

    fun dispose() {
        if (disposed) return
        disposed = true
        onCleared()
    }
}
