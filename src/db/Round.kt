package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.io.*

class Round(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Round>(Rounds)

    var resolved by Rounds.resolved
    var number by Rounds.number


    var _left by Entry optionalReferencedOn Rounds.left
        private set
    var left: Entry?
        set(value) {
            this.let { transaction { it._left = value } }
        }
        get() {
            return this.let { transaction { it._left } }
        }

    var _right by Entry optionalReferencedOn Rounds.right
        private set
    var right: Entry?
        set(value) {
            this.let { transaction { it._right = value } }
        }
        get() {
            return this.let { transaction { it._right } }
        }
    
    var bracket by Bracket referencedOn Rounds.bracket

    var _child by Round optionalReferencedOn Rounds.child
        private set
    var child: Round?
        set(value) {
            this.let { transaction { it._child = value } }
        }
        get() {
            return this.let { transaction { it._child } }
        }


    var childEntry by Rounds.childEntry

    // lookup fields for convenience
    val parents by Round optionalReferrersOn Rounds.child

    var votes by User via RoundUsers
    
    fun getParents(): List<Round> {
        // required workaround due to this having a different meaning in the transaction
        return this.let {
            transaction {
                it.parents.asSequence().toList()
            }
        }
    }

    fun remove() {
        this.let {
            transaction {
                it.delete()
            }
        }
    }

    fun hasEntrants(): Boolean {
        return left != null && right != null
    }

    fun isFinale(): Boolean {
        return this.child == null
    }

    /*
    Returns true if the round can be resolved, meaning there are the correct number of votes.
     */
    fun canBeResolved(): Boolean {
        // check if there have been enough votes
        return this.let {
            transaction {
                it.votes.count() == it.bracket.threshold.toLong()
            }
        }
    }

    /*
    Returns two lists of users, the first is the users who voted for the left entrant
    and the right is the users who voted for the right entrant.
     */
    fun getVotes(): Pair<List<User>, List<User>> {
        val myId = this.id

        // get all users that voted for left and right
        val left = RoundUsers.select { RoundUsers.round eq myId }.andWhere{ 
            RoundUsers.vote eq 0 }.map { User(it[RoundUsers.user]) }
        val right = RoundUsers.select { RoundUsers.round eq myId }.andWhere { 
            RoundUsers.vote eq 1 }.map { User(it[RoundUsers.user]) }

        return Pair(left, right)
        
    }

    /*
    Attempt to resolve this round, and resolve if possible
    Side effect: fills in the winner entrant in the child round (if this has a child round)
    Returns the winning entrant from this round if it was resolved
     */
    fun tryResolve(): Entry? {
        if (!this.hasEntrants()) {
            return null
        }

        this.resolved = true

        if (!this.canBeResolved()) {
            // TODO log error - couldn't resolve
            this.resolved = false
            return null

        }

        val myId = this.id
        var leftVotes = 0
        var rightVotes = 0

        RoundUsers.select { RoundUsers.round eq myId }.forEach {
            if (it[RoundUsers.vote] == 0) {
                leftVotes += 1
            } else {
                rightVotes += 1
            }

        }

        // guaranteed to have an Entry since hasEntrants() already been checked
        val winner: Entry? = if (leftVotes > rightVotes) {
            this.left
        } else {
            this.right
        }

        // propagate the winner to the child round
        if (this.childEntry == 0) {
            this.child?.left = winner
        } else {
            this.child?.right = winner
        }

        return winner
    }

}

/*
 * Create a new round.
 * Optionally, pass the left and right parents of this round 
 * Rounds may not have a right parent, and some rounds have no parents.
 */
fun createRound(left: Entry?, right: Entry?, number: Int, bracket: Bracket, parent: Pair<Round?,Round>?): Round {
    return transaction {
        val round = Round.new {
            this.resolved = false
            this.number = number
            this.left = left
            this.bracket = bracket
            this.right = right
        }

        parent?.first?.child = round
        parent?.first?.childEntry = 0

        parent?.second?.child = round
        parent?.second?.childEntry = 1

        round
    }
}

/*
 */
// fun Rounds.getAll(): List<Round> {
//     return transaction {
//         Round.all().asSequence().toList()
//     }
// }
