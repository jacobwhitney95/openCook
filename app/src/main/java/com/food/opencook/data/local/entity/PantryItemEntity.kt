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

package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Something the household keeps in stock; used to skip already-owned ingredients.
 *
 * [basic]: whether it is a *basic* — always in the house, so cooking never uses it up and the
 * pantry lists it in its collapsed "basics" group. The household decides by dragging an item
 * into or out of that group; null means it never has, and the built-in staples list decides
 * (see [com.food.opencook.repository.isBasic]) — which is how existing and pre-seeded items
 * land in the right group without a data migration.
 */
@Entity(tableName = "pantry_items")
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val basic: Boolean? = null,
)
