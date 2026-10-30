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

package com.food.opencook.sync

import com.food.opencook.data.local.entity.PantryItemEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Projects a pantry item into per-field changes for the sync log. */
object PantryMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.PANTRY

    fun encode(item: PantryItemEntity): List<FieldChange> = listOfNotNull(
        FieldChange(d, item.id, "name", json.encodeToString(String.serializer(), item.name)),
        // Only once the household has decided: an unset flag must never out-vote (per-field
        // LWW) a decision another device made — nor would an older app know the field.
        item.basic?.let { FieldChange(d, item.id, "basic", it.toString()) },
        FieldChange(d, item.id, SyncDatasets.COLUMN_DELETED, "false"),
    )

    fun tombstone(itemId: String): List<FieldChange> =
        listOf(FieldChange(d, itemId, SyncDatasets.COLUMN_DELETED, "true"))
}
