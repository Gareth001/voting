package com.voting

import kotlin.test.*

import com.voting.db.dao.*

class UserTest {

    @Test
    fun testCreationRemove() {

        // size won't be zero due to admin user
        val size = getAllUsers().size

        val user: User? = createUser("test", "test", true)

        assertEquals(getAllUsers().size, size + 1)

        assertNotNull(user)

        assertEquals(user.name, "test")
        assertEquals(user.admin, true)

        user.remove()

        assertEquals(getAllUsers().size, size)

    }

    @Test
    fun testCreationDuplicate() {
        // should not have users with duplicate names

        val user: User? = createUser("test", "test", true)
        assertNotNull(user)

        val user2: User? = createUser("test", "testing", false)
        assertNull(user2)

        user.remove()
    }

    @Test
    fun testLookup() {
        val user: User? = createUser("test", "test", true)
        assertNotNull(user)

        val user2: User? = createUser("testing", "testing", false)
        assertNotNull(user2)

        assertEquals(user.id, lookupUser("test")?.id)
        assertNotEquals(user.id, lookupUser("testing")?.id)
        assertEquals(user2.id, lookupUser("testing")?.id)
        assertNotEquals(user2.id, lookupUser("testding")?.id)

        assertEquals(user.id, lookupUserId(user.id.value)?.id)
        assertNotEquals(user.id, lookupUserId(user2.id.value)?.id)
        assertEquals(user2.id, lookupUserId(user2.id.value)?.id)

        assertNull(lookupUser("testi"))
        assertNull(lookupUser("te"))

        user.remove()
        user2.remove()
    }

}
