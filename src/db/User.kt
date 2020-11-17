package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.io.*
import org.mindrot.jbcrypt.BCrypt

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

fun createUser(name: String, password: String, admin: Boolean): User? {
    // can result in Unique index or primary key violation
    return try {
        transaction {
            User.new {
                this.name = name
                this.passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
                this.admin = admin
            }
        }
    } catch (e: Exception) {
        null
    }
}

fun lookupUser(name: String): User? {
    return transaction { User.find { Users.name eq name }.firstOrNull() }

}
