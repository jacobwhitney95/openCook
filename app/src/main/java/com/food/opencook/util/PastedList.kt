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
 * Splits a list pasted into the recipe editor — an ingredient list or the steps copied off a
 * web page — into its entries (GitHub #28). Pure, so the rules are unit-testable; the editor
 * decides *when* text counts as a pasted list.
 */
object PastedList {

    /** List markers a web page or a note brings along: •, -, *, –, … */
    private val BULLET = Regex("""^[•·▪◦‣*–—-]\s*""")

    /**
     * A step's own number, which the app adds itself: "1.", "2)", "(3)", "Step 4:",
     * "Schritt 5", "١.", … A number is only taken when it's clearly a marker — followed by "." or
     * ")" and a space — so "2 Eier verquirlen" or "1.5 l Wasser" keep theirs.
     */
    private val STEP_NUMBER = Regex(
        """^(?:(?:step|schritt|étape|etape|paso|الخطوة|خطوة)\s*\p{Nd}+\s*[.:)\-–]?|\p{Nd}+[.)](?=\s)|\(\p{Nd}+\))\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** A line that is nothing but a step number ("3"), as some sites lay steps out. */
    private val NUMBER_ONLY = Regex("""^\p{Nd}+[.)]?$""")

    private val BLANK_LINE = Regex("""\n\s*\n""")

    /** One ingredient per non-blank line, list markers removed (amounts stay — "2 Eier"). */
    fun ingredientLines(text: String): List<String> =
        text.lines().map { it.trim().replace(BULLET, "").trim() }.filter { it.isNotEmpty() }

    /**
     * The steps: blocks separated by blank lines if there are any — how most pages copy, a
     * paragraph per step — else one per line. Markers and step numbers are removed; a
     * block's own line breaks become spaces.
     */
    fun steps(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        val blocks = if (BLANK_LINE.containsMatchIn(normalized)) normalized.split(BLANK_LINE) else normalized.lines()
        return blocks.map { block ->
            block.lines()
                .map { it.trim().replace(BULLET, "").replace(STEP_NUMBER, "").trim() }
                .filterNot { it.isEmpty() || NUMBER_ONLY.matches(it) }
                .joinToString(" ")
        }.filter { it.isNotEmpty() }
    }

    /**
     * Whether a step field's change from [old] to [new] is a pasted list rather than typing:
     * several characters at once that bring a line break along (one typed Enter adds a lone
     * "\n" and stays a line break) and make at least two steps.
     */
    fun isPastedSteps(old: String, new: String): Boolean {
        if (new.length - old.length < 2) return false
        val prefix = old.commonPrefixWith(new).length
        val suffix = old.drop(prefix).commonSuffixWith(new.drop(prefix)).length
        val inserted = new.substring(prefix, new.length - suffix)
        return '\n' in inserted && steps(new).size >= 2
    }
}
