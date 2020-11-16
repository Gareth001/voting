package com.voting
import com.voting.db.dao.*

import org.jetbrains.exposed.sql.transactions.transaction
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import io.ktor.util.*
import java.io.*

val db = initdb()

val admin = createUser("admin", "haha", true)

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    routing {
        // serve static files
        static("static") {
            files("resources/static")
        }

        get("/") {
            call.respondText("test", contentType = ContentType.Text.Plain)
        }

        get("/html-dsl") {
            val numbers = (0..9).toList()
            val user: User? = createUser("test", "test", true)
            val entry: Entry = createEntry("test".toByteArray())

            val bracket: Bracket = createBracket("test", 5)

            bracket.createRounds(MutableList(51) {"test".toByteArray()})

            println(bracket.name)

            println(bracket.getRounds())

            println(bracket.name)
            bracket.name = "testing"
            println(bracket.name)

            bracket.getRounds().first().canBeResolved()

            // println(Round.all())

            call.respondHtml {
                body {
                    h1 { +"HTML ${user?.id}" }
                    ul {
                        for (n in numbers) {
                            li { +"$n" }
                        }
                    }
                    img {
                        src = "static/${entry.getImagePath()}"
                    }
                }
            }
        }

        get("/styles.css") {
            call.respondCss {
                body {
                    backgroundColor = Color.red
                }
                p {
                    fontSize = 2.em
                }
                rule("p.myclass") {
                    color = Color.blue
                }
            }
        }

    }
}

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
    style(type = ContentType.Text.CSS.toString()) {
        +CSSBuilder().apply(builder).toString()
    }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
