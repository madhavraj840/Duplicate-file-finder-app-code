package com.bunkwise.duplicatefilefinder.core.data

import android.content.Context
import com.bunkwise.duplicatefilefinder.core.domain.model.Confidence
import com.bunkwise.duplicatefilefinder.core.domain.model.DuplicateGroup
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.model.MatchEvidence
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import com.bunkwise.duplicatefilefinder.core.domain.model.Signal
import com.bunkwise.duplicatefilefinder.core.domain.repository.ScanResultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Holds the LAST completed scan as the single source of truth for Results/Cleanup
 * (ARCHITECTURE §4.3). v1 keeps it in a StateFlow and mirrors a JSON snapshot to
 * internal storage so results survive process death. (Room is the production
 * drop-in behind this same interface — §8.)
 */
class ScanResultRepositoryImpl(
    context: Context
) : ScanResultRepository {

    private val file = File(context.filesDir, "last_scan.json")
    private val state = MutableStateFlow<ScanResult?>(loadFromDisk())

    val flow: StateFlow<ScanResult?> = state.asStateFlow()

    override fun observe() = flow
    override fun current(): ScanResult? = state.value

    override suspend fun save(result: ScanResult) {
        state.value = result
        withContext(Dispatchers.IO) { runCatching { file.writeText(serialize(result).toString()) } }
    }

    override suspend fun clear() {
        state.value = null
        withContext(Dispatchers.IO) { runCatching { file.delete() } }
    }

    override suspend fun removeFiles(fileIds: Set<Long>) {
        val current = state.value ?: return
        val newGroups = current.groups.mapNotNull { g ->
            val kept = g.files.filterNot { it.id in fileIds }
            when {
                kept.size < 2 -> null // a group needs >= 2 files
                else -> {
                    val keeperId = if (kept.any { it.id == g.keeperId }) g.keeperId else kept.first().id
                    g.copy(files = kept, keeperId = keeperId)
                }
            }
        }
        save(current.copy(groups = newGroups))
    }

    // ---- JSON (de)serialization ----

    private fun serialize(r: ScanResult): JSONObject = JSONObject().apply {
        put("mode", r.mode.name)
        put("finishedAtMs", r.finishedAtMs)
        put("categories", JSONArray(r.categories.map { it.name }))
        put("groups", JSONArray(r.groups.map { g ->
            JSONObject().apply {
                put("id", g.id)
                put("category", g.category.name)
                put("keeperId", g.keeperId)
                g.evidence?.let { put("evidence", serializeEvidence(it)) }
                put("files", JSONArray(g.files.map { serializeFile(it) }))
            }
        }))
    }

    private fun serializeEvidence(e: MatchEvidence) = JSONObject().apply {
        put("fusedScore", e.fusedScore.toDouble())
        put("confidence", e.confidence.name)
        put("signals", JSONArray(e.signals.map {
            JSONObject().apply {
                put("name", it.name); put("value", it.value.toDouble()); put("passed", it.passed)
            }
        }))
    }

    private fun serializeFile(f: FileItem) = JSONObject().apply {
        put("id", f.id); put("path", f.path); put("uri", f.uri)
        put("displayName", f.displayName); put("sizeBytes", f.sizeBytes)
        put("modifiedMs", f.modifiedMs); put("mime", f.mime ?: JSONObject.NULL)
        put("category", f.category.name); put("width", f.width)
        put("height", f.height); put("durationMs", f.durationMs)
    }

    private fun loadFromDisk(): ScanResult? = runCatching {
        if (!file.exists()) return null
        parse(JSONObject(file.readText()))
    }.getOrNull()

    private fun parse(o: JSONObject): ScanResult {
        val groups = o.getJSONArray("groups").let { arr ->
            (0 until arr.length()).map { i -> parseGroup(arr.getJSONObject(i)) }
        }
        val categories = o.getJSONArray("categories").let { arr ->
            (0 until arr.length()).map { FileCategory.valueOf(arr.getString(it)) }.toSet()
        }
        return ScanResult(
            mode = ScanMode.valueOf(o.getString("mode")),
            categories = categories,
            groups = groups,
            finishedAtMs = o.getLong("finishedAtMs")
        )
    }

    private fun parseGroup(o: JSONObject): DuplicateGroup {
        val files = o.getJSONArray("files").let { arr ->
            (0 until arr.length()).map { parseFile(arr.getJSONObject(it)) }
        }
        val evidence = if (o.has("evidence")) parseEvidence(o.getJSONObject("evidence")) else null
        return DuplicateGroup(
            id = o.getLong("id"),
            category = FileCategory.valueOf(o.getString("category")),
            files = files,
            keeperId = o.getLong("keeperId"),
            evidence = evidence
        )
    }

    private fun parseEvidence(o: JSONObject): MatchEvidence {
        val signals = o.getJSONArray("signals").let { arr ->
            (0 until arr.length()).map {
                val s = arr.getJSONObject(it)
                Signal(s.getString("name"), s.getDouble("value").toFloat(), s.getBoolean("passed"))
            }
        }
        return MatchEvidence(
            fusedScore = o.getDouble("fusedScore").toFloat(),
            confidence = Confidence.valueOf(o.getString("confidence")),
            signals = signals
        )
    }

    private fun parseFile(o: JSONObject) = FileItem(
        id = o.getLong("id"),
        path = o.getString("path"),
        uri = o.getString("uri"),
        displayName = o.getString("displayName"),
        sizeBytes = o.getLong("sizeBytes"),
        modifiedMs = o.getLong("modifiedMs"),
        mime = if (o.isNull("mime")) null else o.getString("mime"),
        category = FileCategory.valueOf(o.getString("category")),
        width = o.getInt("width"),
        height = o.getInt("height"),
        durationMs = o.getLong("durationMs")
    )
}
