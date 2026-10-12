package com.simmer.app.data

import android.content.Context
import com.simmer.app.domain.model.Recipe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val dao: RecipeDao,
    @ApplicationContext private val context: Context,
) {

    /**
     * Exposes all recipes as a reactive stream.
     * Built-in recipes are loaded from assets on first run if the DB is empty.
     */
    val recipes: Flow<List<Recipe>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Returns only User Recipes (mine = true).
     */
    val userRecipes: Flow<List<Recipe>> = dao.observeMine().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Seeds the database from the built-in recipes asset if it is empty.
     * Should be called once at startup.
     */
    suspend fun seedBuiltInsIfEmpty(assets: android.content.res.AssetManager) = withContext(Dispatchers.IO) {
        // Check if there are any built-in recipes
        val hasBuiltIns = dao.getById("chilli") != null
        if (!hasBuiltIns) {
            loadBuiltIns(assets)
        }
    }

    private suspend fun loadBuiltIns(assets: android.content.res.AssetManager) = withContext(Dispatchers.IO) {
        val json = assets.open("recipes.json").bufferedReader().readText()
        val arr = Json { ignoreUnknownKeys = true }.parseToJsonElement(json).jsonArray
        arr.forEach { elem ->
            val recipe = parseBuiltInRecipe(elem.jsonObject)
            dao.upsert(recipe.toEntity())
        }
    }

    private fun parseBuiltInRecipe(obj: JsonObject): Recipe {
        // Parse ingredients
        val ingredients = obj["ingredients"]?.jsonArray?.map { ing ->
            val i = ing.jsonObject
            val uRaw = i["u"]
            val (u, uPlural) = when {
                uRaw is JsonArray -> (uRaw[0].jsonPrimitive.content) to (uRaw[1].jsonPrimitive.content)
                uRaw is JsonPrimitive -> uRaw.content to ""
                else -> "" to ""
            }
            val alt = i["alt"]?.jsonArray?.map { pair ->
                val arr = pair.jsonArray
                arr[0].jsonPrimitive.content to arr[1].jsonPrimitive.double
            } ?: emptyList()
            com.simmer.app.domain.model.Ingredient(
                id = i["id"]!!.jsonPrimitive.content,
                name = i["name"]!!.jsonPrimitive.content,
                quantity = i["q"]!!.jsonPrimitive.double,
                unit = u,
                uPlural = uPlural,
                note = i["note"]?.jsonPrimitive?.content ?: "",
                aisle = i["aisle"]?.jsonPrimitive?.content ?: "",
                tips = i["tips"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                ifCondition = i["if"]?.jsonPrimitive?.content,
                alt = alt,
            )
        } ?: emptyList()

        // Parse variations
        val variations = obj["variations"]?.jsonArray?.map { v ->
            val vObj = v.jsonObject
            val options = vObj["options"]?.jsonArray?.map { o ->
                val oObj = o.jsonObject
                com.simmer.app.domain.model.Option(
                    value = oObj["v"]!!.jsonPrimitive.content,
                    label = oObj["l"]!!.jsonPrimitive.content,
                )
            } ?: emptyList()
            com.simmer.app.domain.model.Variation(
                id = vObj["id"]!!.jsonPrimitive.content,
                kind = vObj["kind"]!!.jsonPrimitive.content,
                label = vObj["label"]?.jsonPrimitive?.content ?: "",
                options = options,
                def = vObj["def"]?.jsonPrimitive?.content ?: "",
                d = vObj["d"]?.jsonPrimitive?.content ?: "",
                ing = vObj["ing"]?.jsonPrimitive?.content ?: "",
            )
        } ?: emptyList()

        // Parse methods
        val methods = obj["methods"]?.jsonArray?.map { m ->
            val mObj = m.jsonObject
            val options = mObj["options"]!!.jsonArray.map { o ->
                val oObj = o.jsonObject
                com.simmer.app.domain.model.MethodOption(
                    value = oObj["v"]!!.jsonPrimitive.content,
                    label = oObj["l"]!!.jsonPrimitive.content,
                    icon = oObj["icon"]?.jsonPrimitive?.content ?: "flame",
                    time = oObj["time"]?.jsonPrimitive?.content ?: "",
                    d = oObj["d"]?.jsonPrimitive?.content ?: "",
                )
            }
            com.simmer.app.domain.model.Method(
                id = mObj["id"]!!.jsonPrimitive.content,
                label = mObj["label"]?.jsonPrimitive?.content ?: "",
                q = mObj["q"]?.jsonPrimitive?.content ?: "",
                options = options,
            )
        } ?: emptyList()

        // Parse steps
        val steps = obj["steps"]?.jsonArray?.map { s ->
            val sObj = s.jsonObject
            com.simmer.app.domain.model.Step(
                id = sObj["id"]!!.jsonPrimitive.content,
                phase = sObj["ph"]!!.jsonPrimitive.content,
                title = sObj["t"]!!.jsonPrimitive.content,
                durationSecs = sObj["d"]!!.jsonPrimitive.int,
                body = sObj["b"]!!.jsonPrimitive.content,
                needs = sObj["needs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                ifCondition = sObj["if"]?.jsonPrimitive?.content,
            )
        } ?: emptyList()

        return Recipe(
            id = obj["id"]!!.jsonPrimitive.content,
            title = obj["title"]!!.jsonPrimitive.content,
            category = obj["category"]!!.jsonPrimitive.content,
            tags = obj["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            time = obj["time"]!!.jsonPrimitive.int,
            difficulty = obj["difficulty"]!!.jsonPrimitive.content,
            base = obj["base"]!!.jsonPrimitive.int,
            icon = obj["icon"]?.jsonPrimitive?.content ?: "utensils",
            summary = obj["summary"]?.jsonPrimitive?.content ?: "",
            variations = variations,
            methods = methods,
            ingredients = ingredients,
            steps = steps,
            mine = false,
            edited = false,
        )
    }

    /**
     * Saves a User Recipe or persists an edit to a built-in recipe.
     * If the recipe is a built-in, marks it as edited = true.
     */
    suspend fun save(recipe: Recipe) = withContext(Dispatchers.IO) {
        dao.upsert(recipe.toEntity())
    }

    /**
     * Deletes a User Recipe by ID. Only User Recipes (mine = true) can be deleted.
     */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    /**
     * Restores a built-in recipe to its original state by re-loading from the asset.
     */
    suspend fun restoreBuiltIn(id: String) = withContext(Dispatchers.IO) {
        val json = context.assets.open("recipes.json").bufferedReader().readText()
        val arr = Json { ignoreUnknownKeys = true }.parseToJsonElement(json).jsonArray
        val recipeObj = arr.map { it.jsonObject }.firstOrNull { it["id"]?.jsonPrimitive?.content == id }
        if (recipeObj != null) {
            val recipe = parseBuiltInRecipe(recipeObj)
            dao.upsert(recipe.copy(edited = false).toEntity())
        }
    }
}
