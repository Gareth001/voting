package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.io.*


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
