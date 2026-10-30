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

package com.food.opencook.util

/**
 * Coarse "what's the main protein" classifier for the meal planner's variety scoring — so
 * three chicken dishes in one week read as monotonous even when their side ingredients differ.
 *
 * Keyword substring match (like [GroceryCategories]). The keyword lists live in the per-locale
 * arrays.xml resources (`protein_kw_*`) and are loaded by `LocalizedLists` as the **union across
 * every bundled content language**, so classification is content-language independent (an
 * English-locale device with German recipes still groups "Hähnchen" as poultry) and a newly
 * translated language grows the lists without touching this file. The canonical group keys stay
 * fixed internal identifiers (also shown, title-cased, in the meal-plan reason). Deliberately
 * heuristic: a wrong grouping only nudges a soft variety penalty, never excludes a recipe.
 *
 * The hardcoded DE+EN default keeps unit tests (and a not-yet-initialized process) working before
 * `LocalizedLists` swaps in the resource-backed lists.
 */
object ProteinGroups {

    /** Canonical group → substring keywords (lower-case). Order = priority; first hit wins. */
    private val DEFAULT_GROUPS: List<Pair<String, List<String>>> = listOf(
        "geflügel" to listOf("hähnchen", "haehnchen", "hühn", "huhn", "pute", "geflügel", "chicken", "turkey", "poultry"),
        "fisch" to listOf(
            "fisch", "lachs", "thunfisch", "forelle", "kabeljau", "seelachs", "scholle", "hering",
            "garnele", "scampi", "shrimp", "prawn", "salmon", "tuna", "trout",
        ),
        "hackfleisch" to listOf("hackfleisch", "hack", "mince"),
        "schwein" to listOf("schwein", "kasseler", "schnitzel", "schinken", "speck", "pork", "bacon"),
        "rind" to listOf("rinder", "rindfleisch", "steak", "beef"),
        "lamm" to listOf("lamm", "lamb"),
        "tofu" to listOf("tofu", "tempeh", "seitan"),
    )

    /** Active group rules; swapped at runtime by `LocalizedLists` to the unioned content languages. */
    @Volatile
    private var groups: List<Pair<String, List<String>>> = DEFAULT_GROUPS

    /** Replace the group keyword rules (called by `LocalizedLists` on language change). */
    fun setGroups(newGroups: List<Pair<String, List<String>>>) {
        if (newGroups.isNotEmpty()) groups = newGroups
    }

    /** Active groups — exposed so tests can snapshot and restore around [setGroups]. */
    val activeGroups: List<Pair<String, List<String>>> get() = groups

    /** Canonical protein group whose keyword occurs in [text], or null if none. */
    fun groupOf(text: String): String? {
        val t = TextFold.forMatch(text)
        return groups.firstOrNull { (_, keywords) -> keywords.any { it in t } }?.first
    }
}
