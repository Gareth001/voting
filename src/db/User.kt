package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.mindrot.jbcrypt.BCrypt

/* 
 * User DAO class
 */
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    // name of user (unique for every user)
    private var _name by Users.name
    var name: String
        set(value) { transaction { _name = value } }
        get() { return transaction { _name } }

    // user's password hash using BCrypt
    var passwordHash: String by Users.passwordHash
    
    // boolean, true if user has admin powers
    var admin: Boolean by Users.admin

    /* 
     * Delete user
     */
    fun remove() {
        transaction {
            delete()
        }
    }

}

/* 
 * Create user. Hashes and salts the plaintext password. 
 * Will fail if there is another user with the same name.
 */
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

/* 
 * Lookup user from username 
 */
fun lookupUser(name: String): User? {
    return transaction { User.find { Users.name eq name }.firstOrNull() }
}

/* 
 * Lookup user from id 
 */
fun lookupUserId(id: Int): User? {
    return transaction { User.find { Users.id eq id }.firstOrNull() }
}

/*
 * Return all brackets
 */
fun getAllUsers(): List<User> {
    return transaction {
        User.all().asSequence().toList()
    }
}
