package com.voting.db.tables

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable

/// Database tables

object Users : IntIdTable() {
    val name = varchar("name", 20).uniqueIndex()
    val passwordHash = varchar("passwordHash", 64)
    val admin = bool("admin")

}

object Entries : UUIDTable() {

}

object Rounds : IntIdTable() {
    val resolved = bool("resolved")
    val number = integer("number")
    val left = reference("leftEntry", Entries).nullable()
    val right = reference("rightEntry", Entries).nullable()
    val bracket = reference("bracket", Brackets)
    val child = reference("child", Rounds).nullable()
    val childEntry = integer("childEntry").nullable()

}

object Brackets : IntIdTable() {
    val name = varchar("name", 20)
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
