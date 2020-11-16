package com.voting

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import kotlin.test.*
import io.ktor.server.testing.*

import com.voting.db.dao.*

class DaoTest {

    @Test
    fun testUserCreation() {
        val user: User? = createUser("test", "test", true)

        assertNotNull(user)

        assertEquals(user.name, "test")
        assertEquals(user.passwordHash, "test")
        assertEquals(user.admin, true)

        user.remove()
    }

    @Test
    fun testUserCreationDuplicate() {
        // should not have users with duplicate names

        val user: User? = createUser("test", "test", true)
        assertNotNull(user)

        val user2: User? = createUser("test", "testing", false)
        assertNull(user2)

        user.remove()
    }

}
