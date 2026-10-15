package com.simmer.app

import androidx.lifecycle.SavedStateHandle
import com.simmer.app.data.CaptionFetcher
import com.simmer.app.data.CaptionStatus
import com.simmer.app.data.ReelCard
import com.simmer.app.data.ReelImport
import com.simmer.app.domain.withCaption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: [ReelImport], the Reel card's state and what it puts in the text field, over a fake [CaptionFetcher]. */
class ReelImportTest {

    private val url = "https://www.instagram.com/reel/CHOJBJ6pcSx/"

    private class FakeFetcher : CaptionFetcher {
        val answers = mutableListOf<CompletableDeferred<String?>>()
        val asked = mutableListOf<String>()
        override suspend fun fetch(reelUrl: String): String? {
            asked += reelUrl
            return CompletableDeferred<String?>().also { answers += it }.await()
        }
    }

    private class Harness(scope: TestScope, state: SavedStateHandle = SavedStateHandle(), val fetcher: FakeFetcher = FakeFetcher()) {
        val captions = mutableListOf<String>()
        val import = ReelImport(state, fetcher, scope, onCaption = { captions += it })
    }

    @Test fun `no reel means no card`() = runTest {
        assertNull(Harness(this).import.card.first())
    }

    @Test fun `starting shows the link loading and asks for its caption`() = runTest {
        val h = Harness(this)

        h.import.start(url)
        advanceUntilIdle()

        assertEquals(ReelCard(url, CaptionStatus.Loading, 0), h.import.card.first())
        assertEquals(listOf(url), h.fetcher.asked)
        h.fetcher.answers.single().complete(null)
    }

    @Test fun `a found caption is handed on and counted in lines`() = runTest {
        val h = Harness(this)
        h.import.start(url)
        advanceUntilIdle()

        h.fetcher.answers.single().complete("Pasta\n\n2 eggs\n200 g flour")
        advanceUntilIdle()

        assertEquals(ReelCard(url, CaptionStatus.Found, 4), h.import.card.first())
        assertEquals(listOf("Pasta\n\n2 eggs\n200 g flour"), h.captions)
    }

    @Test fun `the card says found only once the caption is placed`() = runTest {
        val saved = SavedStateHandle()
        val fetcher = FakeFetcher()
        var statusWhilePlacing: CaptionStatus? = null
        lateinit var import: ReelImport
        import = ReelImport(saved, fetcher, this, onCaption = { statusWhilePlacing = import.card.first()?.status })
        import.start(url)
        advanceUntilIdle()

        fetcher.answers.single().complete("Soup")
        advanceUntilIdle()

        assertEquals(CaptionStatus.Loading, statusWhilePlacing)
        assertEquals(CaptionStatus.Found, import.card.first()?.status)
    }

    @Test fun `no caption is a status, not an error`() = runTest {
        val h = Harness(this)
        h.import.start(url)
        advanceUntilIdle()

        h.fetcher.answers.single().complete(null)
        advanceUntilIdle()

        assertEquals(ReelCard(url, CaptionStatus.NotFound, 0), h.import.card.first())
        assertEquals(emptyList<String>(), h.captions)
    }

    @Test fun `the source url is the reel link while there is a card`() = runTest {
        val h = Harness(this)
        assertNull(h.import.sourceUrl)

        h.import.start(url)

        assertEquals(url, h.import.sourceUrl)
        advanceUntilIdle()
        h.fetcher.answers.single().complete(null)
    }

    @Test fun `plain shared text removes the card`() = runTest {
        val h = Harness(this)
        h.import.start(url)
        advanceUntilIdle()
        h.fetcher.answers.single().complete(null)
        advanceUntilIdle()

        h.import.clear()

        assertNull(h.import.card.first())
        assertNull(h.import.sourceUrl)
    }

    @Test fun `a new link replaces the old one and ignores its late caption`() = runTest {
        val h = Harness(this)
        h.import.start(url)
        advanceUntilIdle()
        val other = "https://www.instagram.com/reel/AAAA/"

        h.import.start(other)
        advanceUntilIdle()
        h.fetcher.answers[0].complete("old caption")
        h.fetcher.answers[1].complete("new caption")
        advanceUntilIdle()

        assertEquals(ReelCard(other, CaptionStatus.Found, 1), h.import.card.first())
        assertEquals(listOf("new caption"), h.captions)
    }

    @Test fun `after process death a fetch that was loading is asked again`() = runTest {
        val saved = SavedStateHandle()
        val before = Harness(this, saved)
        before.import.start(url)
        advanceUntilIdle()

        val after = Harness(this, saved)
        after.import.resume()
        advanceUntilIdle()

        assertEquals(listOf(url), after.fetcher.asked)
        after.fetcher.answers.single().complete("Soup")
        advanceUntilIdle()
        assertEquals(listOf("Soup"), after.captions)
        before.fetcher.answers.single().complete(null)
    }

    @Test fun `after process death a finished card is not fetched again`() = runTest {
        val saved = SavedStateHandle()
        val before = Harness(this, saved)
        before.import.start(url)
        advanceUntilIdle()
        before.fetcher.answers.single().complete("Soup")
        advanceUntilIdle()

        val after = Harness(this, saved)
        after.import.resume()
        advanceUntilIdle()

        assertEquals(ReelCard(url, CaptionStatus.Found, 1), after.import.card.first())
        assertEquals(emptyList<String>(), after.fetcher.asked)
    }

    @Test fun `the caption goes under a label line after any text already there`() {
        assertEquals("Caption\nPasta", withCaption("", "Pasta", "Caption"))
        assertEquals("Mine\n\nCaption\nPasta", withCaption("Mine", "Pasta", "Caption"))
        assertEquals("Mine\n\nCaption\nPasta", withCaption("Mine\n\n", "Pasta", "Caption"))
    }

    @Test fun `a caption already in the text is not added twice`() {
        assertEquals("Mine\n\nCaption\nPasta", withCaption("Mine\n\nCaption\nPasta", "Pasta", "Caption"))
    }
}
