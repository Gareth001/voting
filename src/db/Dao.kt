package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.io.*

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

