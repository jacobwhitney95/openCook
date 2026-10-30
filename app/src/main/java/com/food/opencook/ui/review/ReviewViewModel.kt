/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.ui.review

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.image.ImageEditor
import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.SaveResult
import com.food.opencook.repository.SuggestionRepository
import com.food.opencook.util.IngredientCorrection
import com.food.opencook.ui.navigation.Routes
import com.food.opencook.util.DurationFormat
import com.food.opencook.util.MealTypes
import com.food.opencook.util.Numbers
import com.food.opencook.util.PastedList
import com.food.opencook.util.TextFold
import com.food.opencook.data.recipeimport.IngredientLineParser
import com.food.opencook.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/** Linear wizard the editor walks the user through. Order matches array index for the
 *  progress indicator (BASICS = step 1 of 5, SUMMARY = step 5 of 5). */
enum class WizardStep { BASICS, INGREDIENTS, STEPS, DETAILS, SUMMARY }

/**
 * A save the app refused because another recipe already carries this name. Carries
 * everything the dialog needs to explain the clash and to act on it.
 */
data class NameConflict(
    /** Which page of the review pager the blocked draft sits on. */
    val pageIndex: Int,
    val name: String,
    /** The recipe already holding the name — the one "replace" writes onto. */
    val existingId: String,
    /** True when that recipe is itself an unreviewed scan result — i.e. a double scan. */
    val isDraft: Boolean,
    /**
     * Whether "discard this one" may be offered. False when the editor was opened on an
     * already-saved recipe: there, throwing the draft away would delete that recipe.
     */
    val canDiscard: Boolean,
)

/** Editable in-memory copies of the detected recipes; mapped back to entities on save. */
data class EditableRecipe(
    val id: String,
    val sourcePhotoId: String?,
    val createdAt: Long,
    val name: String,
    val servings: String,
    val category: String,
    /** Selected [com.food.opencook.util.MealTypes] keys; empty = unset (default applies). */
    val mealTypes: List<String>,
    val prepTime: String,
    val cookTime: String,
    val notes: String,
    val cookbook: String,
    val ingredients: List<EditableIngredient>,
    val instructions: List<EditableInstruction>,
    val nutrition: EditableNutrition?,
    val images: List<ImageEntity>,
    /** Search tags, newline-joined (AI-assigned, user-correctable in the details step). */
    val tags: String? = null,
    // Fields the editor doesn't expose but must not destroy: persist() rebuilds the
    // whole entity, so anything missing here would be wiped on every edit (and the
    // wipe would sync). Carried through verbatim.
    val lastCookedAt: String? = null,
    val totalTime: String? = null,
    val description: String? = null,
    val sourceUrl: String? = null,
)

/**
 * [id] is null for a freshly added row; existing rows keep their id so edits sync in place.
 * [suggestion]/[autoCorrected] are UI-only review hints from the ingredient corrector
 * (never persisted): a non-null [suggestion] offers a tap-to-apply fix, [autoCorrected] marks
 * a name the corrector already snapped to a known term.
 * [rowKey] identifies the row in the editor list while it is dragged (see [EditableInstruction]).
 */
data class EditableIngredient(
    val id: String?,
    val quantity: String,
    val unit: String,
    val name: String,
    val suggestion: String? = null,
    val autoCorrected: Boolean = false,
    val rowKey: Long = nextRowKey(),
)

/**
 * [rowKey] is a UI-only list key, never persisted: the drag-to-reorder list needs one that is
 * unique and survives a move, and [id] is null for every freshly added row. `copy()` keeps it.
 */
data class EditableInstruction(val id: String?, val text: String, val rowKey: Long = nextRowKey())

private val rowKeys = AtomicLong()

private fun nextRowKey(): Long = rowKeys.incrementAndGet()

data class EditableNutrition(
    val calories: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val basis: String,
    // Not shown in the editor; carried through so an edit doesn't wipe them (see EditableRecipe).
    val fiber: String? = null,
    val sugar: String? = null,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val suggestionRepository: SuggestionRepository,
    private val imageStore: ImageStore,
    private val imageEditor: ImageEditor,
    settings: SettingsRepository,
) : ViewModel() {

    // Review-of-scan(s) uses jobId; editing one saved recipe uses recipeId.
    private val jobId: String? = savedStateHandle[Routes.ARG_JOB_ID]
    private val recipeId: String? = savedStateHandle[Routes.ARG_RECIPE_ID]

    /** null while loading; empty list means nothing was detected. */
    private val _recipes = MutableStateFlow<List<EditableRecipe>?>(null)
    val recipes: StateFlow<List<EditableRecipe>?> = _recipes.asStateFlow()

    /** Transient user message (e.g. "merged into the existing recipe"); the screen shows + clears it. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    /** A list just pasted into the editor and split into rows — the screen offers "Undo".
     *  [seq] makes two identical pastes in a row two events. */
    data class Pasted(val steps: Boolean, val count: Int, val seq: Long)

    private val _pasted = MutableStateFlow<Pasted?>(null)
    val pasted: StateFlow<Pasted?> = _pasted.asStateFlow()
    private var pasteSeq = 0L

    /** Done with [event] — unless a newer paste has replaced it meanwhile. */
    fun clearPasted(event: Pasted) { _pasted.compareAndSet(event, null) }

    /** The recipe (by page) as it was before the last split, for [undoPaste]. */
    private var beforePaste: Pair<Int, EditableRecipe>? = null

    private val _conflict = MutableStateFlow<NameConflict?>(null)
    /** Set when a save was blocked by a same-named recipe; the screen offers the way out. */
    val conflict: StateFlow<NameConflict?> = _conflict.asStateFlow()

    private val serverBaseUrl: StateFlow<String?> =
        settings.serverUrl.map { it?.trimEnd('/') }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val manual = jobId == Routes.JOB_ID_NEW
            val loaded = when {
                recipeId != null -> listOfNotNull(repository.getRecipeOnce(recipeId))
                manual -> emptyList()
                jobId == Routes.JOB_ID_ALL -> repository.getUnreviewedRecipes()
                jobId != null -> repository.getRecipesForJob(jobId)
                else -> emptyList()
            }
            val editables = loaded.map { it.toEditable() }
            _recipes.value = when {
                // Manual create: start with one blank draft so the editor has something to fill in.
                manual && editables.isEmpty() -> listOf(blankDraft())
                // Freshly-scanned recipes: run names through the offline corrector so the review
                // screen can offer typo fixes; never touch an existing saved recipe.
                recipeId == null -> {
                    val corrector = IngredientCorrection.corrector(suggestionRepository.pool())
                    editables.map { it.withCorrections(corrector) }
                }
                else -> editables
            }
            // Reviewing freshly-scanned recipes also acknowledges them (clears the
            // status strip); editing an existing recipe / starting from scratch doesn't.
            if (recipeId == null && !manual) repository.acknowledgeFinishedJobs()
        }
    }

    private fun blankDraft(): EditableRecipe {
        val now = System.currentTimeMillis()
        return EditableRecipe(
            id = UUID.randomUUID().toString(),
            sourcePhotoId = null,
            createdAt = now,
            name = "",
            servings = "",
            category = "",
            // The classic hot-meal slots pre-selected — same meaning as "unset".
            mealTypes = MealTypes.DEFAULT,
            prepTime = "",
            cookTime = "",
            notes = "",
            cookbook = "",
            ingredients = listOf(EditableIngredient(id = null, quantity = "", unit = "", name = "")),
            instructions = listOf(EditableInstruction(id = null, text = "")),
            nutrition = null,
            images = emptyList(),
        )
    }

    /** Coil model for a crop: a local [java.io.File] or a server URL; null if neither. */
    fun imageUrlFor(image: ImageEntity): Any? = when {
        image.localPath != null -> java.io.File(image.localPath)
        image.remoteName != null -> serverBaseUrl.value?.let { "$it/images/${image.remoteName}" }
        else -> null
    }

    fun updateRecipe(index: Int, transform: (EditableRecipe) -> EditableRecipe) {
        _recipes.update { list ->
            list?.mapIndexed { i, r -> if (i == index) transform(r) else r }
        }
    }

    fun addIngredient(index: Int) = updateRecipe(index) {
        it.copy(ingredients = it.ingredients + EditableIngredient(id = null, quantity = "", unit = "", name = ""))
    }

    fun removeIngredient(index: Int, ingredientIndex: Int) = updateRecipe(index) {
        it.copy(ingredients = it.ingredients.filterIndexed { i, _ -> i != ingredientIndex })
    }

    /** Accept the corrector's "Meinten Sie …?" suggestion for one ingredient. */
    fun applySuggestion(index: Int, ingredientIndex: Int) = updateRecipe(index) { r ->
        r.copy(ingredients = r.ingredients.mapIndexed { i, ing ->
            if (i == ingredientIndex && ing.suggestion != null) {
                ing.copy(name = ing.suggestion, suggestion = null, autoCorrected = false)
            } else {
                ing
            }
        })
    }

    fun addStep(index: Int) = updateRecipe(index) {
        it.copy(instructions = it.instructions + EditableInstruction(id = null, text = ""))
    }

    fun removeStep(index: Int, stepIndex: Int) = updateRecipe(index) {
        it.copy(instructions = it.instructions.filterIndexed { i, _ -> i != stepIndex })
    }

    /**
     * A multi-line paste into one ingredient's name field (GitHub #28): a name never holds
     * a line break, so this is a list — each line becomes a row, read like an imported
     * ingredient line ("400 g Spaghetti" → amount, unit, name). The first line fills the row
     * pasted into, the rest follow it.
     */
    fun pasteIngredients(index: Int, at: Int, text: String) {
        val parsed = PastedList.ingredientLines(text).map { line ->
            val dto = IngredientLineParser.parse(line)
            EditableIngredient(null, Numbers.formatQuantity(dto.quantity).orEmpty(), dto.unit.orEmpty(), dto.name)
        }
        if (parsed.size < 2) {
            // A single line after all (e.g. "Salz\n"): just its text, as if typed.
            val name = PastedList.ingredientLines(text).firstOrNull().orEmpty()
            updateRecipe(index) { r ->
                r.copy(ingredients = r.ingredients.mapIndexed { j, ing ->
                    if (j == at) ing.copy(name = name, suggestion = null, autoCorrected = false) else ing
                })
            }
            return
        }
        splitInto(index, steps = false, count = parsed.size) { r ->
            val rows = r.ingredients.toMutableList()
            val target = rows.getOrNull(at) ?: return@splitInto r
            rows[at] = parsed.first().copy(id = target.id, rowKey = target.rowKey)
            rows.addAll(at + 1, parsed.drop(1))
            r.copy(ingredients = rows)
        }
    }

    /** A pasted list in one step field — see [PastedList.isPastedSteps] — split into steps. */
    fun pasteSteps(index: Int, at: Int, text: String) {
        val steps = PastedList.steps(text)
        splitInto(index, steps = true, count = steps.size) { r ->
            val rows = r.instructions.toMutableList()
            val target = rows.getOrNull(at) ?: return@splitInto r
            rows[at] = target.copy(text = steps.first())
            rows.addAll(at + 1, steps.drop(1).map { EditableInstruction(id = null, text = it) })
            r.copy(instructions = rows)
        }
    }

    private fun splitInto(index: Int, steps: Boolean, count: Int, transform: (EditableRecipe) -> EditableRecipe) {
        val before = _recipes.value?.getOrNull(index) ?: return
        beforePaste = index to before
        updateRecipe(index, transform)
        _pasted.value = Pasted(steps, count, ++pasteSeq)
    }

    /** Put the page back as it was before the last split — one paste of a list, undone. */
    fun undoPaste() {
        val (index, before) = beforePaste ?: return
        beforePaste = null
        updateRecipe(index) { before }
    }

    /** Move an ingredient row (drag & drop in the editor); its position is what gets saved and synced. */
    fun moveIngredient(index: Int, from: Int, to: Int) = updateRecipe(index) {
        it.copy(ingredients = it.ingredients.moved(from, to))
    }

    /** Move an instruction row so the user can reorder steps without delete-and-re-add. */
    fun moveStep(index: Int, from: Int, to: Int) = updateRecipe(index) {
        it.copy(instructions = it.instructions.moved(from, to))
    }

    private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
        if (from !in indices || to !in indices || from == to) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    // --- Wizard navigation (per pager page) ---

    private val _stepByPage = MutableStateFlow<Map<Int, WizardStep>>(emptyMap())
    val stepByPage: StateFlow<Map<Int, WizardStep>> = _stepByPage.asStateFlow()

    fun stepFor(page: Int): WizardStep = _stepByPage.value[page] ?: WizardStep.BASICS

    fun setStep(page: Int, step: WizardStep) {
        _stepByPage.update { it + (page to step) }
    }

    /** Attach a gallery image as the recipe's primary photo (non-blocking helper). */
    fun attachImage(index: Int, uri: Uri) {
        viewModelScope.launch {
            attachLocalImage(index, imageStore.saveFromUri(uri))
        }
    }

    /** The primary image of the recipe at [index], but only if it has a local file we
     *  can edit (rotate/crop need bytes on disk; a not-yet-downloaded remote image has none). */
    private fun editablePrimary(index: Int): ImageEntity? {
        val recipe = _recipes.value?.getOrNull(index) ?: return null
        val primary = recipe.images.firstOrNull { it.isPrimary } ?: recipe.images.firstOrNull()
        return primary?.takeIf { it.localPath != null }
    }

    /** Whether the recipe at [index] has a locally-editable primary image (drives the UI). */
    fun canEditImage(index: Int): Boolean = editablePrimary(index) != null

    /** Rotate the primary image 90° clockwise and re-attach it (re-uploads on next sync). */
    fun rotateImage(index: Int) {
        val image = editablePrimary(index) ?: return
        viewModelScope.launch {
            attachLocalImage(index, imageEditor.rotate90(image.localPath!!))
        }
    }

    /** Crop the primary image to a fractional rectangle (0..1) and re-attach it. */
    fun cropImage(index: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val image = editablePrimary(index) ?: return
        viewModelScope.launch {
            attachLocalImage(index, imageEditor.crop(image.localPath!!, left, top, right, bottom))
        }
    }

    /** Attach an already-saved local file (e.g. a camera capture) as primary photo. */
    fun attachLocalImage(index: Int, path: String) {
        val recipeId = _recipes.value?.getOrNull(index)?.id ?: return
        val image = ImageEntity(
            id = UUID.randomUUID().toString(),
            recipeId = recipeId,
            position = 0,
            remoteName = null,
            localPath = path,
            isPrimary = true,
        )
        updateRecipe(index) { it.copy(images = listOf(image)) }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch { saveFrom(0, onSaved) }
    }

    /**
     * Save the drafts from [startIndex] on, stopping at the first name clash so the
     * user can resolve it. Re-saving an already-saved draft is a no-op write (same id),
     * which is what lets every resolution simply resume the loop.
     */
    private suspend fun saveFrom(startIndex: Int, onSaved: () -> Unit) {
        val now = System.currentTimeMillis()
        val list = _recipes.value ?: return
        for (index in startIndex until list.size) {
            val editable = list[index]
            when (val result = persist(editable, now)) {
                SaveResult.Saved -> Unit
                is SaveResult.Duplicate -> {
                    _conflict.value = NameConflict(
                        pageIndex = index,
                        name = editable.name,
                        existingId = result.existingId,
                        isDraft = result.existingId in unreviewedIds(),
                        canDiscard = recipeId == null,
                    )
                    return
                }
            }
        }
        _conflict.value = null
        onSaved()
    }

    /** Ids of recipes that are themselves still-unreviewed scan results. */
    private suspend fun unreviewedIds(): Set<String> =
        repository.getUnreviewedRecipes().map { it.recipe.id }.toSet()

    /**
     * Take over the recipe we collide with: the edited content is written onto it and
     * this draft disappears. The usual cause is the same page scanned twice.
     */
    fun resolveByReplacing(onSaved: () -> Unit) {
        val conflict = _conflict.value ?: return
        viewModelScope.launch {
            val editable = _recipes.value?.getOrNull(conflict.pageIndex) ?: return@launch
            val now = System.currentTimeMillis()
            val e = entities(editable, now)
            repository.replaceRecipe(
                conflict.existingId, e.recipe, e.ingredients, e.instructions, e.nutrition, e.images,
            )
            dropPage(conflict.pageIndex)
            _message.value = context.getString(R.string.review_conflict_replaced, editable.name)
            _conflict.value = null
            // The list shrank by one, so this index now holds the *next* unsaved draft.
            saveFrom(conflict.pageIndex, onSaved)
        }
    }

    /** Throw this draft away and keep what is already saved. */
    fun resolveByDiscarding(onSaved: () -> Unit) {
        val conflict = _conflict.value ?: return
        viewModelScope.launch {
            val editable = _recipes.value?.getOrNull(conflict.pageIndex) ?: return@launch
            // A manually created draft was never written, so there is nothing to tombstone.
            if (repository.getRecipeOnce(editable.id) != null) repository.deleteRecipe(editable.id)
            dropPage(conflict.pageIndex)
            _message.value = context.getString(R.string.review_conflict_discarded)
            _conflict.value = null
            saveFrom(conflict.pageIndex, onSaved)
        }
    }

    /** Keep both recipes — back to the name field so the user can pick another title. */
    fun resolveByRenaming() {
        val conflict = _conflict.value ?: return
        setStep(conflict.pageIndex, WizardStep.BASICS)
        _conflict.value = null
    }

    private fun dropPage(index: Int) {
        _recipes.update { list -> list?.filterIndexed { i, _ -> i != index } }
    }

    /** Entity form of a draft, shared by the plain save and the "replace" resolution. */
    private data class Entities(
        val recipe: RecipeEntity,
        val ingredients: List<IngredientEntity>,
        val instructions: List<InstructionEntity>,
        val nutrition: NutritionEntity?,
        val images: List<ImageEntity>,
    )

    private suspend fun persist(e: EditableRecipe, now: Long): SaveResult {
        val entities = entities(e, now)
        return repository.saveRecipe(
            entities.recipe, entities.ingredients, entities.instructions, entities.nutrition, entities.images,
        )
    }

    private fun entities(e: EditableRecipe, now: Long): Entities {
        val servingsNum = e.servings.trim().toIntOrNull()
        val recipe = RecipeEntity(
            id = e.id,
            name = e.name.ifBlank { null },
            // Keep the numeric servings as the source of truth; the UI renders a localized
            // "N servings" label from it (no German string baked into stored data).
            recipeYield = null,
            servings = servingsNum,
            category = e.category.ifBlank { null },
            // Deselecting everything stores null — the "lunch + dinner" default applies again.
            mealTypes = MealTypes.toStored(e.mealTypes),
            notes = e.notes.ifBlank { null },
            cookbook = e.cookbook.ifBlank { null },
            prepTime = DurationFormat.toIso(e.prepTime),
            cookTime = DurationFormat.toIso(e.cookTime),
            totalTime = e.totalTime,
            description = e.description,
            sourceUrl = e.sourceUrl,
            tags = e.tags,
            lastCookedAt = e.lastCookedAt,
            sourcePhotoId = e.sourcePhotoId,
            createdAt = e.createdAt,
            updatedAt = now,
        )
        val ingredients = e.ingredients
            .filter { it.quantity.isNotBlank() || it.name.isNotBlank() }
            .mapIndexed { i, ing ->
                IngredientEntity(
                    id = ing.id ?: UUID.randomUUID().toString(), // reuse stable id on edit
                    recipeId = e.id,
                    position = i,
                    quantity = TextFold.digits(ing.quantity.trim()).replace(',', '.').toDoubleOrNull(),
                    unit = ing.unit.ifBlank { null },
                    name = ing.name,
                )
            }
        val instructions = e.instructions
            .filter { it.text.isNotBlank() }
            .mapIndexed { i, step ->
                InstructionEntity(step.id ?: UUID.randomUUID().toString(), e.id, i, step.text)
            }
        val nutrition = e.nutrition?.toEntity(e.id)
        return Entities(recipe, ingredients, instructions, nutrition, e.images)
    }
}

/** Run each ingredient name through the offline corrector, attaching review hints. */
private fun EditableRecipe.withCorrections(corrector: IngredientCorrection.Corrector): EditableRecipe =
    copy(
        ingredients = ingredients.map { ing ->
            if (ing.name.isBlank()) {
                ing
            } else {
                val r = corrector.correct(ing.name)
                ing.copy(name = r.name, suggestion = r.suggestion, autoCorrected = r.autoCorrected)
            }
        },
    )

private fun RecipeWithDetails.toEditable() = EditableRecipe(
    id = recipe.id,
    sourcePhotoId = recipe.sourcePhotoId,
    createdAt = recipe.createdAt,
    name = recipe.name.orEmpty(),
    servings = recipe.servings?.toString().orEmpty(),
    category = recipe.category.orEmpty(),
    // Null resolves to the default, so the chips show "lunch + dinner" pre-selected.
    mealTypes = MealTypes.fromStored(recipe.mealTypes),
    prepTime = DurationFormat.toHuman(recipe.prepTime),
    cookTime = DurationFormat.toHuman(recipe.cookTime),
    notes = recipe.notes.orEmpty(),
    cookbook = recipe.cookbook.orEmpty(),
    tags = recipe.tags,
    lastCookedAt = recipe.lastCookedAt,
    totalTime = recipe.totalTime,
    description = recipe.description,
    sourceUrl = recipe.sourceUrl,
    ingredients = ingredients.sortedBy { it.position }
        .map { EditableIngredient(it.id, Numbers.formatQuantity(it.quantity).orEmpty(), it.unit.orEmpty(), it.name) },
    instructions = instructions.sortedBy { it.position }.map { EditableInstruction(it.id, it.text) },
    nutrition = nutrition?.let {
        EditableNutrition(
            calories = it.calories.orEmpty(),
            protein = it.proteinContent.orEmpty(),
            fat = it.fatContent.orEmpty(),
            carbs = it.carbohydrateContent.orEmpty(),
            basis = it.basis.orEmpty(),
            fiber = it.fiberContent,
            sugar = it.sugarContent,
        )
    },
    images = images.sortedByDescending { it.isPrimary },
)

private fun EditableNutrition.toEntity(recipeId: String): NutritionEntity? {
    val hasAny = listOf(calories, protein, fat, carbs).any { it.isNotBlank() }
    if (!hasAny) return null
    return NutritionEntity(
        recipeId = recipeId,
        calories = calories.ifBlank { null },
        proteinContent = protein.ifBlank { null },
        fatContent = fat.ifBlank { null },
        carbohydrateContent = carbs.ifBlank { null },
        fiberContent = fiber,
        sugarContent = sugar,
        basis = basis.ifBlank { null },
    )
}
