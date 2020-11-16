package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.io.*

import kotlin.reflect.full.memberProperties
import kotlin.reflect.KMutableProperty1

/*
Using exposed's DAO functionality

Maybe I'm doing things wrong but lots of workaroudns are required to make it actually work like a dao,
e.g. accessing convenience fields or things like Bracket.all() requires it to be in a transaction which 
defeats the point of a DAO. 
Also, no fields can be modified as they are not in a transaction either

 */


fun initdb(): Database {
    val db = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver", user = "root", password = "")

    transaction {
        SchemaUtils.create(Users, Entries, Rounds, Brackets, RoundUsers)
    }

    return db
}

/// User 

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var name: String by Users.name
    var passwordHash: String by Users.passwordHash
    var admin: Boolean by Users.admin

    fun remove() {
        this.let {
            transaction {
                it.delete()
            }
        }
    }

}


fun createUser(name: String, passwordHash: String, admin: Boolean): User? {
    // can result in Unique index or primary key violation
    return try {
        transaction {
            User.new {
                this.name = name
                this.passwordHash = passwordHash
                this.admin = admin
            }
        }
    } catch (e: Exception) {
        null
    }
}

/// Entry

class Entry(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Entry>(Entries)

    /**
    * Returns the path to the image refered to by this entry for serving static files.
    */
    fun getImagePath(): String {
        return "uploaded/${this.id}.png"
    }

}

fun createEntry(image: ByteArray): Entry {
    // TODO save image to disk
    

    return transaction {
        Entry.new {
        }
    }
}


/// Round

class Round(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Round>(Rounds)

    var resolved by Rounds.resolved
    var left by Entry optionalReferencedOn Rounds.left
    var right by Entry optionalReferencedOn Rounds.right
    var bracket by Bracket referencedOn Rounds.bracket

    var child by Round optionalReferencedOn Rounds.child
    var childEntry by Rounds.childEntry

    // lookup fields for convenience
    val parents by Round optionalReferrersOn Rounds.child

    var votes by User via RoundUsers
    
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
        var leftVotes = 0
        var rightVotes = 0

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
fun Rounds.createRound(left: Entry?, right: Entry?, bracket: Bracket, parent: Pair<Round?,Round>?): Round {
    return transaction {
        val round = Round.new {
            this.resolved = false
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

// fun <T: Any, U> test(obj: T, value: U, field: String) {
//     transaction {
//         val prop = obj::class.memberProperties.filter {it.name == field}.firstOrNull()
//         if (prop is KMutableProperty1) {
//             (prop as KMutableProperty1<T, U>).set(obj, value)
//         }
//     }
// }


/// Bracket
class Bracket(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Bracket>(Brackets)

    var _name by Brackets.name
        private set
    var name: String
        set(value) {
            this.let { transaction { it._name = value } }
        }
        get() {
            return this._name
        }

    var _threshold by Brackets.threshold
        private set
    var threshold: Int
        set(value) {
            this.let { transaction { it._threshold = value } }
        }
        get() {
            return this._threshold
        }

    var winner by Entry optionalReferencedOn Brackets.winner
        // private set 

    // lookup all rounds in this bracket for convenience
    private val rounds by Round referrersOn Rounds.bracket

    /* 
    Get all rounds
     */
    fun getRounds(): List<Round> {
        // required workaround due to this having a different meaning in the transaction
        return this.let {
            transaction {
                it.rounds.asSequence().toList()
            }
        }
    }

    // given a list of images, initializes all entries and rounds
    fun createRounds(images: List<ByteArray>) {

        // firstly create all entries
        val entries: List<Entry> = images.map { createEntry(it) }

        // Create all rounds from these entries
        val chunks: List<List<Entry>> = entries.chunked(2)

        val rounds: List<Round> = chunks.mapNotNull { 
            if (it.size == 1) null else Rounds.createRound(it[0], it[1], this, null)
        }

        // See if there's a leftover round (i.e. odd number of entrants)
        val leftover: Entry? = if (chunks.lastOrNull()?.size == 1) {
            chunks.last()[0]
        } else {
            null
        }

        println("created ${rounds.size} rounds with leftover ${leftover}")

        /* 
        * Recursive function to create the remaineder of the brackets.
        * If there's a single leftover, use it as the first in the next round to ensure fairness 
        * e.g. for 2^n + 1 number of entrants.
        */
        fun createRoundsRecursive(rounds: List<Round>, leftoverEntry: Entry?, leftoverRound: Round?) {

            // base case - final round
            if (rounds.size == 1 && leftoverEntry == null && leftoverRound == null) {
                return
            }

            var myRounds: MutableList<Round> = mutableListOf()
            var startIndex = 0

            // check if there is a leftover, add it to the intial round
            if (leftoverEntry != null) {
                myRounds.add(Rounds.createRound(leftoverEntry, null, this, Pair(null, rounds[0])))
                startIndex = 1

            } else if (leftoverRound != null) {
                myRounds.add(Rounds.createRound(null, null, this, Pair(leftoverRound, rounds[0])))
                startIndex = 1

            }

            // create the rest
            val newChunks: List<List<Round>> = rounds.subList(startIndex, rounds.size).chunked(2)

            myRounds.addAll(
                newChunks.mapNotNull { 
                    if (it.size == 1) null else Rounds.createRound(null, null, this, Pair(it[0], it[1]))
                }
            )

            // See if there's a leftover round (i.e. odd number of entrants)
            val newLeftover: Round? = if (newChunks.lastOrNull()?.size == 1) {
                newChunks.last()[0]
            } else {
                null
            }

            println("created ${myRounds.size} rounds with leftover ${newLeftover}")

            // recursive call
            createRoundsRecursive(myRounds, null, newLeftover)

        }

        createRoundsRecursive(rounds, leftover, null)

    }


}

// Create new bracket
fun createBracket(name: String, threshold: Int): Bracket {
    return transaction {
        Bracket.new {
            this.name = name
            this.threshold = threshold
        }
    }
}

// return voting ladder?

