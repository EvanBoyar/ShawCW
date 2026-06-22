package com.shawcw

import android.app.Application
import com.shawcw.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Loads persisted settings into [AppState] at startup and writes any later
 * changes back. [AppState] stays the single in memory source of truth; this
 * class just bridges it to disk so the UI and service need not know about
 * storage.
 */
class ShawApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val store = SettingsStore(this)

        scope.launch {
            // Merge persisted values in without touching the live "listening"
            // flag. The disk read is async and can land after the user has
            // already tapped Start, so a wholesale replace would clobber it and
            // leave the UI idle while the service keeps running.
            val loaded = store.flow.first()
            AppState.updateSettings { current -> loaded.copy(listening = current.listening) }

            // Persist subsequent changes. Drop the current value so loading does
            // not immediately rewrite what we just read.
            AppState.settings
                .drop(1)
                .onEach { store.save(it) }
                .launchIn(scope)
        }
    }
}
