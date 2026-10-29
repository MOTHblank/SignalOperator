package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.LogEntry
import com.mothblank.signaloperator.models.LogType

/**
 * Two independently bounded histories. Routine tuning and feedback stay in the
 * recent terminal; meaningful discoveries and story beats survive in the archive.
 * Deliberately no Android dependency, so old saves can be compacted when loaded.
 */
object OperatorLogRetention {
    const val RECENT_LIMIT = 180
    const val IMPORTANT_LIMIT = 96

    data class Snapshot(
        val recent: List<LogEntry>,
        val important: List<LogEntry>
    )

    fun isImportant(entry: LogEntry): Boolean {
        val message = entry.text
        if (entry.type == LogType.INTERCEPT && !message.startsWith("LOCK ACQUIRED")) return true
        return listOf(
            "DECODED:", "TRANSMITTED:", "FIELD EFFECT:",
            "SECURITY BREACH:", "FIREWALL SYNC SUCCESSFUL",
            "INTEGRATION INITIALIZED", "PHYSICAL BOUNDARIES",
            "YOU ARE HOME", "CONNECTION TERMINATED",
            "NEURAL INTERFACE OFFLINE", "STATIC REMAINS",
            "CRITICAL CONTAINER LEAK", "FLESH CORRUPTION",
            "THE TERMINAL SEES YOU", "LOCAL BUFFER PURGED",
            "SIGNAL INDUCED COGNITIVE", "CRITICAL: DIRECT",
            "KERNEL TELEMETRY RECOVERED", "MISSION TRAFFIC",
            "REINFORCED ", "RECOVERY ROUTE OPENED",
            "TRANSMISSION SUCCESSFUL"
        ).any { message.startsWith(it) }
    }

    /** Also upgrades legacy saves whose whole log history was stored in one array. */
    fun restore(recent: List<LogEntry>, archived: List<LogEntry> = emptyList()): Snapshot {
        val important = (archived + recent.filter(::isImportant))
            .distinctBy { it.id }
            .takeLast(IMPORTANT_LIMIT)
        return Snapshot(recent.takeLast(RECENT_LIMIT), important)
    }

    fun append(snapshot: Snapshot, entry: LogEntry): Snapshot = Snapshot(
        recent = (snapshot.recent + entry).takeLast(RECENT_LIMIT),
        important = if (isImportant(entry)) {
            (snapshot.important + entry).takeLast(IMPORTANT_LIMIT)
        } else snapshot.important
    )
}
