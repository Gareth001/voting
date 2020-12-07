package com.voting

import kotlin.test.*

import com.voting.db.dao.*

class RoundTest {

    @Test
    fun testEmpty() {
        val bracket: Bracket = createBracket("test", 3)

        val round = createRound(null, null, 1, 0, bracket, null)

        val id = round.id.value

        assertNull(round.left)
        assertNull(round.right)
        assertNull(round.child)
        assertFalse(round.resolved)

        assertFalse(round.hasEntrants())
        assertTrue(round.isFinale())
        assertFalse(round.canBeResolved())

        assertTrue(round.getVotes(0).isEmpty())
        assertTrue(round.getVotes(1).isEmpty())

        assertEquals(round.id, lookupRound(round.id.value)?.id)

        assertNull(round.tryResolve())
        assertNotNull(lookupRound(id))

        round.remove()
        bracket.remove()

        assertNull(lookupRound(id))

    }

}
