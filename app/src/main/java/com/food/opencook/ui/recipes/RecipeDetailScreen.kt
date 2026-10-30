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

package com.food.opencook.ui.recipes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import coil3.compose.AsyncImage
import com.food.opencook.R
import com.food.opencook.data.export.ExportFormat
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.ui.components.CookedCelebration
import com.food.opencook.ui.components.HeartBurstFull
import com.food.opencook.ui.components.LikedBadge
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.DateLabels
import com.food.opencook.util.DurationFormat
import com.food.opencook.util.Numbers
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.food.opencook.util.SourceLink
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.platform.LocalUriHandler


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val recipeMissing by viewModel.recipeMissing.collectAsStateWithLifecycle()
    val baseUrl by viewModel.serverBaseUrl.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val cooked by viewModel.cooked.collectAsStateWithLifecycle()
    val cookedCount by viewModel.cookedCount.collectAsStateWithLifecycle()
    val confirmsOtherDay by viewModel.confirmsOtherDay.collectAsStateWithLifecycle()
    val targetServings by viewModel.targetServings.collectAsStateWithLifecycle()

    // Screen-filling celebrations, fired from the user's TAP (not from the flow loading its
    // value on open) and only when the toggle turns ON.
    var heartTick by remember { mutableIntStateOf(0) }
    var cookedTick by remember { mutableIntStateOf(0) }
    val onToggleLiked: () -> Unit = { if (!liked) heartTick++; viewModel.toggleLiked() }
    val onToggleCooked: () -> Unit = { if (!cooked) cookedTick++; viewModel.toggleCooked() }

    // Keep the screen awake while a recipe is open — you cook straight from this view.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // A time in a step ("10 Minuten") starts a countdown in the clock app. Named "Step 3 ·
    // dish": the number is what the open recipe shows (the screen stays on while cooking),
    // and it leads so a long dish name can't push it out of the clock's line.
    val context = LocalContext.current
    val timerSetFormat = stringResource(R.string.recipe_timer_set)
    val timerLabelFormat = stringResource(R.string.recipe_timer_label)
    val noTimerAppMessage = stringResource(R.string.recipe_timer_no_app)
    val openClockLabel = stringResource(R.string.recipe_timer_open_clock)
    val onStepTimer: (seconds: Int, step: Int, recipeName: String) -> Unit = { seconds, step, recipeName ->
        scope.launch {
            if (!startClockTimer(context, seconds, timerLabelFormat.format(step, recipeName))) {
                snackbarHostState.showSnackbar(noTimerAppMessage)
                return@launch
            }
            // Which step's timer this is, and one tap to the clock to stop or extend it.
            // An action alone would keep the snackbar up for good, hence the duration.
            val result = snackbarHostState.showSnackbar(
                message = timerSetFormat.format(step, DurationFormat.toHuman("PT${seconds}S")),
                actionLabel = openClockLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) showClockTimers(context)
        }
    }
    // A timer of one's own choosing (the recipe gives no time, or you want another): the
    // clock's own "new timer" screen, already named after the dish.
    val onOwnTimer: () -> Unit = {
        if (!startClockTimer(context, null, recipe?.recipe?.name.orEmpty())) {
            scope.launch { snackbarHostState.showSnackbar(noTimerAppMessage) }
        }
    }
    val addedMessage = stringResource(R.string.shopping_added)
    val alreadyOnListMessage = stringResource(R.string.shopping_already_on_list)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var leaving by remember { mutableStateOf(false) }
    val leave: () -> Unit = { if (!leaving) { leaving = true; onBack() } }
    LaunchedEffect(recipeMissing) { if (recipeMissing == true) leave() }
    var showPlanSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    // One CreateDocument launcher per format: the MIME type is fixed per contract.
    val exportDoneMessage = stringResource(R.string.recipe_export_done)
    val exportFailedMessage = stringResource(R.string.recipe_export_failed)
    val onExportResult: (Boolean) -> Unit = { ok ->
        scope.launch { snackbarHostState.showSnackbar(if (ok) exportDoneMessage else exportFailedMessage) }
    }
    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.MARKDOWN.mime),
    ) { uri -> if (uri != null) viewModel.export(uri, ExportFormat.MARKDOWN, onExportResult) }
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.SCHEMA_ORG_JSON.mime),
    ) { uri -> if (uri != null) viewModel.export(uri, ExportFormat.SCHEMA_ORG_JSON, onExportResult) }
    val planAddedFormat = stringResource(R.string.recipe_plan_added)
    val planned by viewModel.plannedDishes.collectAsStateWithLifecycle()
    val plannedMeals by viewModel.plannedMeals.collectAsStateWithLifecycle()
    val multiDishMeals by viewModel.multiDishMeals.collectAsStateWithLifecycle()
    val recipeMealTypes by viewModel.recipeMealTypes.collectAsStateWithLifecycle()

    // Cooked-off-plan: today had a different dish planned → swap happens automatically; the
    // snackbar only informs (where the displaced dish went) and offers an undo.
    val lastSwap by viewModel.lastSwap.collectAsStateWithLifecycle()
    val movedTemplate = stringResource(R.string.recipe_cook_moved)
    val removedTemplate = stringResource(R.string.recipe_cook_removed)
    val undoLabel = stringResource(R.string.undo)
    val dayFmt = remember { DateLabels.weekdayDayMonth() }
    LaunchedEffect(lastSwap) {
        val s = lastSwap ?: return@LaunchedEffect
        val movedLabel = s.movedTo?.let { runCatching { LocalDate.parse(it).format(dayFmt) }.getOrNull() }
        val msg = if (movedLabel != null) movedTemplate.format(s.displacedName, movedLabel)
            else removedTemplate.format(s.displacedName)
        val res = snackbarHostState.showSnackbar(message = msg, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
        if (res == SnackbarResult.ActionPerformed) viewModel.undoSwap(s) else viewModel.clearLastSwap()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.recipe_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recipe_delete_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; viewModel.delete(leave) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(recipe?.recipe?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { showExportSheet = true }, enabled = recipe != null) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.recipe_export))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.recipe_edit))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.recipe_delete))
                    }
                },
            )
        },
    ) { innerPadding ->
        val data = recipe
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val model = imageModelFor(data.images, baseUrl)
        val onAddToShopping: () -> Unit = {
            viewModel.addToShoppingList { added ->
                scope.launch {
                    snackbarHostState.showSnackbar(if (added) addedMessage else alreadyOnListMessage)
                }
            }
        }
        val onPlan: () -> Unit = { showPlanSheet = true }
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            // Tablet landscape: ingredients (the reference you glance at) beside the steps (the
            // focus while cooking), each scrolling on its own — no more scroll-pingpong. Narrow
            // widths (phones, portrait) keep the familiar single scrolling column.
            if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Name lives in the top bar already, so the left pane starts with the image.
                        ImageHeader(model, liked, cooked)
                        RecipeMeta(data, cookedCount)
                        IngredientsSection(data, targetServings, viewModel::setServings)
                        // Tags are secondary while cooking → bottom of the reference column.
                        TagChips(data.recipe.tags)
                    }
                    VerticalDivider()
                    Column(
                        Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Quick actions sit above the steps (the cooking focus), reachable without
                        // scrolling through the ingredients on the left.
                        ActionButtons(data, cooked, confirmsOtherDay, liked, onAddToShopping, onPlan, onToggleCooked, onToggleLiked, onOwnTimer)
                        InstructionsSection(data, onStepTimer)
                        NotesSection(data)
                        NutritionSection(data)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImageHeader(model, liked, cooked)
                    Text(data.recipe.name ?: "—", style = MaterialTheme.typography.headlineSmall)
                    RecipeMeta(data, cookedCount)
                    TagChips(data.recipe.tags)
                    ActionButtons(data, cooked, confirmsOtherDay, liked, onAddToShopping, onPlan, onToggleCooked, onToggleLiked, onOwnTimer)
                    IngredientsSection(data, targetServings, viewModel::setServings)
                    InstructionsSection(data, onStepTimer)
                    NotesSection(data)
                    NutritionSection(data)
                }
            }
        }
    }
        // Screen-filling celebrations over the whole screen, so icons enter/leave off-screen
        // (no visible clip line at the content edges). Non-interactive.
        HeartBurstFull(heartTick, MaterialTheme.colorScheme.error, Modifier.fillMaxSize())
        CookedCelebration(
            cookedTick,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Modifier.fillMaxSize(),
        )
    }

    if (showExportSheet) {
        ExportSheet(
            onDismiss = { showExportSheet = false },
            onMarkdown = {
                showExportSheet = false
                exportMarkdownLauncher.launch(viewModel.suggestedFileName(ExportFormat.MARKDOWN))
            },
            onJson = {
                showExportSheet = false
                exportJsonLauncher.launch(viewModel.suggestedFileName(ExportFormat.SCHEMA_ORG_JSON))
            },
        )
    }

    if (showPlanSheet) {
        AddToMealPlanSheet(
            weeks = viewModel.planWeekDates,
            planned = planned,
            plannedMeals = plannedMeals,
            recipeMealTypes = recipeMealTypes,
            onAssign = viewModel::assignToMealPlan,
            onReplace = viewModel::replaceOnMealPlan,
            multiDish = multiDishMeals,
            onDismiss = { showPlanSheet = false },
            onAssigned = { dayLabel ->
                scope.launch { snackbarHostState.showSnackbar(planAddedFormat.format(dayLabel)) }
            },
        )
    }
}

/** Format picker for the single-recipe export — one row per file format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(onDismiss: () -> Unit, onMarkdown: () -> Unit, onJson: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.recipe_export_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        ExportRow(
            icon = Icons.Outlined.Description,
            title = R.string.recipe_export_markdown_title,
            description = R.string.recipe_export_markdown_desc,
            onClick = onMarkdown,
        )
        ExportRow(
            icon = Icons.Outlined.DataObject,
            title = R.string.recipe_export_json_title,
            description = R.string.recipe_export_json_desc,
            onClick = onJson,
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun ExportRow(
    icon: ImageVector,
    @androidx.annotation.StringRes title: Int,
    @androidx.annotation.StringRes description: Int,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The dish image (or a warm placeholder) with the liked/cooked status badges. */
@Composable
private fun ImageHeader(model: Any?, liked: Boolean, cooked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        // Badges bounce in when the recipe gets liked / cooked, so the new status catches the eye.
        AnimatedVisibility(
            visible = liked,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) { LikedBadge() }
        AnimatedVisibility(
            visible = cooked,
            enter = scaleIn(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                transformOrigin = TransformOrigin(1f, 0f), // grow out of the top-right corner
            ) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) { CookedRibbon() }
    }
}

/** Shopping / plan / cooked / like quick actions. */
@Composable
private fun ActionButtons(
    data: RecipeWithDetails,
    cooked: Boolean,
    confirmsOtherDay: Boolean,
    liked: Boolean,
    onAddToShopping: () -> Unit,
    onPlan: () -> Unit,
    onToggleCooked: () -> Unit,
    onToggleLiked: () -> Unit,
    onTimer: () -> Unit,
) {
    // Local spring-pop on the icon, fired from the tap (not the flow) and only when turning ON,
    // so it never plays on opening an already-liked/cooked recipe.
    val scope = rememberCoroutineScope()
    val likeScale = remember { Animatable(1f) }
    val cookedScale = remember { Animatable(1f) }
    val onCook: () -> Unit = {
        if (!cooked) scope.launch { cookedScale.snapTo(0.7f); cookedScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
        onToggleCooked()
    }
    val onLike: () -> Unit = {
        if (!liked) scope.launch { likeScale.snapTo(0.55f); likeScale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow)) }
        onToggleLiked()
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (data.ingredients.isNotEmpty()) {
            // Neutral (not green) — "add to the shopping list".
            OutlinedIconButton(onClick = onAddToShopping) {
                Icon(Icons.Outlined.AddShoppingCart, contentDescription = stringResource(R.string.shopping_add_from_recipe))
            }
        }
        // Assign this recipe to a day in the current or next week.
        OutlinedIconButton(onClick = onPlan) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.recipe_add_to_plan))
        }
        // A timer of your own — for when the recipe names no time, or you want another one.
        // Cooking comes before "cooked": the actions lead, the two coloured states close the row.
        OutlinedIconButton(onClick = onTimer) {
            Icon(Icons.Outlined.Timer, contentDescription = stringResource(R.string.recipe_timer_own))
        }
        // Toggle "has been cooked" — green when confirmed (also shows as the image ribbon).
        val cookedIcon = @Composable {
            Icon(
                Icons.Outlined.Restaurant,
                // "Cooked today" would be a lie when confirming Monday's planned meal.
                contentDescription = stringResource(
                    when {
                        cooked && confirmsOtherDay -> R.string.recipe_cooked_badge
                        cooked -> R.string.recipe_cooked_marked
                        else -> R.string.recipe_cooked_mark
                    },
                ),
                modifier = Modifier.scale(cookedScale.value),
            )
        }
        if (cooked) {
            FilledTonalIconButton(
                onClick = onCook,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { cookedIcon() }
        } else {
            OutlinedIconButton(onClick = onCook) { cookedIcon() }
        }
        // Like toggle; heart stays red when liked.
        if (liked) {
            FilledTonalIconButton(
                onClick = onLike,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.recipe_like), modifier = Modifier.scale(likeScale.value))
            }
        } else {
            OutlinedIconButton(onClick = onLike) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = stringResource(R.string.recipe_like), modifier = Modifier.scale(likeScale.value))
            }
        }
    }
}

/** Ingredients with the portions stepper that scales the displayed amounts. */
@Composable
private fun IngredientsSection(data: RecipeWithDetails, targetServings: Int?, onServings: (Int) -> Unit) {
    if (data.ingredients.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_ingredients), style = MaterialTheme.typography.titleMedium)

        // Portions stepper — scales the displayed amounts (only if servings is known).
        val baseServings = data.recipe.servings
        val effectiveServings = targetServings ?: baseServings
        if (baseServings != null && baseServings > 0 && effectiveServings != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onServings(effectiveServings - 1) }, enabled = effectiveServings > 1) { Text("−") }
                Text(
                    "$effectiveServings ${stringResource(R.string.settings_household_size_label)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = { onServings(effectiveServings + 1) }) { Text("+") }
            }
        }
        val factor = Numbers.scaleFor(baseServings, effectiveServings ?: (baseServings ?: 1))
        data.ingredients.sortedBy { it.position }.forEach { ing ->
            val q = Numbers.scaleQuantity(ing.quantity, factor)
            // Larger text: you read ingredients from an arm's length while cooking.
            Text("• " + Numbers.displayIngredient(q, ing.unit, ing.name), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Numbered preparation steps, set in a comfortable cook-from-screen size. */
@Composable
private fun InstructionsSection(
    data: RecipeWithDetails,
    onTimer: (seconds: Int, step: Int, recipeName: String) -> Unit,
) {
    if (data.instructions.isEmpty()) return
    val recipeName = data.recipe.name.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_instructions), style = MaterialTheme.typography.titleMedium)
        data.instructions.sortedBy { it.position }.forEachIndexed { i, step ->
            StepText(number = i + 1, text = step.text) { seconds -> onTimer(seconds, i + 1, recipeName) }
        }
    }
}

@Composable
private fun NotesSection(data: RecipeWithDetails) {
    val notes = data.recipe.notes?.takeIf { it.isNotBlank() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_notes), style = MaterialTheme.typography.titleMedium)
        Text(notes, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NutritionSection(data: RecipeWithDetails) {
    val n = data.nutrition ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_nutrition), style = MaterialTheme.typography.titleMedium)
        n.basis?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        listOfNotNull(
            n.calories?.let { stringResource(R.string.nutrition_calories_value, it) },
            n.proteinContent?.let { stringResource(R.string.nutrition_protein_value, it) },
            n.fatContent?.let { stringResource(R.string.nutrition_fat_value, it) },
            n.carbohydrateContent?.let { stringResource(R.string.nutrition_carbs_value, it) },
        ).forEach { Text(it) }
    }
}

/**
 * The recipe's tags. A web import easily brings twenty ("30 minute dinners", "cheap pasta"…),
 * which used to push the actions and the ingredients a screen down — so they start as two
 * lines, and a text button (not a chip: it must not read as one more tag) shows the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: String?) {
    val list = tags?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (list.isEmpty()) return
    var expanded by rememberSaveable(tags) { mutableStateOf(false) }
    val tagChip: @Composable (String) -> Unit = { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
    if (expanded) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            list.forEach { tagChip(it) }
            TagToggle(stringResource(R.string.recipe_tags_less), Icons.Outlined.ExpandLess) { expanded = false }
        }
    } else {
        LimitedChips(
            count = list.size,
            maxLines = 2,
            chip = { tagChip(list[it]) },
            more = { hidden ->
                TagToggle(stringResource(R.string.recipe_tags_more, hidden), Icons.Outlined.ExpandMore) { expanded = true }
            },
        )
    }
}

/** "+45 more ▾" / "Less ▴" — a text button in the primary colour, so it reads as a control. */
@Composable
private fun TagToggle(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label)
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/**
 * As many [chip]s as fit on [maxLines] lines, with [more] after them for the rest when some
 * don't. (Compose's own FlowRow overflow does this but is deprecated and unmaintained.)
 */
@Composable
private fun LimitedChips(
    count: Int,
    maxLines: Int,
    chip: @Composable (Int) -> Unit,
    more: @Composable (hidden: Int) -> Unit,
) {
    SubcomposeLayout { constraints ->
        val gap = 8.dp.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val chips = (0 until count).map { i -> subcompose("chip$i") { chip(i) }.first().measure(loose) }
        // Room for the widest "+N" there can be, so the chips never crowd it out.
        val moreWidth = subcompose("probe") { more(count) }.first().measure(loose).width

        // Lay the chips into lines; the last allowed line keeps room for the "more" button.
        val lines = mutableListOf(mutableListOf<Int>())
        var x = 0
        var shown = 0
        while (shown < count) {
            val w = chips[shown].width
            val lastLine = lines.size == maxLines
            val reserve = if (lastLine && shown < count - 1) gap + moreWidth else 0
            if (lines.last().isNotEmpty() && x + w + reserve > width) {
                if (lastLine) break
                lines += mutableListOf<Int>()
                x = 0
                continue
            }
            lines.last() += shown
            x += w + gap
            shown++
        }
        val morePlaceable = if (shown < count) subcompose("more") { more(count - shown) }.first().measure(loose) else null

        val lineHeights = lines.mapIndexed { i, line ->
            val chipsHeight = line.maxOfOrNull { chips[it].height } ?: 0
            if (i == lines.lastIndex && morePlaceable != null) maxOf(chipsHeight, morePlaceable.height) else chipsHeight
        }
        layout(width, lineHeights.sum()) {
            var y = 0
            lines.forEachIndexed { i, line ->
                var px = 0
                line.forEach { idx ->
                    val p = chips[idx]
                    p.placeRelative(px, y + (lineHeights[i] - p.height) / 2)
                    px += p.width + gap
                }
                if (i == lines.lastIndex && morePlaceable != null) {
                    morePlaceable.placeRelative(px, y + (lineHeights[i] - morePlaceable.height) / 2)
                }
                y += lineHeights[i]
            }
        }
    }
}

/**
 * Meta line under the title: servings (with a people icon), then the source cookbook and
 * the page it came from, then prep/cook times. Wraps to a second line on narrow screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeMeta(data: RecipeWithDetails, cookedCount: Int = 0) {
    val r = data.recipe
    val servings = r.recipeYield ?: r.servings?.let { stringResource(R.string.wizard_summary_servings, it.toString()) }
    val times = listOfNotNull(
        r.prepTime?.let { DurationFormat.toHuman(it) }?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.recipe_meta_prep, it) },
        r.cookTime?.let { DurationFormat.toHuman(it) }?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.recipe_meta_cook, it) },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        servings?.let { MetaItem(Icons.Outlined.Group, it) }
        r.cookbook?.takeIf { it.isNotBlank() }?.let { MetaItem(Icons.AutoMirrored.Outlined.MenuBook, it) }
        r.sourceUrl?.let { url -> SourceLink.host(url)?.let { SourceLinkItem(url, it) } }
        times?.let { MetaItem(Icons.Outlined.Schedule, it) }
        r.lastCookedAt?.let { lastCookedLabel(it) }?.let { recency ->
            // With a history behind it the count leads and the date becomes the qualifier —
            // "5× gekocht · zuletzt vor 3 Tagen". Without one the line is exactly as before.
            MetaItem(
                Icons.Outlined.Restaurant,
                if (cookedCount > 0) {
                    val times = pluralStringResource(R.plurals.recipe_cooked_times, cookedCount, cookedCount)
                    "$times · ${stringResource(R.string.recipe_cooked_last_short, recency)}"
                } else {
                    "${stringResource(R.string.recipe_last_cooked_prefix)}: $recency"
                },
            )
        }
    }
}

/** Human "last cooked" recency: today / yesterday / N days / N weeks ago. Null if unparseable. */
@Composable
private fun lastCookedLabel(iso: String): String? {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(date, LocalDate.now()).coerceAtLeast(0)
    return when {
        days == 0L -> stringResource(R.string.recipe_cooked_today)
        days == 1L -> stringResource(R.string.recipe_cooked_yesterday)
        days < 7L -> pluralStringResource(R.plurals.recipe_cooked_days_ago, days.toInt(), days.toInt())
        days < 14L -> stringResource(R.string.recipe_cooked_week_ago)
        else -> {
            val weeks = (days / 7L).toInt()
            pluralStringResource(R.plurals.recipe_cooked_weeks_ago, weeks, weeks)
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The page an imported recipe came from, as its site name ("chefkoch.de"); opens it. */
@Composable
private fun SourceLinkItem(url: String, host: String) {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier.clickable(onClickLabel = stringResource(R.string.recipe_open_source)) {
            runCatching { uriHandler.openUri(url) }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(host, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Diagonal green corner banner shown on the image (top-right) once the recipe is cooked.
 *  The bar is centred on the corner diagonal (symmetric offset) and the label is centred
 *  within the bar, so "Gekocht" sits in the middle of the visible banner. */
@Composable
private fun CookedRibbon(modifier: Modifier = Modifier) {
    Box(modifier.size(130.dp).clipToBounds()) {
        Text(
            text = stringResource(R.string.recipe_cooked_badge),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 32.dp, y = (-32).dp)
                .rotate(45f)
                .width(200.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(vertical = 6.dp),
        )
    }
}

// LikedBadge now lives in ui/components — the recipe cards in the list wear the same mark.
