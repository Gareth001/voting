package com.voting.db.tables

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.IntIdTable

/// Database tables

object Users : IntIdTable() {
    val name = varchar("name", 20).uniqueIndex()
    val passwordHash = varchar("passwordHash", 64)
    val admin = bool("admin")

}

/*
 * Entry is just an id, this id is used to store image files on disk (resources/static/uploaded/x.png)
 */
object Entries : IntIdTable() {

}

/*
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

object Rounds : IntIdTable() {
    val resolved = bool("resolved")
    val left = reference("leftEntry", Entries).nullable()
    val right = reference("rightEntry", Entries).nullable()
    val bracket = reference("bracket", Brackets)
    val child = reference("child", Rounds).nullable()
    val childEntry = integer("childEntry").nullable()

}

object Brackets : IntIdTable() {
    val name = varchar("name", 20)
    // val leftover = reference("leftover", Entries).nullable() // an entry unassigned to any round
    val threshold = integer("threshold") // votes to decide a winner in all rounds in the bracket (should be odd)
    val winner = reference("winner", Entries).nullable() // the final round

}

// Store what each user votes for in each round
object RoundUsers : Table() {
    val round = reference("round", Rounds).index()
    val user = reference("user", Users).index()
    val vote = integer("vote") // 0 for left, 1 for right

    override val primaryKey = PrimaryKey(round, user, name = "RoundUserPK")
}
