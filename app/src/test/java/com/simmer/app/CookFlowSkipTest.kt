package com.simmer.app

import com.simmer.app.domain.hasSelectableMethod
import com.simmer.app.domain.model.Method
import com.simmer.app.domain.model.MethodOption
import com.simmer.app.domain.model.Recipe
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [hasSelectableMethod], which decides whether the cooking flow shows Flow Phase 2. */
class CookFlowSkipTest {
    private fun option(v: String) = MethodOption(value = v, label = v)
    private fun recipe(methods: List<Method>) = Recipe(
        id = "r", title = "R", category = "Mains", time = 10, difficulty = "Easy", base = 2, methods = methods,
    )

    @Test fun `no methods means nothing to pick`() {
        assertFalse(hasSelectableMethod(recipe(emptyList())))
    }

    @Test fun `methods with a single option each mean nothing to pick`() {
        val m = Method("m", "M", "q", listOf(option("a")))
        assertFalse(hasSelectableMethod(recipe(listOf(m))))
    }

    @Test fun `a method without options means nothing to pick`() {
        assertFalse(hasSelectableMethod(recipe(listOf(Method("m", "M", "q", emptyList())))))
    }

    @Test fun `one method with a choice is enough`() {
        val single = Method("s", "S", "q", listOf(option("a")))
        val multi = Method("m", "M", "q", listOf(option("a"), option("b")))
        assertTrue(hasSelectableMethod(recipe(listOf(single, multi))))
    }

    @Test fun `built-in chilli has methods to pick`() {
        assertTrue(hasSelectableMethod(BuiltInRecipes.chilli()))
    }
}
