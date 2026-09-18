package com.bunkwise.duplicatefilefinder.core.engine

import com.bunkwise.duplicatefilefinder.core.common.DispatcherProvider
import com.bunkwise.duplicatefilefinder.core.common.Result
import com.bunkwise.duplicatefilefinder.core.domain.engine.DuplicateEngine
import com.bunkwise.duplicatefilefinder.core.domain.engine.ScanRequest
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.core.domain.model.Confidence
import com.bunkwise.duplicatefilefinder.core.domain.model.DuplicateGroup
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.model.MatchEvidence
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanPhase
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import com.bunkwise.duplicatefilefinder.core.domain.model.Signal
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import com.bunkwise.duplicatefilefinder.core.engine.algo.Hamming
import com.bunkwise.duplicatefilefinder.core.engine.algo.QuickHasher
import com.bunkwise.duplicatefilefinder.core.engine.algo.StreamingHasher
import com.bunkwise.duplicatefilefinder.core.engine.algo.UnionFind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The staged duplicate-detection pipeline (ARCHITECTURE §5). v1 runs the stages
 * sequentially inside the caller's coroutine (single-writer, race-free by
 * construction). The production target is the multi-worker channel pipeline of
 * §6.1 behind this same interface — an extension point, not a rewrite.
 */
class DuplicateEngineImpl(
    private val enumerator: FileEnumerator,
    private val imageFeatures: ImageFeatureExtractor,
    private val dispatchers: DispatcherProvider
) : DuplicateEngine {

    override suspend fun scan(
        request: ScanRequest,
        onProgress: (ScanProgress) -> Unit
    ): Result<ScanResult, AppError.Scan> {
        return try {
            val started = System.currentTimeMillis()
            // Stages 3/4 emit from parallel IO workers, so late emissions can arrive
            // out of order. Gate every emission so the reported percent never moves
            // backwards (a terminal DONE always passes).
            val lastPercent = AtomicInteger(-1)
            val post: (ScanProgress) -> Unit = { p ->
                if (p.phase == ScanPhase.DONE || p.percent >= lastPercent.get()) {
                    lastPercent.set(p.percent)
                    onProgress(p)
                }
            }
            var progress = ScanProgress()
            fun emit(update: ScanProgress.() -> ScanProgress) {
                progress = progress.update().copy(elapsedMs = System.currentTimeMillis() - started)
                post(progress)
            }

            // ---- STAGE 1: enumeration (0–10%) ----
            val files = ArrayList<FileItem>()
            emit { copy(phase = ScanPhase.ENUMERATING, percent = 0) }
            enumerator.enumerate(request.categories, request.fileBudget).collect { item ->
                currentCoroutineContext().ensureActive()
                files.add(item)
                if (files.size % 20 == 0) {
                    emit {
                        copy(
                            phase = ScanPhase.ENUMERATING,
                            filesScanned = files.size,
                            currentPath = item.path,
                            percent = 5
                        )
                    }
                }
            }
            if (files.isEmpty()) {
                return Result.Success(ScanResult(request.mode, request.categories, emptyList(), System.currentTimeMillis()))
            }
            emit { copy(phase = ScanPhase.ENUMERATING, filesScanned = files.size, percent = 10) }

            val uf = UnionFind(files.size)
            val inGroup = BooleanArray(files.size)
            var matched = 0
            fun markUnion(a: Int, b: Int) {
                uf.union(a, b)
                if (!inGroup[a]) { inGroup[a] = true; matched++ }
                if (!inGroup[b]) { inGroup[b] = true; matched++ }
            }

            // Written from parallel IO workers → concurrent map.
            val fullHashByIndex = ConcurrentHashMap<Int, String>()
            val dHashByIndex = HashMap<Int, Long>()

            withContext(dispatchers.io) {
                val job = currentCoroutineContext()[Job]
                val canceller = StreamingHasher.Canceller { job?.isActive == false }
                // Bounded IO parallelism (advice #16/§6.1): use several cores for
                // hashing/decoding instead of one blocking file at a time.
                val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                val ioPool = dispatchers.io.limitedParallelism(minOf(4, cores))
                val hasherPerThread = ThreadLocal.withInitial { StreamingHasher() }

                // ---- STAGE 3: exact identity (10–60%) ----
                val bySize = files.indices.groupBy { files[it].sizeBytes }
                val sizeCandidates = bySize.values.filter { it.size >= 2 }.flatten()

                // Quick-hash prune (cheap 48 KB reads, kept sequential). Bucket by
                // size + quick-hash so only true collisions get full-hashed.
                val byQuick = HashMap<String, MutableList<Int>>()
                var qDone = 0
                for (i in sizeCandidates) {
                    currentCoroutineContext().ensureActive()
                    val qh = QuickHasher.hash(files[i].path, files[i].sizeBytes) ?: continue
                    byQuick.getOrPut("${files[i].sizeBytes}:$qh") { ArrayList() }.add(i)
                    qDone++
                    if (qDone % 20 == 0) {
                        val pct = 10 + (25.0 * qDone / sizeCandidates.size.coerceAtLeast(1)).toInt()
                        emitPhase(ScanPhase.HASHING, pct, files[i].path, files.size, matched, started, post)
                    }
                }

                // Full-hash the survivors IN PARALLEL.
                val survivors = byQuick.values.filter { it.size >= 2 }.flatten()
                val fDone = AtomicInteger(0)
                coroutineScope {
                    survivors.map { i ->
                        async(ioPool) {
                            currentCoroutineContext().ensureActive()
                            hasherPerThread.get()?.hash(files[i].path, canceller)?.let { fullHashByIndex[i] = it }
                            val d = fDone.incrementAndGet()
                            if (d % 20 == 0) {
                                val pct = 35 + (25.0 * d / survivors.size.coerceAtLeast(1)).toInt()
                                emitPhase(ScanPhase.HASHING, pct, files[i].path, files.size, matched, started, post)
                            }
                        }
                    }.awaitAll()
                }
                // Union exact-identical files.
                fullHashByIndex.entries.groupBy({ it.value }, { it.key }).values.forEach { group ->
                    for (k in 1 until group.size) markUnion(group[0], group[k])
                }

                // ---- STAGE 4: similarity for images (60–90%) ----
                if (request.mode != ScanMode.EXACT) {
                    val imageIdx = files.indices.filter { files[it].category == FileCategory.IMAGES }
                    // Decode-once per EXACT group: byte-identical copies share a dHash,
                    // so fingerprint only one representative per union-find component
                    // (skips redundant decoding — advice #9). The rest follow via the
                    // exact union, and similarity only bridges representatives.
                    val seenRoot = HashSet<Int>()
                    val reps = imageIdx.filter { seenRoot.add(uf.find(it)) }
                    val featById = ConcurrentHashMap<Int, ImageHashes>()
                    val done = AtomicInteger(0)
                    coroutineScope {
                        reps.map { i ->
                            async(ioPool) {
                                currentCoroutineContext().ensureActive()
                                imageFeatures.hashes(files[i].path)?.let { featById[i] = it }
                                val d = done.incrementAndGet()
                                if (d % 10 == 0 || d == reps.size) {
                                    val pct = 60 + (30.0 * d / reps.size.coerceAtLeast(1)).toInt()
                                    emitPhase(ScanPhase.COMPARING, pct, files[i].path, files.size, matched, started, post)
                                }
                            }
                        }.awaitAll()
                    }
                    featById.forEach { (i, h) -> dHashByIndex[i] = h.dHash }
                    clusterSimilar(request.mode, featById, ::markUnion)
                }
            }

            // ---- STAGE 5: grouping (90–100%) ----
            emitPhase(ScanPhase.GROUPING, 92, "", files.size, matched, started, post)
            val groups = uf.componentsMinSize2().mapIndexed { gi, comp ->
                buildGroup(gi.toLong(), comp, files, fullHashByIndex, dHashByIndex)
            }.sortedByDescending { it.wastedBytes }

            emit {
                copy(
                    phase = ScanPhase.DONE,
                    percent = 100,
                    filesScanned = files.size,
                    duplicatesFound = groups.sumOf { it.duplicateCount }
                )
            }
            Result.Success(ScanResult(request.mode, request.categories, groups, System.currentTimeMillis()))
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.Error(AppError.Scan.UNKNOWN)
        }
    }

    private fun emitPhase(
        phase: ScanPhase, percent: Int, path: String, scanned: Int, matched: Int,
        started: Long, onProgress: (ScanProgress) -> Unit
    ) {
        onProgress(
            ScanProgress(
                phase = phase,
                percent = percent.coerceIn(0, 100),
                currentPath = path,
                filesScanned = scanned,
                duplicatesFound = matched,
                elapsedMs = System.currentTimeMillis() - started
            )
        )
    }

    /** Per-mode (dHash, aHash) distance ceilings. Thresholds INCREASE with mode:
     *  Similar (small edits) < Very Similar (crop/resize) < Deep (major changes). */
    private fun thresholdsFor(mode: ScanMode): Pair<Int, Int> = when (mode) {
        ScanMode.SIMILAR -> 6 to 6
        ScanMode.VERY_SIMILAR -> 10 to 9
        ScanMode.DEEP -> 14 to 12
        ScanMode.EXACT -> 0 to 0
    }

    /**
     * Seed-based clustering (replaces naive transitive union). Candidates come from
     * 4x16-bit dHash bands (never O(n^2)); a file joins a cluster ONLY when it is
     * within threshold of that cluster's SEED — never transitively — which stops
     * A~B~C chaining from collapsing unrelated images into one giant bogus group.
     * A match must clear BOTH the dHash AND aHash thresholds (two independent
     * signals), which is the false-positive guard.
     */
    private fun clusterSimilar(
        mode: ScanMode,
        feat: Map<Int, ImageHashes>,
        union: (Int, Int) -> Unit
    ) {
        if (feat.size < 2) return
        val (dThresh, aThresh) = thresholdsFor(mode)

        val bands = Array(4) { HashMap<Int, MutableList<Int>>() }
        for ((i, h) in feat) {
            for (b in 0 until 4) {
                val v = ((h.dHash ushr (b * 16)) and 0xFFFF).toInt()
                bands[b].getOrPut(v) { ArrayList() }.add(i)
            }
        }

        val assigned = HashSet<Int>()
        for (seed in feat.keys.sorted()) {
            if (seed in assigned) continue
            assigned.add(seed)
            val seedH = feat.getValue(seed)

            val candidates = LinkedHashSet<Int>()
            for (b in 0 until 4) {
                val v = ((seedH.dHash ushr (b * 16)) and 0xFFFF).toInt()
                bands[b][v]?.let { candidates.addAll(it) }
            }
            for (c in candidates) {
                if (c == seed || c in assigned) continue
                val h = feat.getValue(c)
                if (Hamming.distance(seedH.dHash, h.dHash) <= dThresh &&
                    Hamming.distance(seedH.aHash, h.aHash) <= aThresh
                ) {
                    assigned.add(c)
                    union(seed, c)
                }
            }
        }
    }

    private fun buildGroup(
        id: Long,
        comp: List<Int>,
        files: List<FileItem>,
        fullHash: Map<Int, String>,
        dHash: Map<Int, Long>
    ): DuplicateGroup {
        val groupFiles = comp.map { files[it] }
        val hashes = comp.mapNotNull { fullHash[it] }.toSet()
        val isExact = hashes.size == 1 && comp.all { fullHash[it] != null }

        val evidence: MatchEvidence? = if (isExact) {
            null // exact => 100%, no evidence rows needed
        } else {
            var minDist = 64
            val withHash = comp.filter { dHash[it] != null }
            if (withHash.size <= MAX_PAIRWISE_EVIDENCE) {
                for (x in withHash.indices) {
                    for (y in x + 1 until withHash.size) {
                        val d = Hamming.distance(dHash[withHash[x]]!!, dHash[withHash[y]]!!)
                        if (d < minDist) minDist = d
                    }
                }
            } else {
                // Big groups (burst sequences) would make the pairwise scan O(M²).
                // Members joined the cluster by distance to its seed, so distance to
                // one anchor is a faithful O(M) evidence estimate.
                val anchor = dHash[withHash.first()]!!
                for (y in 1 until withHash.size) {
                    val d = Hamming.distance(anchor, dHash[withHash[y]]!!)
                    if (d < minDist) minDist = d
                }
            }
            val confidence = when {
                minDist <= 2 -> Confidence.VERY_HIGH
                minDist <= 6 -> Confidence.HIGH
                else -> Confidence.MEDIUM
            }
            val score = (1f - minDist / 64f)
            MatchEvidence(
                fusedScore = score,
                confidence = confidence,
                signals = listOf(
                    Signal("Visual fingerprint", 1f - minDist / 32f, minDist <= 14),
                    Signal("Same file type", 1f, true)
                )
            )
        }

        val category = groupFiles.groupingBy { it.category }.eachCount()
            .maxByOrNull { it.value }?.key ?: FileCategory.OTHER
        return DuplicateGroup(
            id = id,
            category = category,
            files = groupFiles,
            keeperId = KeeperRanker.keeperId(groupFiles),
            evidence = evidence
        )
    }

    private companion object {
        /** Above this many hashed members, evidence uses the O(M) anchor path. */
        const val MAX_PAIRWISE_EVIDENCE = 24
    }
}
