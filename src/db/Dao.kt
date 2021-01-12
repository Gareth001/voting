package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.*
import java.io.File

/*
 * Using exposed's DAO functionality
 */

fun initdb(testing: Boolean): Database {
    val db = 
        if (testing) {
            Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver", user = "root", password = "")
        } else {
            val ret = Database.connect("jdbc:mariadb://${System.getenv("DATABASE_IP") ?: "localhost"}:3306/voting", 
                    driver = "org.mariadb.jdbc.Driver", user = System.getenv("DATABASE_USER") ?: "root", password = System.getenv("DATABASE_PASS") ?: "")
            createUser("admin", System.getenv("ADMIN_PASS") ?: "test", true)
            ret
        }

    transaction {
        SchemaUtils.create(Users, Entries, Rounds, Brackets, RoundUsers)
    }

    return db
}

