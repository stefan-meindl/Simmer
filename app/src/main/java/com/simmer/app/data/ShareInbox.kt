package com.simmer.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text another app shared to Simmer, waiting for Add recipe to take it. MainActivity puts it here (and opens
 * Add recipe); the Add recipe screen takes it, whether it was already open or is created afterwards.
 */
@Singleton
class ShareInbox @Inject constructor() {
    private val mutablePending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = mutablePending

    fun offer(text: String) {
        mutablePending.value = text
    }

    /** Empties the inbox once [text] has been taken; a newer share that arrived meanwhile stays. */
    fun taken(text: String) {
        mutablePending.compareAndSet(text, null)
    }
}
