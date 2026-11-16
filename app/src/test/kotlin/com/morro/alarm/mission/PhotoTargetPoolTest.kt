package com.morro.alarm.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The object pool is persisted by id, so a duplicate or dangling id silently shrinks the pool or
 * makes a stored selection unreadable. These tests pin the shape of the data the alarm depends on.
 */
class PhotoTargetPoolTest {

    @Test
    fun `pool has thirty items`() {
        assertEquals(30, PhotoTargetPool.ITEMS.size)
    }

    @Test
    fun `ids are unique`() {
        val ids = PhotoTargetPool.ITEMS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `display names are unique and non blank`() {
        val names = PhotoTargetPool.ITEMS.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.isNotBlank() })
    }

    @Test
    fun `ids look like stable storage keys`() {
        // The id is what goes into SharedPreferences, so it must stay a slug: no spaces, no case
        // changes that would silently orphan an existing selection.
        val slug = Regex("^[a-z0-9_]+$")
        PhotoTargetPool.ITEMS.forEach { item ->
            assertTrue("bad id '${item.id}'", slug.matches(item.id))
        }
    }

    @Test
    fun `every item has at least one synonym and no blanks`() {
        PhotoTargetPool.ITEMS.forEach { item ->
            assertTrue("${item.id} has no synonyms", item.mlKitSynonyms.isNotEmpty())
            assertTrue("${item.id} has a blank synonym", item.mlKitSynonyms.none { it.isBlank() })
        }
    }

    @Test
    fun `common ten ids all exist in the pool`() {
        val ids = PhotoTargetPool.ITEMS.map { it.id }.toSet()
        assertEquals(10, PhotoTargetPool.COMMON_10_IDS.size)
        assertTrue("dangling common id", ids.containsAll(PhotoTargetPool.COMMON_10_IDS))
    }

    @Test
    fun `every item can be found by its id`() {
        PhotoTargetPool.ITEMS.forEach { item ->
            assertEquals(item, PhotoTargetPool.getById(item.id))
        }
    }

    @Test
    fun `unknown id returns null instead of throwing`() {
        assertNull(PhotoTargetPool.getById("no_such_object"))
        assertNull(PhotoTargetPool.getById(""))
    }

    @Test
    fun `all display names line up with the item list`() {
        assertEquals(
            PhotoTargetPool.ITEMS.map { it.displayName },
            PhotoTargetPool.ALL_ITEMS
        )
    }

    @Test
    fun `mission enum round trips by name`() {
        // MissionPrefs stores MissionType.name and restores with valueOf, so every constant has to
        // survive that round trip.
        MissionType.entries.forEach { type ->
            assertEquals(type, MissionType.valueOf(type.name))
        }
    }
}
