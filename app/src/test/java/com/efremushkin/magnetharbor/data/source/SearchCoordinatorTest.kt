package com.efremushkin.magnetharbor.data.source

import com.efremushkin.magnetharbor.data.model.TorrentCategory
import com.efremushkin.magnetharbor.data.model.TorrentResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCoordinatorTest {
    @Test
    fun `search combines enabled sources and ignores disabled sources`() = runTest {
        val enabled = FakeSource("one", listOf(result("First", HASH_ONE)))
        val disabled = FakeSource("two", listOf(result("Second", HASH_TWO)))
        val coordinator = SearchCoordinator(listOf(enabled, disabled))

        val batch = coordinator.search("linux", setOf("one"))

        assertEquals(listOf("First"), batch.results.map { it.title })
        assertTrue(batch.failures.isEmpty())
    }

    @Test
    fun `one failing source does not cancel successful sources`() = runTest {
        val good = FakeSource("good", listOf(result("Good", HASH_ONE)))
        val bad = FakeSource("bad", failure = IllegalStateException("temporarily unavailable"))
        val coordinator = SearchCoordinator(listOf(good, bad))

        val batch = coordinator.search("archive", setOf("good", "bad"))

        assertEquals(1, batch.results.size)
        assertEquals("Good", batch.results.single().title)
        assertEquals("temporarily unavailable", batch.failures.single().message)
    }

    @Test
    fun `duplicates use the copy with the highest seeder count`() = runTest {
        val low = result("Same item", HASH_ONE, seeders = 2)
        val high = result("Same item mirror", HASH_ONE.uppercase(), seeders = 20)
        val coordinator = SearchCoordinator(
            listOf(
                FakeSource("one", listOf(low)),
                FakeSource("two", listOf(high)),
            ),
        )

        val batch = coordinator.search("same", setOf("one", "two"))

        assertEquals(1, batch.results.size)
        assertEquals(20, batch.results.single().seeders)
    }

    private fun result(title: String, hash: String, seeders: Int = 1) = TorrentResult(
        title = title,
        magnetUri = "magnet:?xt=urn:btih:$hash",
        source = "test",
        sizeBytes = 1_024,
        seeders = seeders,
        leechers = 0,
        category = TorrentCategory.SOFTWARE,
    )

    private class FakeSource(
        override val id: String,
        private val results: List<TorrentResult> = emptyList(),
        private val failure: Throwable? = null,
    ) : TorrentSource {
        override val displayName = id

        override suspend fun search(query: String, page: Int): List<TorrentResult> {
            failure?.let { throw it }
            return results
        }
    }

    private companion object {
        const val HASH_ONE = "1111111111111111111111111111111111111111"
        const val HASH_TWO = "2222222222222222222222222222222222222222"
    }
}
