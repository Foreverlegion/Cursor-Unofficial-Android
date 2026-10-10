package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Hidden and forgotten machines, last seen times, and the auto-hide setting. Kept in the versioned
 * DataStore, so it survives restarts and updates. Data that will not parse is copied aside before
 * anything can overwrite it.
 */
class MachineStore(
    private val ui: UiPrefsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun prefs(): MachinePrefs = synchronized(lock) { read() }

    fun now(): Long = clock()

    fun observe(computers: List<Computer>, agents: List<AgentSummary>) {
        if (computers.isEmpty()) return
        update { observeMachines(it, computers, agents, clock()) }
    }

    fun hide(key: String, name: String) = update { it.withMark(key, name, MachineMarkState.HIDDEN, clock()) }

    fun forget(key: String, name: String) = update { it.withMark(key, name, MachineMarkState.FORGOTTEN, clock()) }

    fun show(key: String, name: String) = update { it.withMark(key, name, MachineMarkState.SHOWN, clock()) }

    fun setAutoHideDays(days: Int) = update { it.withAutoHideDays(days) }

    fun visible(computers: List<Computer>): List<Computer> = visibleMachines(computers, prefs(), clock())

    fun replace(next: MachinePrefs) = synchronized(lock) { write(next) }

    private fun update(change: (MachinePrefs) -> MachinePrefs) = synchronized(lock) {
        val before = read()
        val after = change(before)
        if (after != before) write(after)
    }

    private fun read(): MachinePrefs {
        val raw = ui[UiKeys.machinePrefs] ?: return MachinePrefs()
        return runCatching { json.decodeFromString<MachinePrefs>(raw) }.getOrElse {
            if (ui[UiKeys.machinePrefsUnreadable] == null) ui.put(UiKeys.machinePrefsUnreadable, raw)
            MachinePrefs()
        }
    }

    private fun write(next: MachinePrefs) {
        ui.put(UiKeys.machinePrefs, json.encodeToString(next))
    }
}
