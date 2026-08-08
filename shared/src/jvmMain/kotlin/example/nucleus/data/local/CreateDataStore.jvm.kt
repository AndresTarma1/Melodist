package example.nucleus.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import example.nucleus.platform.AppPaths

fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = {
        val dir = File(AppPaths.roamingRoot, "data")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, dataStoreFileName)
        file.absolutePath
    }
)