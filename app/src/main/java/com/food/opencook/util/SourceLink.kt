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

import java.net.URI

/** A recipe's source page (RecipeEntity.sourceUrl): which addresses count, and how to show one. */
object SourceLink {

    /** An http(s) address with a host — the only kind worth offering as a link. */
    fun isWebUrl(url: String?): Boolean = host(url) != null

    /** "chefkoch.de" for "https://www.chefkoch.de/rezepte/…" — the short form shown as the link. */
    fun host(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        return uri.host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }
}
