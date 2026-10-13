package com.simmer.app

import com.simmer.app.domain.PipelineStage
import com.simmer.app.domain.StageMark
import com.simmer.app.domain.stageMarks
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seam: [stageMarks], the Working screen's checklist. */
class StageMarksTest {

    @Test fun `the first pass is current and the rest pending`() {
        assertEquals(
            listOf(StageMark.Current, StageMark.Pending, StageMark.Pending, StageMark.Pending),
            stageMarks(PipelineStage.Ingredients).map { it.second },
        )
    }

    @Test fun `earlier passes are done and later ones pending`() {
        assertEquals(
            listOf(StageMark.Done, StageMark.Done, StageMark.Current, StageMark.Pending),
            stageMarks(PipelineStage.Linking).map { it.second },
        )
    }

    @Test fun `the last pass has three checks`() {
        assertEquals(
            listOf(StageMark.Done, StageMark.Done, StageMark.Done, StageMark.Current),
            stageMarks(PipelineStage.Timers).map { it.second },
        )
    }
}
