package com.voting

import kotlin.test.*

import com.voting.db.dao.*

class BracketTest {

    @Test
    fun testEmpty() {
        val bracket: Bracket = createBracket("test", 3)
        val id = bracket.id.value

        assertTrue( bracket.getRounds().isEmpty())

        assertEquals(bracket.id, lookupBracket(bracket.id.value)?.id)

        assertEquals(getAllBrackets().size, 1)

        bracket.remove()

        assertNull(lookupBracket(id))
        assertTrue(getAllBrackets().isEmpty())

    }

}
