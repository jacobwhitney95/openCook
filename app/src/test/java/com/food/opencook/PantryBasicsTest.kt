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

import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.isBasic
import com.food.opencook.sync.Hlc
import com.food.opencook.sync.MessageCodec
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.sync.PantryMessageEncoder
import com.food.opencook.sync.Stamper
import com.food.opencook.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Always in stock" (PantryItemEntity.basic): who decides, what syncs, what cooking uses up. */
class PantryBasicsTest {

    private class Pantry(vararg items: PantryItemEntity) : PantryDao {
        val items = items.toMutableList()
        override fun observeAll(): Flow<List<PantryItemEntity>> = throw NotImplementedError()
        override suspend fun getById(id: String) = items.find { it.id == id }
        override suspend fun findByName(name: String) = items.find { it.name.equals(name, ignoreCase = true) }
        override suspend fun allNames() = items.map { it.name }
        override suspend fun getAll() = items.toList()
        override suspend fun upsert(item: PantryItemEntity) { items.removeAll { it.id == item.id }; items += item }
        override suspend fun deleteById(id: String) { items.removeAll { it.id == id } }
    }

    /** Keeps what the repository records, so the synced fields can be checked. */
    private class Messages : MessageDao {
        val recorded = mutableListOf<MessageEntity>()
        override suspend fun insert(message: MessageEntity) { recorded += message }
        override suspend fun insertAll(messages: List<MessageEntity>) { recorded += messages }
        override suspend fun maxTimestamp(dataset: String, rowId: String, column: String): String? = null
        override suspend fun maxTimestamp(): String? = null
        override suspend fun all() = recorded.toList()
        override suspend fun since(cursor: String) = emptyList<MessageEntity>()
        override suspend fun forRow(dataset: String, rowId: String) = emptyList<MessageEntity>()
        override suspend fun hasValue(dataset: String, rowId: String, column: String, value: String) = false
        override suspend fun existingTimestamps(timestamps: List<String>) = emptyList<String>()
        override suspend fun count() = recorded.size
    }

    private fun item(name: String, basic: Boolean? = null) = PantryItemEntity(name, name, 0, 0, basic)

    private fun repo(pantry: Pantry, messages: Messages = Messages()): PantryRepository {
        var n = 0L
        val stamper = object : Stamper { override suspend fun stamp() = Hlc(1_000 + n++, 0, "T") }
        val trigger = object : SyncTrigger { override fun requestSync() {} }
        return PantryRepository(pantry, MessageRecorder(messages, stamper, trigger))
    }

    @Test
    fun `the household's choice beats the built-in staples list`() {
        assertTrue(item("Salz").isBasic())          // undecided → built-in list
        assertFalse(item("Kokosmilch").isBasic())
        assertFalse(item("Salz", basic = false).isBasic())
        assertTrue(item("Kokosmilch", basic = true).isBasic())
    }

    @Test
    fun `cooking uses up regular stock but never what is always in stock`() = runTest {
        val pantry = Pantry(
            item("Salz"),                          // built-in basic
            item("Kokosmilch"),                    // regular stock
            item("Parmesan", basic = true),        // made a basic by the household
            item("Mehl", basic = false),           // taken out of the basics
        )
        repo(pantry).consume(listOf("Salz", "Kokosmilch", "Parmesan", "Mehl"))
        assertEquals(listOf("Salz", "Parmesan"), pantry.items.map { it.name })
    }

    @Test
    fun `dragging into always-in-stock is recorded and synced`() = runTest {
        val pantry = Pantry(item("Kokosmilch"))
        val messages = Messages()
        repo(pantry, messages).setBasic("Kokosmilch", true)
        assertEquals(true, pantry.items.single().basic)
        assertEquals("true", messages.recorded.single { it.column == "basic" }.value)
    }

    @Test
    fun `dragging where it already is records nothing`() = runTest {
        val messages = Messages()
        repo(Pantry(item("Salz")), messages).setBasic("Salz", true)  // already a basic
        assertTrue(messages.recorded.isEmpty())
    }

    @Test
    fun `an undecided item doesn't sync a basic flag`() {
        // Must not out-vote (per-field LWW) a decision another device already made.
        assertTrue(PantryMessageEncoder.encode(item("Salz")).none { it.column == "basic" })
        assertEquals("false", PantryMessageEncoder.encode(item("Salz", basic = false)).single { it.column == "basic" }.value)
    }

    @Test
    fun `the basic flag decodes as a tri-state`() {
        assertEquals(true, MessageCodec.decodeNullableBoolean("true"))
        assertEquals(false, MessageCodec.decodeNullableBoolean("false"))
        assertNull(MessageCodec.decodeNullableBoolean(null))
        assertNull(MessageCodec.decodeNullableBoolean("null"))
    }
}
