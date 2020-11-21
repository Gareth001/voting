package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.io.*

/*
 * DAO class for the table Brackets.
 * A bracket contains all rounds, a threshold and a winning entrant
 */
class Bracket(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Bracket>(Brackets)

    // name of bracket
    private var _name by Brackets.name
    var name: String
        set(value) { this.let { transaction { it._name = value } } }
        get() { return this._name }

    // total number of votes for a round to be decided
    private var _threshold by Brackets.threshold
    var threshold: Int
        set(value) { this.let { transaction { it._threshold = value } } }
        get() { return this._threshold }

    var winner by Entry optionalReferencedOn Brackets.winner
        private set 

    // lookup all rounds in this bracket for convenience
    private val rounds by Round referrersOn Rounds.bracket

    /* 
     * Get all rounds in this bracket
     */
    fun getRounds(): List<Round> {
        // required workaround due to this having a different meaning in the transaction
        return this.let {
            transaction {
                it.rounds.asSequence().toList()
            }
        }
    }

    /* 
     * Given a list of already created entries, create all rounds for the bracket.
     * Side effects: creates rounds
     */
    fun createRounds(entries: List<Entry>) {

        // Create all rounds from these entries
        val chunks: List<List<Entry>> = entries.chunked(2)

        val rounds: List<Round> = chunks.mapIndexed { index, round ->
            if (round.size == 1) null else createRound(round[0], round[1], index+1, this, null)
        }.filterNotNull()

        // See if there's a leftover round (i.e. odd number of entrants)
        val leftover: Entry? = if (chunks.lastOrNull()?.size == 1) {
            chunks.last()[0]
        } else {
            null
        }

        // keep track of round number, since this is required for each round
        var roundNumber = rounds.size

        /* 
        * Recursive function to create the remainder of the brackets.
        * If there's a single leftover, use it as the first in the next round to ensure fairness 
        * e.g. for 2^n + 1 number of entrants.
        */
        fun createRoundsRecursive(rounds: List<Round>, leftoverEntry: Entry?, leftoverRound: Round?) {

            // base case - final round
            if (rounds.size == 1 && leftoverEntry == null && leftoverRound == null) {
                return
            }

            val myRounds: MutableList<Round> = mutableListOf()
            var startIndex = 0

            // check if there is a leftover, add it to the intial round
            if (leftoverEntry != null) {
                myRounds.add(createRound(leftoverEntry, null, roundNumber + 1, this, Pair(null, rounds[0])))
                roundNumber += 1
                startIndex = 1

            } else if (leftoverRound != null) {
                myRounds.add(createRound(null, null, roundNumber + 1, this, Pair(leftoverRound, rounds[0])))
                roundNumber += 1
                startIndex = 1

            }

            // create the rest
            val newChunks: List<List<Round>> = rounds.subList(startIndex, rounds.size).chunked(2)

            myRounds.addAll(
                newChunks.mapNotNull { 
                    if (it.size == 1) null else {
                        roundNumber += 1
                        createRound(null, null, roundNumber, this, Pair(it[0], it[1]))
                    } 
                }
            )

            // See if there's a leftover round (i.e. odd number of entrants)
            val newLeftover: Round? = if (newChunks.lastOrNull()?.size == 1) {
                newChunks.last()[0]
            } else {
                null
            }

            // recursive call
            createRoundsRecursive(myRounds, null, newLeftover)

        }

        createRoundsRecursive(rounds, leftover, null)

    }

}

/*
 * Create new bracket
 */
fun createBracket(name: String, threshold: Int): Bracket {
    return transaction {
        Bracket.new {
            this.name = name
            this.threshold = threshold
        }
    }
}

/*
 * Given an id, maybe return a bracket object
 */
fun lookupBracket(id: Int): Bracket? {
    return transaction { Bracket.find { Brackets.id eq id }.firstOrNull() }

}

/*
 * Return all brackets
 */
fun getAllBrackets(): List<Bracket> {
    return transaction {
        Bracket.all().asSequence().toList()
    }
}
