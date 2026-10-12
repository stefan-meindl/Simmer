package com.simmer.app

import com.simmer.app.data.draftFromJson
import com.simmer.app.data.toDraftJson
import com.simmer.app.domain.EXAMPLE_TEXT
import com.simmer.app.domain.stubConvert
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seam: [toDraftJson] and [draftFromJson], how the Add recipe draft is kept in saved state across process death. */
class AddRecipeDraftRestoreTest {

    @Test fun `the converted example survives a round trip unchanged`() {
        val draft = stubConvert(EXAMPLE_TEXT, id = "u1")!!

        assertEquals(draft, draftFromJson(draft.toDraftJson()))
    }

    @Test fun `edits made to the draft survive a round trip`() {
        val edited = stubConvert(EXAMPLE_TEXT, id = "u1")!!.copy(title = "Sunday chicken", time = 75)

        assertEquals(edited, draftFromJson(edited.toDraftJson()))
    }
}
