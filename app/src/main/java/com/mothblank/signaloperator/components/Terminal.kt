package com.mothblank.signaloperator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mothblank.signaloperator.engine.EvidenceCorrelation
import com.mothblank.signaloperator.engine.EvidenceDisposition
import com.mothblank.signaloperator.engine.EvidenceLink
import com.mothblank.signaloperator.engine.EvidenceRelation
import com.mothblank.signaloperator.engine.InterceptEvidence
import com.mothblank.signaloperator.models.LogEntry
import com.mothblank.signaloperator.models.LogType
import androidx.compose.ui.tooling.preview.Preview
import java.util.Locale
import java.util.UUID

private enum class TerminalMode { RECENT, ARCHIVE, EVIDENCE }
private enum class EvidenceFilter { ALL, FLAGGED, TRUSTED, CONFLICTS }

@Composable
fun Terminal(
    logs: List<LogEntry>,
    color: Color,
    onLogClick: (LogEntry) -> Unit,
    modifier: Modifier = Modifier,
    importantLogs: List<LogEntry> = emptyList(),
    evidence: List<InterceptEvidence> = emptyList(),
    onEvidenceDisposition: (String, EvidenceDisposition) -> Unit = { _, _ -> }
) {
    var mode by remember { mutableStateOf(TerminalMode.RECENT) }
    var evidenceFilter by remember { mutableStateOf(EvidenceFilter.ALL) }
    val retainedEvidence = remember(evidence) { EvidenceCorrelation.retain(evidence) }
    val evidenceLinks = remember(retainedEvidence) {
        retainedEvidence.flatMapIndexed { index, item ->
            EvidenceCorrelation.assess(retainedEvidence.take(index), item).links
        }.distinct()
    }
    val linksByEvidence = remember(evidenceLinks) {
        buildMap<String, MutableList<EvidenceLink>> {
            evidenceLinks.forEach { link ->
                getOrPut(link.earlierId) { mutableListOf() }.add(link)
                getOrPut(link.laterId) { mutableListOf() }.add(link)
            }
        }
    }

    val evidenceHasConflict: (InterceptEvidence) -> Boolean = { item ->
        linksByEvidence[item.id].orEmpty().any {
            it.relation == EvidenceRelation.CONTRADICTION ||
                it.relation == EvidenceRelation.CARRIER_MISMATCH ||
                it.relation == EvidenceRelation.CARRIER_REUSE
        }
    }
    val filteredEvidence = retainedEvidence.filter { item ->
        when (evidenceFilter) {
            EvidenceFilter.ALL -> true
            EvidenceFilter.FLAGGED ->
                item.operatorDisposition == EvidenceDisposition.SUSPICIOUS
            EvidenceFilter.TRUSTED ->
                item.operatorDisposition == EvidenceDisposition.TRUSTED
            EvidenceFilter.CONFLICTS -> evidenceHasConflict(item)
        }
    }

    val visibleLogs = when (mode) {
        TerminalMode.RECENT -> logs
        TerminalMode.ARCHIVE -> importantLogs
        TerminalMode.EVIDENCE -> emptyList()
    }

    Column(
        modifier = modifier.fillMaxWidth().border(1.dp, color).padding(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TerminalTab("RECENT [${logs.size}]", mode == TerminalMode.RECENT, color) {
                mode = TerminalMode.RECENT
            }
            TerminalTab("ARCHIVE [${importantLogs.size}]", mode == TerminalMode.ARCHIVE, color) {
                mode = TerminalMode.ARCHIVE
            }
            TerminalTab("EVIDENCE [${retainedEvidence.size}]", mode == TerminalMode.EVIDENCE, color) {
                mode = TerminalMode.EVIDENCE
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (mode == TerminalMode.EVIDENCE) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                EvidenceFilterTab(
                    text = "ALL ${retainedEvidence.size}",
                    selected = evidenceFilter == EvidenceFilter.ALL,
                    color = color
                ) { evidenceFilter = EvidenceFilter.ALL }
                EvidenceFilterTab(
                    text = "FLAG ${retainedEvidence.count { it.operatorDisposition == EvidenceDisposition.SUSPICIOUS }}",
                    selected = evidenceFilter == EvidenceFilter.FLAGGED,
                    color = color
                ) { evidenceFilter = EvidenceFilter.FLAGGED }
                EvidenceFilterTab(
                    text = "TRUST ${retainedEvidence.count { it.operatorDisposition == EvidenceDisposition.TRUSTED }}",
                    selected = evidenceFilter == EvidenceFilter.TRUSTED,
                    color = color
                ) { evidenceFilter = EvidenceFilter.TRUSTED }
                EvidenceFilterTab(
                    text = "CONFLICT ${retainedEvidence.count(evidenceHasConflict)}",
                    selected = evidenceFilter == EvidenceFilter.CONFLICTS,
                    color = color
                ) { evidenceFilter = EvidenceFilter.CONFLICTS }
            }
            Spacer(modifier = Modifier.height(3.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (retainedEvidence.isEmpty()) {
                    item {
                        Text(
                            "[NO CORRELATED INTERCEPTS FILED]",
                            color = color.copy(alpha = 0.42f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else if (filteredEvidence.isEmpty()) {
                    item {
                        Text(
                            "[NO EVIDENCE MATCHES CURRENT FILTER]",
                            color = color.copy(alpha = 0.42f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    items(filteredEvidence.asReversed(), key = { it.id }) { item ->
                        EvidenceRow(
                            item = item,
                            links = linksByEvidence[item.id].orEmpty(),
                            color = color,
                            onDisposition = { disposition ->
                                onEvidenceDisposition(item.id, disposition)
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(reverseLayout = true, modifier = Modifier.weight(1f)) {
                if (visibleLogs.isEmpty()) {
                    item {
                        Text(
                            if (mode == TerminalMode.ARCHIVE) {
                                "[--:--:--] NO ARCHIVED INTELLIGENCE."
                            } else {
                                "[--:--:--] SESSION LOG EMPTY."
                            },
                            color = color.copy(alpha = 0.42f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    items(visibleLogs.reversed(), key = { it.id }) { log ->
                        TerminalRow(log = log, color = color, onClick = { onLogClick(log) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalTab(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) color else color.copy(alpha = 0.48f),
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun EvidenceFilterTab(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) color else color.copy(alpha = 0.42f),
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun EvidenceRow(
    item: InterceptEvidence,
    links: List<EvidenceLink>,
    color: Color,
    onDisposition: (EvidenceDisposition) -> Unit
) {
    val contradictions = links.count { it.relation == EvidenceRelation.CONTRADICTION }
    val repeatedCallsigns = links.count { it.relation == EvidenceRelation.REPEATED_CALLSIGN }
    val sharedFrequencies = links.count { it.relation == EvidenceRelation.SHARED_FREQUENCY }
    val matchingCarriers = links.count { it.relation == EvidenceRelation.MATCHING_CARRIER }
    val carrierWarnings = links.count {
        it.relation == EvidenceRelation.CARRIER_MISMATCH ||
            it.relation == EvidenceRelation.CARRIER_REUSE
    }
    val warning = contradictions > 0 || carrierWarnings > 0 || item.suspectedSpoof
    val rowColor = if (warning) Color.Red else color

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, rowColor.copy(alpha = 0.28f))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            "#${item.sequence} // ${item.callsign ?: "UNKNOWN SOURCE"} // " +
                String.format(Locale.US, "%.3f", item.frequencyMhz) + " MHz",
            color = rowColor,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "REL ${item.reliability.name} // SECTOR ${item.sectorId ?: "UNKNOWN"} // " +
                "CALLSIGN $repeatedCallsigns // FREQ $sharedFrequencies // RF MATCH $matchingCarriers // WARN $carrierWarnings // CONFLICTS $contradictions",
            color = rowColor.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace
        )
        Text(
            "RF FINGERPRINT > ${item.carrierFingerprint ?: "UNRECOVERED"}",
            color = rowColor.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace
        )
        if (item.suspectedSpoof) {
            Text(
                "SOURCE STATUS > POSSIBLE SPOOF / IDENTITY CONFLICT",
                color = Color.Red,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "OPERATOR: ${item.operatorDisposition.name}",
                color = when (item.operatorDisposition) {
                    EvidenceDisposition.TRUSTED -> color
                    EvidenceDisposition.SUSPICIOUS -> Color.Red
                    EvidenceDisposition.UNREVIEWED -> color.copy(alpha = 0.55f)
                },
                fontFamily = FontFamily.Monospace
            )
            Text(
                "[TRUST]",
                modifier = Modifier.clickable {
                    onDisposition(EvidenceDisposition.TRUSTED)
                },
                color = if (item.operatorDisposition == EvidenceDisposition.TRUSTED) {
                    color
                } else {
                    color.copy(alpha = 0.45f)
                },
                fontFamily = FontFamily.Monospace
            )
            Text(
                "[FLAG]",
                modifier = Modifier.clickable {
                    onDisposition(EvidenceDisposition.SUSPICIOUS)
                },
                color = if (item.operatorDisposition == EvidenceDisposition.SUSPICIOUS) {
                    Color.Red
                } else {
                    color.copy(alpha = 0.45f)
                },
                fontFamily = FontFamily.Monospace
            )
            Text(
                "[CLEAR]",
                modifier = Modifier.clickable {
                    onDisposition(EvidenceDisposition.UNREVIEWED)
                },
                color = color.copy(alpha = 0.45f),
                fontFamily = FontFamily.Monospace
            )
        }

        if (item.missingFields.isNotEmpty()) {
            Text(
                "MISSING > ${item.missingFields.sorted().joinToString(", ")}",
                color = Color.Red.copy(alpha = 0.82f),
                fontFamily = FontFamily.Monospace
            )
        }
        if (item.claims.isNotEmpty()) {
            Text(
                "CLAIMS > " + item.claims.entries
                    .sortedBy { it.key }
                    .joinToString(" // ") { (key, value) -> "${key.uppercase()}=$value" },
                color = rowColor.copy(alpha = 0.68f),
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            item.message,
            color = color.copy(alpha = 0.88f),
            fontFamily = FontFamily.Monospace
        )
        links.filter {
            it.relation == EvidenceRelation.CONTRADICTION ||
                it.relation == EvidenceRelation.CARRIER_MISMATCH ||
                it.relation == EvidenceRelation.CARRIER_REUSE
        }
            .take(3)
            .forEach { link ->
                Text(
                    "${link.relation.name.replace('_', ' ')} > ${link.detail}",
                    color = Color.Red,
                    fontFamily = FontFamily.Monospace
                )
            }
    }
}

@Composable
fun TerminalRow(
    log: LogEntry,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActive = isHovered || isPressed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) color.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        val rowColor = when (log.type) {
            LogType.ERROR -> Color.Red
            LogType.INTERCEPT -> color
            LogType.ACTION -> color.copy(alpha = 0.9f)
            LogType.SYSTEM -> color.copy(alpha = 0.62f)
        }
        Text(
            text = "[${log.timestamp}] ${log.text}",
            color = rowColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        if (isActive) {
            Text(
                text = "▮",
                color = color,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Preview
@Composable
fun TerminalPreview() {
    Terminal(
        logs = listOf(
            LogEntry(UUID.randomUUID().toString(), "12:00:00", "SYSTEM READY", LogType.SYSTEM),
            LogEntry(UUID.randomUUID().toString(), "12:00:05", "SIGNAL DETECTED", LogType.INTERCEPT)
        ),
        color = Color.Green,
        onLogClick = {}
    )
}
