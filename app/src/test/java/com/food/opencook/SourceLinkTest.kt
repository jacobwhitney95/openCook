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

import com.food.opencook.sync.RecipeMessageEncoder
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.util.SourceLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub #31: the link to an imported recipe's page. */
class SourceLinkTest {

    @Test
    fun `the site name is the host without www`() {
        assertEquals("chefkoch.de", SourceLink.host("https://www.chefkoch.de/rezepte/123/x.html"))
        assertEquals("bbc.co.uk", SourceLink.host("http://bbc.co.uk/food"))
    }

    @Test
    fun `only http and https count as a link`() {
        assertTrue(SourceLink.isWebUrl("https://example.org/x"))
        assertFalse(SourceLink.isWebUrl("ftp://example.org/x"))
        assertFalse(SourceLink.isWebUrl("javascript:alert(1)"))
        assertFalse(SourceLink.isWebUrl("recipe-123"))
        assertNull(SourceLink.host(null))
    }

    @Test
    fun `the source page syncs`() {
        val recipe = RecipeEntity(id = "r", name = "R", sourceUrl = "https://example.org/r", createdAt = 0, updatedAt = 0)
        val field = RecipeMessageEncoder.encode(recipe, emptyList(), emptyList(), null).single { it.column == "sourceUrl" }
        assertEquals("\"https://example.org/r\"", field.value)
    }
}
