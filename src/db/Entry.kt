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
        return this.let { transaction { "/static/uploaded/${it.id}.png" } }
    }

    fun remove() {
        this.let {
            transaction {
                it.delete()
            }
        }
    }


}

fun createEntry(image: InputStream): Entry {
    // TODO validate image

    val entry = transaction {
        Entry.new {
        }
    }

    val file = File("resources/static/uploaded/${entry.id.value}.png")
    image.use { input -> file.outputStream().buffered().use { output -> input.copyTo(output) } }

    return entry

}

/*
Adapted from https://ktor.io/docs/uploads.html#receiving-files-using-multipart
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
