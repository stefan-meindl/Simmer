package com.simmer.app

import com.simmer.app.domain.CookingPersistence
import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.CookingSnapshot
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cooking Progress outlives the process: a new store over the same persistence sees what the old one saw. */
class CookingProgressPersistenceTest {

    private class FakePersistence : CookingPersistence {
        var saved = CookingSnapshot()
        override fun load() = saved
        override fun save(snapshot: CookingSnapshot) { saved = snapshot }
    }

    private val disk = FakePersistence()
    private fun store() = CookingProgressStore(disk)

    @Test
    fun markedStepsSurviveProcessDeath() {
        store().apply {
            markDone("chilli", "p-veg")
            markDone("chilli", "c-simmer")
            undo("chilli", "p-veg")
        }
        assertEquals(setOf("c-simmer"), store().doneSteps("chilli"))
    }

    @Test
    fun theChoiceMarkedStepsBelongToSurvivesProcessDeath() {
        val choice = PlannedChoice(6, Selection(choice = mapOf("mince" to "plant"), add = mapOf("choc" to true)))
        store().setChoice("chilli", choice)
        assertEquals(choice, store().choice("chilli"))
    }

    @Test
    fun resetSurvivesProcessDeathAndKeepsTheChoice() {
        val choice = PlannedChoice(6, Selection())
        store().apply {
            setChoice("chilli", choice)
            markDone("chilli", "p-veg")
            markDone("curry", "p-rice")
            reset("chilli")
        }
        val reopened = store()
        assertEquals(emptySet<String>(), reopened.doneSteps("chilli"))
        assertEquals(setOf("p-rice"), reopened.doneSteps("curry"))
        assertEquals(choice, reopened.choice("chilli"))
    }
}
