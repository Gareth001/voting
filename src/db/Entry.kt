package com.voting.db.dao

import com.voting.db.tables.*

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.*
import java.io.*
import java.util.UUID

/*
 * DAO class for Entry.
 * Entry is just an id, this id is used to store image files on disk (resources/static/uploaded/x.png)
 * Therefore it doesn't require any fields.
 */
class Entry(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Entry>(Entries)

    /*
     * Returns the path to the image referred to by this entry for serving static files.
     */
    fun getImagePath(): String {
        // necessary let
        return this.let { transaction { "/static/uploaded/${it.id}.png" } }
    }

    /* 
     * Delete entry
     */
    fun remove() {
        transaction {
            delete()
        }
    }

}

/* 
 * Create entry. Takes a java.io.InputStream
 * Side effect: save contents of InputStream to disk
 */
fun createEntry(image: InputStream): Entry {
    // TODO validate image somehow

    val entry = transaction {
        Entry.new {
        }
    }

    val file = File("resources/static/uploaded/${entry.id.value}.png")
    image.use { input -> file.outputStream().buffered().use { output -> input.copyTo(output) } }

    return entry

}

/*
 * Copy from input stream to output stream
 * Adapted from https://ktor.io/docs/uploads.html#receiving-files-using-multipart
 */
fun InputStream.copyTo(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
    val buffer = ByteArray(bufferSize)
    var bytesCopied = 0L

    while (true) {
        val bytes = read(buffer).takeIf { it >= 0 } ?: break
        out.write(buffer, 0, bytes)
        bytesCopied += bytes

    }

    return bytesCopied
}
