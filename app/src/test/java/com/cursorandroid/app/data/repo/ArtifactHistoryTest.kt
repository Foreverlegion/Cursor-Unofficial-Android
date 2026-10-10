package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ArtifactItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class ArtifactHistoryTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = 1_760_000_000_000L

    @Test
    fun formatsUpdatedAtInTheDeviceZoneAndLocale() {
        val label = ArtifactTime.format("2026-10-10T05:52:00Z", zone, Locale.US)
        assertEquals("Oct 10, 1:52 AM", label)
        val utc = ArtifactTime.format("2026-10-10T05:52:00Z", ZoneId.of("UTC"), Locale.US)
        assertEquals("Oct 10, 5:52 AM", utc)
    }

    @Test
    fun blankOrUnparsedTimesStayUsable() {
        assertEquals("", ArtifactTime.format(null, zone, Locale.US))
        assertEquals("", ArtifactTime.format("  ", zone, Locale.US))
        assertEquals("not-a-date", ArtifactTime.format("not-a-date", zone, Locale.US))
    }

    @Test
    fun sameNameFromLaterRunsStaysDistinctAndNewestFirst() {
        val first = ArtifactHistoryLogic.merge(
            existing = emptyList(),
            incoming = listOf(ArtifactItem("notes.md", 100, "2026-10-06T18:20:00Z")),
            producedAt = null,
            now = now,
        )
        val second = ArtifactHistoryLogic.merge(
            existing = first.kept,
            incoming = listOf(ArtifactItem("notes.md", 140, "2026-10-10T05:52:00Z")),
            producedAt = null,
            now = now + 1_000,
        )
        assertEquals(2, second.kept.size)
        assertEquals("2026-10-10T05:52:00Z", second.kept[0].updatedAt)
        assertEquals("2026-10-06T18:20:00Z", second.kept[1].updatedAt)
        assertEquals("notes.md", second.kept[0].path)
        assertEquals("notes.md", second.kept[1].path)
        val labels = second.kept.map { ArtifactTime.format(it.updatedAt, zone, Locale.US) }
        assertEquals(listOf("Oct 10, 1:52 AM", "Oct 6, 2:20 PM"), labels)
    }

    @Test
    fun missingApiTimeUsesTheProducingRunAndDoesNotDuplicateOnRefresh() {
        val first = ArtifactHistoryLogic.merge(
            existing = emptyList(),
            incoming = listOf(ArtifactItem(path = "notes.md", sizeBytes = 100)),
            producedAt = "2026-10-10T05:52:00Z",
            now = now,
        )
        assertEquals("2026-10-10T05:52:00Z", first.kept.single().updatedAt)
        val again = ArtifactHistoryLogic.merge(
            existing = first.kept,
            incoming = listOf(ArtifactItem(path = "notes.md", sizeBytes = 100)),
            producedAt = "2026-10-10T05:52:00Z",
            now = now + 5_000,
        )
        assertEquals(1, again.kept.size)
        assertEquals("2026-10-10T05:52:00Z", again.resolved.single().updatedAt)
    }

    @Test
    fun sizeChangeWithoutApiTimeKeepsTheEarlierRun() {
        val first = ArtifactHistoryLogic.merge(
            existing = emptyList(),
            incoming = listOf(ArtifactItem(path = "notes.md", sizeBytes = 100)),
            producedAt = "2026-10-06T18:20:00Z",
            now = now,
        )
        val rewritten = ArtifactHistoryLogic.merge(
            existing = first.kept,
            incoming = listOf(ArtifactItem(path = "notes.md", sizeBytes = 240)),
            producedAt = "2026-10-10T05:52:00Z",
            now = now + 1_000,
        )
        assertEquals(
            listOf("2026-10-10T05:52:00Z", "2026-10-06T18:20:00Z"),
            rewritten.kept.map { it.updatedAt },
        )
    }

    @Test
    fun createdAtFillsInWhenUpdatedAtIsAbsent() {
        val merged = ArtifactHistoryLogic.merge(
            existing = emptyList(),
            incoming = listOf(
                ArtifactItem(path = "a.md", sizeBytes = 1, updatedAt = null, createdAt = "2026-10-10T05:52:00Z"),
                ArtifactItem(path = "b.md", sizeBytes = 1, updatedAt = "2026-10-06T18:20:00Z", createdAt = "2026-10-01T00:00:00Z"),
            ),
            producedAt = "2026-10-11T00:00:00Z",
            now = now,
        )
        val byPath = merged.kept.associate { it.path to it.updatedAt }
        assertEquals("2026-10-10T05:52:00Z", byPath["a.md"])
        assertEquals("2026-10-06T18:20:00Z", byPath["b.md"])
        assertEquals("a.md", merged.kept.first().path)
    }

    @Test
    fun historyKeepsOnlyTheNewestCopies() {
        val incoming = (1..10).map { index ->
            ArtifactItem("notes.md", index.toLong(), "2026-10-${index.toString().padStart(2, '0')}T00:00:00Z")
        }
        val merged = ArtifactHistoryLogic.merge(emptyList(), incoming, null, now)
        assertEquals(ArtifactHistoryLogic.MAX_PER_AGENT, merged.kept.size)
        assertEquals("2026-10-10T00:00:00Z", merged.kept.first().updatedAt)
        assertTrue(merged.kept.none { it.updatedAt == "2026-10-01T00:00:00Z" })
        assertTrue(merged.kept.none { it.updatedAt == "2026-10-02T00:00:00Z" })
    }

    @Test
    fun artifactPayloadDecodesUpdatedAt() {
        val json = Json { ignoreUnknownKeys = true }
        val item = json.decodeFromString<ArtifactItem>(
            """{"path":"artifacts/screenshot.png","sizeBytes":12345,"updatedAt":"2026-04-13T18:45:00.000Z"}""",
        )
        assertEquals("screenshot.png", item.fileName())
        assertEquals("2026-04-13T18:45:00.000Z", item.whenIso())
        assertEquals("Apr 13, 2:45 PM", ArtifactTime.format(item.whenIso(), zone, Locale.US))
    }
}
