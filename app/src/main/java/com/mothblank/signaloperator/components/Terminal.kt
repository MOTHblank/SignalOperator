package com.mothblank.signaloperator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlin.math.max

private enum class TerminalMode { RECENT, ARCHIVE, EVIDENCE }
private enum class EvidenceFilter { ALL, FLAGGED, TRUSTED, CONFLICTS }
private enum class EvidenceShelf { ACTIVE, HISTORY }

private fun EvidenceRelation.isDispute(): Boolean = when (this) {
    EvidenceRelation.CONTRADICTION, EvidenceRelation.UNRESOLVED_CLAIM,
    EvidenceRelation.CARRIER_MISMATCH, EvidenceRelation.CARRIER_REUSE -> true
    else -> false
}

private fun EvidenceRelation.isWarning(): Boolean = when (this) {
    EvidenceRelation.CONTRADICTION, EvidenceRelation.CARRIER_MISMATCH,
    EvidenceRelation.CARRIER_REUSE -> true
    else -> false
}

private fun evidenceMatches(item: InterceptEvidence, search: String): Boolean {
    val query = search.trim()
    if (query.isEmpty()) return true
    return listOfNotNull(
        item.id, item.sequence.toString(), item.callsign, item.sectorId,
        item.message, item.carrierFingerprint,
        String.format(Locale.US, "%.3f", item.frequencyMhz)
    ).any { it.contains(query, ignoreCase = true) } ||
        item.claims.any { (key, value) ->
            key.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)
        }
}

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
    var shelf by remember { mutableStateOf(EvidenceShelf.ACTIVE) }
    var search by remember { mutableStateOf("") }
    var comparisonAnchor by remember { mutableStateOf<String?>(null) }
    var comparison by remember { mutableStateOf<Pair<String, String>?>(null) }

    val allEvidence = remember(evidence) { EvidenceCorrelation.retain(evidence) }
    val activeEvidence = remember(allEvidence) { EvidenceCorrelation.activeWindow(allEvidence) }
    val archivedEvidence = remember(allEvidence) { EvidenceCorrelation.archive(allEvidence) }
    val byId = remember(allEvidence) { allEvidence.associateBy { it.id } }

    // Resolve the 256-record neighborhood of a selected intercept on demand.
    // Do not allocate a quadratic global link graph for long-running histories.
    val indexById = remember(allEvidence) {
        allEvidence.mapIndexed { index, item -> item.id to index }.toMap()
    }
    val linkCache = remember(allEvidence) { mutableMapOf<String, List<EvidenceLink>>() }
    fun linksFor(item: InterceptEvidence): List<EvidenceLink> =
        linkCache.getOrPut(item.id) {
            val index = indexById[item.id] ?: return@getOrPut emptyList()
            val earlier = EvidenceCorrelation.assess(
                allEvidence.subList(max(0, index - EvidenceCorrelation.MAX_INTERCEPTS), index),
                item
            ).links
            val later = allEvidence.subList(
                index + 1,
                minOf(allEvidence.size, index + 1 + EvidenceCorrelation.MAX_INTERCEPTS)
            ).flatMap { laterItem ->
                EvidenceCorrelation.assess(listOf(item), laterItem).links
            }
            (earlier + later).filter {
                it.relation.isDispute() || it.relation == EvidenceRelation.HISTORICAL_UPDATE
            }.distinct()
        }
    val evidenceHasConflict: (InterceptEvidence) -> Boolean = { item ->
        linksFor(item).any { it.relation.isDispute() }
    }
    val shelfEvidence = if (shelf == EvidenceShelf.ACTIVE) activeEvidence else archivedEvidence
    val filteredEvidence = shelfEvidence.filter { item ->
        evidenceMatches(item, search) && when (evidenceFilter) {
            EvidenceFilter.ALL -> true
            EvidenceFilter.FLAGGED -> item.operatorDisposition == EvidenceDisposition.SUSPICIOUS
            EvidenceFilter.TRUSTED -> item.operatorDisposition == EvidenceDisposition.TRUSTED
            EvidenceFilter.CONFLICTS -> evidenceHasConflict(item)
        }
    }
    val visibleLogs = when (mode) {
        TerminalMode.RECENT -> logs
        TerminalMode.ARCHIVE -> importantLogs
        TerminalMode.EVIDENCE -> emptyList()
    }

    fun openComparison(firstId: String, secondId: String) {
        if (firstId != secondId && byId.containsKey(firstId) && byId.containsKey(secondId)) {
            comparison = firstId to secondId
            comparisonAnchor = null
        }
    }

    Column(modifier = modifier.fillMaxWidth().border(1.dp, color).padding(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            TerminalTab("RECENT [${logs.size}]", mode == TerminalMode.RECENT, color) {
                mode = TerminalMode.RECENT
            }
            TerminalTab("ARCHIVE [${importantLogs.size}]", mode == TerminalMode.ARCHIVE, color) {
                mode = TerminalMode.ARCHIVE
            }
            TerminalTab("EVIDENCE [${allEvidence.size}]", mode == TerminalMode.EVIDENCE, color) {
                mode = TerminalMode.EVIDENCE
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (mode == TerminalMode.EVIDENCE) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                EvidenceFilterTab(
                    "LIVE ${activeEvidence.size}", shelf == EvidenceShelf.ACTIVE, color
                ) { shelf = EvidenceShelf.ACTIVE }
                EvidenceFilterTab(
                    "HISTORY ${archivedEvidence.size}", shelf == EvidenceShelf.HISTORY, color
                ) { shelf = EvidenceShelf.HISTORY }
                if (comparisonAnchor != null) {
                    EvidenceFilterTab("CANCEL PAIR", false, color) { comparisonAnchor = null }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                EvidenceFilter.values().forEach { filter ->
                    val count = shelfEvidence.count { item ->
                        when (filter) {
                            EvidenceFilter.ALL -> true
                            EvidenceFilter.FLAGGED ->
                                item.operatorDisposition == EvidenceDisposition.SUSPICIOUS
                            EvidenceFilter.TRUSTED ->
                                item.operatorDisposition == EvidenceDisposition.TRUSTED
                            EvidenceFilter.CONFLICTS -> evidenceHasConflict(item)
                        }
                    }
                    EvidenceFilterTab(
                        "${filter.name} $count", evidenceFilter == filter, color
                    ) { evidenceFilter = filter }
                }
            }
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = TextStyle(color = color, fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, color.copy(alpha = 0.45f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                decorationBox = { input ->
                    Box {
                        if (search.isEmpty()) {
                            Text(
                                "SEARCH CALLSIGN / FREQUENCY / SECTOR / MESSAGE",
                                color = color.copy(alpha = 0.43f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        input()
                    }
                }
            )
            if (shelf == EvidenceShelf.HISTORY) {
                Text(
                    "ARCHIVE // ${archivedEvidence.size} PRESERVED OUTSIDE ${EvidenceCorrelation.MAX_INTERCEPTS}-INTERCEPT LIVE WINDOW",
                    color = color.copy(alpha = 0.62f),
                    fontFamily = FontFamily.Monospace
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (allEvidence.isEmpty()) {
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
                            "[NO EVIDENCE MATCHES CURRENT SEARCH, FILTER OR HISTORY WINDOW]",
                            color = color.copy(alpha = 0.42f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    items(filteredEvidence.asReversed(), key = { it.id }) { item ->
                        EvidenceRow(
                            item = item,
                            links = linksFor(item),
                            evidenceById = byId,
                            color = color,
                            comparisonAnchor = comparisonAnchor,
                            onPick = {
                                val anchor = comparisonAnchor
                                when {
                                    anchor == null -> comparisonAnchor = item.id
                                    anchor == item.id -> comparisonAnchor = null
                                    else -> openComparison(anchor, item.id)
                                }
                            },
                            onOpenRelated = { other -> openComparison(item.id, other) },
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

    val selected = comparison
    if (selected != null) {
        val left = byId[selected.first]
        val right = byId[selected.second]
        if (left != null && right != null) {
            EvidenceComparisonDialog(
                left = left,
                right = right,
                color = color,
                byId = byId,
                linksByEvidence = mapOf(left.id to linksFor(left), right.id to linksFor(right)),
                onDismiss = { comparison = null },
                onPairChanged = { first, second -> openComparison(first, second) }
            )
        }
    }
}

@Composable
private fun TerminalTab(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
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
    evidenceById: Map<String, InterceptEvidence>,
    color: Color,
    comparisonAnchor: String?,
    onPick: () -> Unit,
    onOpenRelated: (String) -> Unit,
    onDisposition: (EvidenceDisposition) -> Unit
) {
    val contradictions = links.count { it.relation == EvidenceRelation.CONTRADICTION }
    val unresolved = links.count { it.relation == EvidenceRelation.UNRESOLVED_CLAIM }
    val updates = links.count { it.relation == EvidenceRelation.HISTORICAL_UPDATE }
    val carrierWarnings = links.count { it.relation == EvidenceRelation.CARRIER_MISMATCH ||
        it.relation == EvidenceRelation.CARRIER_REUSE }
    val warnings = contradictions + carrierWarnings
    val rowColor = if (warnings > 0 || item.suspectedDeception) Color.Red else color

    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, rowColor.copy(alpha = 0.28f))
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Text(
            "#${item.sequence} // ${item.callsign ?: "UNKNOWN SOURCE"} // " +
                String.format(Locale.US, "%.3f", item.frequencyMhz) + " MHz",
            color = rowColor,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "REL ${item.reliability.name} // SECTOR ${item.sectorId ?: "UNKNOWN"} // " +
                "RF WARN $carrierWarnings // CONTRADICTIONS $contradictions // UNRESOLVED $unresolved // UPDATES $updates",
            color = rowColor.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace
        )
        Text(
            "RF > ${item.carrierFingerprint ?: "UNRECOVERED"}",
            color = color.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace
        )
        if (item.suspectedDeception) {
            Text(
                "SOURCE STATUS > POSSIBLE DECEPTION; VERIFY INDEPENDENTLY",
                color = Color.Red,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "OPERATOR: ${item.operatorDisposition.name}",
                color = when (item.operatorDisposition) {
                    EvidenceDisposition.TRUSTED -> color
                    EvidenceDisposition.SUSPICIOUS -> Color.Red
                    EvidenceDisposition.UNREVIEWED -> color.copy(alpha = 0.55f)
                },
                fontFamily = FontFamily.Monospace
            )
            EvidenceFilterTab("[TRUST]", item.operatorDisposition == EvidenceDisposition.TRUSTED, color) {
                onDisposition(EvidenceDisposition.TRUSTED)
            }
            EvidenceFilterTab("[FLAG]", item.operatorDisposition == EvidenceDisposition.SUSPICIOUS, color) {
                onDisposition(EvidenceDisposition.SUSPICIOUS)
            }
            EvidenceFilterTab("[CLEAR]", false, color) {
                onDisposition(EvidenceDisposition.UNREVIEWED)
            }
            EvidenceFilterTab(
                when (comparisonAnchor) {
                    null -> "[PICK TO COMPARE]"
                    item.id -> "[CANCEL PAIR]"
                    else -> "[COMPARE WITH SELECTED]"
                },
                comparisonAnchor == item.id,
                color,
                onPick
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
                "CLAIMS > " + item.claims.entries.sortedBy { it.key }
                    .joinToString(" // ") { (key, value) -> "${key.uppercase()}=$value" },
                color = color.copy(alpha = 0.68f),
                fontFamily = FontFamily.Monospace
            )
        }
        Text(item.message, color = color.copy(alpha = 0.88f), fontFamily = FontFamily.Monospace)
        links.filter { it.relation.isDispute() || it.relation == EvidenceRelation.HISTORICAL_UPDATE }
            .take(4)
            .forEach { link ->
                val otherId = if (link.earlierId == item.id) link.laterId else link.earlierId
                val other = evidenceById[otherId]
                if (other != null) {
                    Text(
                        "[OPEN #${other.sequence}] ${link.relation.name.replace('_', ' ')} > ${link.detail}",
                        modifier = Modifier.clickable { onOpenRelated(otherId) },
                        color = if (link.relation.isWarning()) Color.Red
                            else if (link.relation == EvidenceRelation.UNRESOLVED_CLAIM) Color.Yellow
                            else color.copy(alpha = 0.82f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
    }
}

@Composable
private fun EvidenceComparisonDialog(
    left: InterceptEvidence,
    right: InterceptEvidence,
    color: Color,
    byId: Map<String, InterceptEvidence>,
    linksByEvidence: Map<String, List<EvidenceLink>>,
    onDismiss: () -> Unit,
    onPairChanged: (String, String) -> Unit
) {
    val earlier = if (left.sequence <= right.sequence) left else right
    val later = if (earlier.id == left.id) right else left
    val relations = remember(left, right) {
        EvidenceCorrelation.assess(listOf(earlier), later).links
    }
    val changed = left.claims.keys.intersect(right.claims.keys).filter { key ->
        !left.claims[key].equals(right.claims[key], ignoreCase = true)
    }.toSet()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.84f)
                .background(Color.Black).border(2.dp, color).padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("COMPARE INTERCEPT #${left.sequence} <> #${right.sequence}",
                    color = color, fontFamily = FontFamily.Monospace)
                Text("[CLOSE]", modifier = Modifier.clickable(onClick = onDismiss),
                    color = color, fontFamily = FontFamily.Monospace)
            }
            Text(
                "Highlighted claim differences are observations, not proof of deception.",
                color = color.copy(alpha = 0.65f), fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EvidenceComparisonPane(
                    item = left, color = color, changedKeys = changed,
                    modifier = Modifier.weight(1f),
                    related = linksByEvidence[left.id].orEmpty().mapNotNull { link ->
                        val other = if (link.earlierId == left.id) link.laterId else link.earlierId
                        byId[other]
                    }.distinctBy { it.id }.take(12),
                    onRelated = { otherId -> onPairChanged(otherId, right.id) }
                )
                EvidenceComparisonPane(
                    item = right, color = color, changedKeys = changed,
                    modifier = Modifier.weight(1f),
                    related = linksByEvidence[right.id].orEmpty().mapNotNull { link ->
                        val other = if (link.earlierId == right.id) link.laterId else link.earlierId
                        byId[other]
                    }.distinctBy { it.id }.take(12),
                    onRelated = { otherId -> onPairChanged(left.id, otherId) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("CORRELATION // ${relations.size} LINKS", color = color, fontFamily = FontFamily.Monospace)
            Column(modifier = Modifier.heightIn(max = 118.dp).verticalScroll(rememberScrollState())) {
                if (relations.isEmpty()) {
                    Text("NO AUTOMATIC LINK; MANUAL COMPARISON ONLY.",
                        color = color.copy(alpha = 0.66f), fontFamily = FontFamily.Monospace)
                }
                relations.filter { it.relation.isDispute() ||
                    it.relation == EvidenceRelation.HISTORICAL_UPDATE }.forEach { link ->
                    Text("${link.relation.name.replace('_', ' ')} > ${link.detail}",
                        color = if (link.relation.isWarning()) Color.Red
                            else if (link.relation == EvidenceRelation.UNRESOLVED_CLAIM) Color.Yellow
                            else color,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun EvidenceComparisonPane(
    item: InterceptEvidence,
    color: Color,
    changedKeys: Set<String>,
    modifier: Modifier,
    related: List<InterceptEvidence>,
    onRelated: (String) -> Unit
) {
    Column(modifier = modifier.fillMaxHeight()
        .border(1.dp, color.copy(alpha = 0.5f)).verticalScroll(rememberScrollState())
        .padding(8.dp)) {
        Text("#${item.sequence} // ${item.callsign ?: "UNKNOWN"}", color = color,
            fontFamily = FontFamily.Monospace)
        Text("SECTOR ${item.sectorId ?: "UNKNOWN"} // " +
            String.format(Locale.US, "%.3f", item.frequencyMhz) + " MHz",
            color = color, fontFamily = FontFamily.Monospace)
        Text("QUALITY ${item.reliability.name} // OPERATOR ${item.operatorDisposition.name}",
            color = color, fontFamily = FontFamily.Monospace)
        Text("RF > ${item.carrierFingerprint ?: "UNRECOVERED"}",
            color = color, fontFamily = FontFamily.Monospace)
        Text("RECOVERED MESSAGE:", color = color.copy(alpha = 0.65f),
            fontFamily = FontFamily.Monospace)
        Text(item.message, color = color, fontFamily = FontFamily.Monospace)
        if (item.missingFields.isNotEmpty()) {
            Text("MISSING: ${item.missingFields.sorted().joinToString(", ")}",
                color = Color.Yellow, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        item.claims.toSortedMap().forEach { (key, value) ->
            val observation = EvidenceCorrelation.observation(item, key)
            Text("$key = $value", color = if (key in changedKeys) Color.Yellow else color,
                fontFamily = FontFamily.Monospace)
            Text("${observation.category.name} / OBS #${observation.observedSequence} / " +
                "REPORTED BY ${observation.reportedBy ?: "UNKNOWN"}",
                color = color.copy(alpha = 0.59f), fontFamily = FontFamily.Monospace)
        }
        if (related.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("RELATED RECORDS", color = color, fontFamily = FontFamily.Monospace)
            related.forEach { neighbor ->
                Text("[OPEN #${neighbor.sequence}] ${neighbor.callsign ?: "UNKNOWN"}",
                    modifier = Modifier.clickable { onRelated(neighbor.id) },
                    color = color, fontFamily = FontFamily.Monospace)
            }
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
