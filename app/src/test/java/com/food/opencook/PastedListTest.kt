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
package com.food.opencook

import com.food.opencook.util.PastedList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub #28: an ingredient list or the steps pasted in one go, split into rows. */
class PastedListTest {

    @Test
    fun `ingredient lines drop markers and blank lines but keep amounts`() {
        val pasted = """
            • 400 g Spaghetti
            - 2 Eier

            * 1 Prise Salz
              100 g Parmesan, gerieben
        """.trimIndent()
        assertEquals(
            listOf("400 g Spaghetti", "2 Eier", "1 Prise Salz", "100 g Parmesan, gerieben"),
            PastedList.ingredientLines(pasted),
        )
    }

    @Test
    fun `steps split by line and lose their numbers`() {
        val pasted = "1. Wasser aufkochen.\n2) Nudeln garen.\nSchritt 3: Abgießen.\nStep 4 Serve."
        assertEquals(
            listOf("Wasser aufkochen.", "Nudeln garen.", "Abgießen.", "Serve."),
            PastedList.steps(pasted),
        )
    }

    @Test
    fun `blank lines make paragraphs the steps`() {
        val pasted = "Den Ofen vorheizen.\nDie Form fetten.\n\nTeig einfüllen\nund backen."
        assertEquals(
            listOf("Den Ofen vorheizen. Die Form fetten.", "Teig einfüllen und backen."),
            PastedList.steps(pasted),
        )
    }

    @Test
    fun `a number on its own line is dropped, a real amount kept`() {
        val pasted = "1\nEier trennen.\n\n2\n2 Eier verquirlen, 1.5 l Milch erhitzen."
        assertEquals(
            listOf("Eier trennen.", "2 Eier verquirlen, 1.5 l Milch erhitzen."),
            PastedList.steps(pasted),
        )
    }

    @Test
    fun `typing Enter in a step is not a paste`() {
        assertFalse(PastedList.isPastedSteps("Teig kneten", "Teig kneten\n"))
        assertFalse(PastedList.isPastedSteps("Teig kneten\n", "Teig kneten\nu"))
    }

    @Test
    fun `pasting several lines into a step is`() {
        assertTrue(PastedList.isPastedSteps("", "Ofen vorheizen.\nTeig kneten."))
        assertTrue(PastedList.isPastedSteps("Erst ", "Erst Ofen vorheizen.\nTeig kneten."))
        // One line pasted, even with a trailing break, stays a single step.
        assertFalse(PastedList.isPastedSteps("", "Ofen vorheizen.\n"))
    }

    @Test
    fun `arabic step numbers are removed`() {
        assertEquals(listOf("سخّن الفرن", "اخلط الدقيق"), PastedList.steps("١. سخّن الفرن\n٢. اخلط الدقيق"))
        assertEquals(listOf("قطّع البصل"), PastedList.steps("الخطوة ١: قطّع البصل"))
    }
}
