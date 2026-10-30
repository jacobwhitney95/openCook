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
 * Script-level folding for *comparisons* — never applied to what is stored or shown.
 *
 * Kotlin's `\d` and `toDoubleOrNull()` only know ASCII digits, while an Arabic keyboard or
 * recipe page writes "٤٠٠ غ". And Arabic spells one word several ways (hamza on the alef or
 * not, final ى/ي and ة/ه, optional vowel marks) and glues the article "ال" onto the noun, so
 * "الأرز" and "ارز" must compare equal. Latin text passes through unchanged.
 */
object TextFold {

    /** Arabic vowel marks (harakat, superscript alef) and the tatweel stretch character. */
    private val ARABIC_MARKS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")

    /** The definite article at the start of a word, when a real word (2+ letters) follows. */
    private val ARABIC_ARTICLE = Regex("(?<!\\p{L})ال(?=\\p{L}{2})")

    /**
     * Arabic-Indic (٠–٩) and Eastern Arabic-Indic (۰–۹) digits → ASCII, and the Arabic
     * decimal separator ٫ → ".". One char for one char, so match ranges found on the folded
     * text still index the original.
     */
    fun digits(s: String): String {
        if (s.none { it in '٠'..'٩' || it in '۰'..'۹' || it == '٫' }) return s
        return buildString(s.length) {
            for (c in s) append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫' -> '.'
                    else -> c
                },
            )
        }
    }

    /** Lower-case [s] and fold digits and Arabic spelling variants, for word-list matching. */
    fun forMatch(s: String): String {
        val t = digits(s.lowercase())
        if (t.none { it in '؀'..'ۿ' }) return t
        return t.replace(ARABIC_MARKS, "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace(ARABIC_ARTICLE, "")
    }

    /** True if [s] is written in Arabic script — its head noun comes first, adjectives after. */
    fun isArabic(s: String): Boolean = s.any { it in '؀'..'ۿ' }
}
