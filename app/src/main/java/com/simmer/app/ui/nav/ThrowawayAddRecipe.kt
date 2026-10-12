package com.simmer.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.data.RecipeRepository
import com.simmer.app.domain.model.Recipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** THROWAWAY: fakes the missing Add recipe review. Never merge. */
@HiltViewModel
class ThrowawayVm @Inject constructor(
    val drafts: RecipeDraftStore,
    private val recipes: RecipeRepository,
) : ViewModel() {
    private val sample = Recipe(
        id = "mine-test", title = "Test pancakes", category = "Breakfast", time = 20, difficulty = "Easy", base = 2,
        summary = "Fake User Recipe.", mine = true,
        ingredients = listOf(
            com.simmer.app.domain.model.Ingredient("fl", "Flour", 200.0, "g", aisle = "Baking"),
            com.simmer.app.domain.model.Ingredient("eg", "Eggs", 2.0, "", aisle = "Dairy"),
        ),
        steps = listOf(
            com.simmer.app.domain.model.Step("s1", "prep", "Mix", 120, "Whisk the {fl:flour} and {eg:eggs}."),
            com.simmer.app.domain.model.Step("s2", "cook", "Fry", 300, "Fry in a hot pan."),
        ),
    )

    fun loadDraft() = drafts.set(sample.copy(id = "draft"))
    fun saveUserRecipe() { viewModelScope.launch { recipes.save(sample) } }
}

@Composable
fun ThrowawayAddRecipe(onEditDraft: () -> Unit, vm: ThrowawayVm = hiltViewModel()) {
    val draft by vm.drafts.current.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("THROWAWAY Add recipe review")
        Text("Draft title: ${draft?.title}")
        Button(onClick = { vm.loadDraft(); onEditDraft() }) { Text("Load fake draft and Edit") }
        Button(onClick = { vm.saveUserRecipe() }) { Text("Save fake User Recipe to Room") }
    }
}
