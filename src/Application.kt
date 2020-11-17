package com.voting
import com.voting.db.dao.*

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import io.ktor.util.*
import java.io.*
import org.mindrot.jbcrypt.BCrypt


val db = initdb()

/**
 * Typed session that will be used in this application.
 */
data class MySession(val username: String)


/* 
secret for authenticating sessions
 */
val secretHashKey = File("hashKey").readBytes()

/*
default admin user
 */
val admin = createUser("admin", File("adminPass").readText(), true)

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Sessions) {
        cookie<MySession>("SESSION") {
            transform(SessionTransportTransformerMessageAuthentication(secretHashKey))
        }
    }

    routing {
        // serve static files
        static("static") {
            files("resources/static")
        }

        route("/login") {
            get {
                call.respondHtml {
                    body {
                        form(action = "/login", method = FormMethod.post) {

                            +"username: "
                            input(InputType.text, name = "username") 
                            br()

                            +"password: "
                            input(InputType.password, name = "password")
                            br()

                            input(InputType.submit) { value = "Login" }
                        }
                    }
                }            
            }

            post {
                val post = call.receive<Parameters>()

                // get user associated with the username
                val user: User? = post["username"]?.let {lookupUser(it)}

                if (user == null || post["password"] == null) {
                    call.respondRedirect("/login", permanent = false)

                } else {
                    // authenticate password
                    if (BCrypt.checkpw(post["password"], user.passwordHash)) {
                        call.sessions.set(MySession(user.name))
                        call.respondRedirect("/", permanent = false)

                    } else {
                        call.respondRedirect("/login", permanent = false)

                    }
                }
            }
        }

        route("/bracket") {

            get("/new") {
                val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}
    
                if (user == null || user.admin == false) {
                    call.respondRedirect("/login", permanent = false)

                } else {
                    call.respondHtml {
                        body {
                            +"Welcome! Logged in as ${user.name}"
                        }
                    }            

                }
            }

            get("/{bracketid}") {
                val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}
    
                if (user == null || user.admin == false) {
                    call.respondRedirect("/login", permanent = false)

                } else {
                    println(call.parameters["bracketid"])
                    call.respondHtml {
                        body {
                            +"Welcome! Logged in as ${user.name}"
                        }
                    }            

                }
            }

        }


        get("/") {

            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            if (user == null) {
                call.respondRedirect("/login", permanent = false)

            } else {
                call.respondHtml {
                    body {
                        +"Welcome! Logged in as ${user.name}"
                        br()

                        if (user.admin) {
                            a(href = "bracket/new") { +"Create new bracket" }
                            br()
                            a(href = "user/new") { +"Register new user" }

                        }

                        // select from all brackets to view

                    }
                }            
            }
        }

        post("/html-dsl") {
            call.receive<Parameters>()
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
