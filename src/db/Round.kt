package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*

/* 
 * Round DAO class.
 * A round has two entries, a left and a right entry.
 * 
 * Round structures have to be predetermined when the bracket is made. if they're created lazily they could 
 * go in an order where a single entrant has to win a very large amount of rounds in order to win
 * which would be unfair. 
 * 
 * The way to get around this is to create the entire ladder when the entrants are imported.
 * This would involve putting all entrants into rounds, and then going through all these rounds and 
 * creating more rounds and connecting them to the previous round as immediate children, repeating this until 
 * there is only 1 round left. Care must be taken to ensure the leftover rounds / entrants are used appropriately 
 * i.e. used in the next round not left until the end
 * Entries are therefore nullable.
 * 
 * As rounds are evaluated, the left and right can be filled in from the child attribute. We need to know which one,
 * so we keep a childEntry (0 for left, 1 for right) to find which to fill in when this round is evaluated.
 */
class Round(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Round>(Rounds)

    // true if this round is resolved, false otherwise
    private var _resolved by Rounds.resolved
    var resolved: Boolean
        set(value) { this.let { transaction { it._resolved = value } } }
        get() { return this.let { transaction { it._resolved } } }

    // round number. E.g. the first round in bracket has a round number of 1 but it 
    // may have a different id
    var number by Rounds.number

    // first entry
    private var _left by Entry optionalReferencedOn Rounds.left
    var left: Entry?
        set(value) { this.let { transaction { it._left = value } } }
        get() {
            return this.let { transaction { it._left } }
        }

    // second entry
    private var _right by Entry optionalReferencedOn Rounds.right
    var right: Entry?
        set(value) { this.let { transaction { it._right = value } } }
        get() { return this.let { transaction { it._right } } }
    
    // link to bracket
    var bracket by Bracket referencedOn Rounds.bracket

    // child round, where the winner of this round will go.
    // can be null i.e. in the final roundc
    private var _child by Round optionalReferencedOn Rounds.child
    var child: Round?
        set(value) { this.let { transaction { it._child = value } } }
        get() { return this.let { transaction { it._child } } }

    // 1 if the winner goes to the left of the child round, 0 for the right
    var childEntry by Rounds.childEntry

    // lookup fields for convenience
    // parents of this round 
    private val parents by Round optionalReferrersOn Rounds.child
    // users that have voted for this round
    private var votes by User via RoundUsers
    
    /*
     * Return all parents of this round
     */
    fun getParents(): List<Round> {
        return this.let {
            transaction {
                it.parents.asSequence().toList()
            }
        }
    }

    /* 
     * Delete round
     */
    fun remove() {
        this.let {
            transaction {
                it.delete()
            }
        }
    }

    /* 
     * Return true if this round has both entrants (i.e. voting can begin on this round)
     */
    fun hasEntrants(): Boolean {
        return left != null && right != null
    }

    /* 
     * Return true if this round is the finale
     */
    fun isFinale(): Boolean {
        return this.child == null
    }

    /*
     * Returns true if the round can be resolved, meaning there are the correct number of votes
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
     * Sets the vote of a user for this round to either left or right
     * Side effect: update the existing vote or create a new one if none exists
     */
    fun setVote(user: User, vote: Int) {
        val myId = this.id

        // can only vote if there are entrants
        transaction {
            if (hasEntrants()) {
                val existing = RoundUsers.select { 
                    (RoundUsers.round eq myId) and (RoundUsers.user eq user.id) }.firstOrNull()

                if (existing != null) {
                    RoundUsers.update(
                        { (RoundUsers.round eq myId) and (RoundUsers.user eq user.id) }) {
                        it[RoundUsers.vote] = vote
                    }
                } else {
                    RoundUsers.insert {
                        it[RoundUsers.round] = myId
                        it[RoundUsers.user] = user.id
                        it[RoundUsers.vote] = vote
                    }
                }
            }
        }
    }

    /*
     * Returns a list of users who voted for the left entrant if entrant is 0,
     * or the right entrant if entrant is 1
     */
    fun getVotes(entrant: Int): List<User> {
        val myId = this.id

        // get all users that voted for left or right
        // very hacky code to get rid of "Property klass should be initialized before get" error 
        // https://github.com/JetBrains/Exposed/issues/497
        // !! since user is guaranteed to exist already
        return transaction {
            RoundUsers.select { RoundUsers.round eq myId }.andWhere{ 
                RoundUsers.vote eq entrant }.map { lookupUserId(User(it[RoundUsers.user]).id.value)!! }
        } 
        
    }

    /*
     * Attempt to resolve this round, and resolve if possible
     * Side effect: fills in the winner entrant in the child round (if this has a child round)
     * and sets the round to be resolved, if it was
     * Returns the winning entrant from this round if it was resolved
     */
    fun tryResolve(): Entry? {
        if (!this.hasEntrants()) {
            return null
        }

        // set to resolved before checking if votes add up to try stop race conditions
        // should be ok since setVote is in one transaction
        this.resolved = true

        if (!this.canBeResolved()) {
            // TODO log error - couldn't resolve
            this.resolved = false
            return null

        }

        val myId = this.id
        var leftVotes = 0
        var rightVotes = 0

        transaction {
            RoundUsers.select { RoundUsers.round eq myId }.forEach {
                if (it[RoundUsers.vote] == 0) {
                    leftVotes += 1
                } else {
                    rightVotes += 1
                }
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
 * Optionally, pass the left and right parents of this round.
 * Side effect: set this to be the parent round's child
 * Note: Rounds may not have a left parent, and some rounds have no parents.
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
 * Lookup user from id 
 */
fun lookupRound(id: Int): Round? {
    return transaction { Round.find { Rounds.id eq id }.firstOrNull() }
}
