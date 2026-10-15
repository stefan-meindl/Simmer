package com.simmer.app

import com.simmer.app.data.toDomain
import com.simmer.app.data.toEntity

class SourceUrlTest {
    private val base = BuiltInRecipes.chilli()

    @org.junit.Test fun `json round trip keeps sourceUrl`() {
        val r = base.copy(sourceUrl = "https://www.instagram.com/reel/abc/")
        org.junit.Assert.assertEquals(r.sourceUrl, r.toEntity().toDomain().sourceUrl)
    }

    @org.junit.Test fun `json round trip without sourceUrl stays null`() {
        org.junit.Assert.assertNull(base.copy(sourceUrl = null).toEntity().toDomain().sourceUrl)
    }

    @org.junit.Test fun `legacy json without sourceUrl decodes to null`() {
        val json = base.toEntity().json.replace(Regex(",?\"sourceUrl\":[^,}]*"), "")
        org.junit.Assert.assertFalse(json.contains("sourceUrl"))
        org.junit.Assert.assertNull(com.simmer.app.data.RecipeEntity(base.id, json, false, false).toDomain().sourceUrl)
    }

    @org.junit.Test fun `edit and save keeps sourceUrl`() {
        val r = base.copy(sourceUrl = "https://example.com/r")
        val saved = com.simmer.app.domain.RecipeDraft.from(r).copy(title = "New title").toRecipe()
        org.junit.Assert.assertEquals("https://example.com/r", saved.sourceUrl)
        org.junit.Assert.assertEquals("New title", saved.title)
    }
}
