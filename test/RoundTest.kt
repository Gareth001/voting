package com.voting

import kotlin.test.*

import com.voting.db.dao.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.voting.db.tables.RoundUsers
import org.jetbrains.exposed.sql.*

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

        assertTrue(round.getParents().isEmpty())

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

    @Test
    fun testHasEntrants() {
        val bracket: Bracket = createBracket("test", 1)

        // don't use createEntry to avoid creating files on disk 
        val entryLeft = transaction { Entry.new {} }
        val entryRight = transaction { Entry.new {} }

        val round = createRound(null, null, 1, 0, bracket, null)

        assertFalse(round.hasEntrants())

        round.left = entryLeft

        assertFalse(round.hasEntrants())

        round.right = entryRight

        assertTrue(round.hasEntrants())

        round.remove()
        bracket.remove()
        entryLeft.remove()
        entryRight.remove()

    }

    @Test
    fun testVotes() {
        val bracket: Bracket = createBracket("test", 1)
        val user: User = createUser("test", "test", true)!!

        // don't use createEntry to avoid creating files on disk 
        val entryLeft = transaction { Entry.new {} }
        val entryRight = transaction { Entry.new {} }

        val round = createRound(null, null, 1, 0, bracket, null)

        // cannot vote yet
        round.setVote(user, 0)
        assertTrue(round.getVotes(0).isEmpty())
        assertTrue(round.getVotes(1).isEmpty())

        // set entrants
        round.left = entryLeft

        // cannot vote yet
        round.setVote(user, 0)
        assertTrue(round.getVotes(0).isEmpty())
        assertTrue(round.getVotes(1).isEmpty())

        round.right = entryRight

        // can vote now
        round.setVote(user, 0)
        assertTrue(round.getVotes(0).size == 1)
        assertTrue(round.getVotes(1).isEmpty())

        round.setVote(user, 1)
        assertTrue(round.getVotes(0).isEmpty())
        assertTrue(round.getVotes(1).size == 1)

        assertTrue(round.canBeResolved())

        transaction {
            RoundUsers.deleteWhere { RoundUsers.user eq user.id }
        }

        user.remove()
        round.remove()
        bracket.remove()
        entryLeft.remove()
        entryRight.remove()

    }

    @Test
    fun testResolve() {
        val bracket: Bracket = createBracket("test", 1)
        val user: User = createUser("test", "test", true)!!

        // don't use createEntry to avoid creating files on disk 
        val entryLeft = transaction { Entry.new {} }
        val entryRight = transaction { Entry.new {} }
        val entryExtra = transaction { Entry.new {} }

        val roundFirst = createRound(entryLeft, entryRight, 1, 0, bracket, null)
        var roundSecond = createRound(entryExtra, null, 2, 1, bracket, Pair(null, roundFirst))

        assertFalse(roundFirst.isFinale())
        assertTrue(roundFirst.hasEntrants())
        assertFalse(roundSecond.hasEntrants())
        assertNull(roundFirst.tryResolve())
        assertNull(roundSecond.tryResolve())

        // vote for first round
        roundFirst.setVote(user, 0)
        assertTrue(roundFirst.canBeResolved())

        val result = roundFirst.tryResolve()
        assertNotNull(result)
        assertEquals(result.id, entryLeft.id)

        assertEquals(roundFirst.child!!.id, roundSecond.id)
        assertEquals(roundFirst.childEntry, 1)

        // For some reason DAO values aren't updated in the object. Get object again
        roundSecond = lookupRound(roundSecond.id.value)!!

        // check result is propagated
        assertNotNull(roundSecond.right)
        assertEquals(roundSecond.right!!.id, entryLeft.id)
        assertTrue(roundSecond.hasEntrants())

        // vote for second round
        roundSecond.setVote(user, 0)
        assertTrue(roundSecond.canBeResolved())
        val resultSecond = roundSecond.tryResolve()

        assertNotNull(resultSecond)
        assertEquals(resultSecond.id, entryExtra.id)

        transaction {
            RoundUsers.deleteWhere { RoundUsers.user eq user.id }
        }

        user.remove()
        roundFirst.remove()
        roundSecond.remove()
        bracket.remove()
        entryLeft.remove()
        entryRight.remove()
        entryExtra.remove()

    }

    @Test
    fun testResolveThreeVotes() {
        val bracket: Bracket = createBracket("test", 3)
        val user1: User = createUser("test1", "test", true)!!
        val user2: User = createUser("test2", "test", true)!!
        val user3: User = createUser("test3", "test", true)!!

        // don't use createEntry to avoid creating files on disk 
        val entryLeft = transaction { Entry.new {} }
        val entryRight = transaction { Entry.new {} }

        val round = createRound(entryLeft, entryRight, 1, 0, bracket, null)

        round.setVote(user1, 0)
        round.setVote(user2, 1)

        assertFalse(round.canBeResolved())

        round.setVote(user3, 1)
        
        assertTrue(round.canBeResolved())

        val result = round.tryResolve()
        assertNotNull(result)
        assertEquals(result.id, entryRight.id)

        transaction {
            RoundUsers.deleteWhere { RoundUsers.user greater 0 }
        }

        user1.remove()
        user2.remove()
        user3.remove()
        round.remove()
        bracket.remove()
        entryLeft.remove()
        entryRight.remove()

    }

}
