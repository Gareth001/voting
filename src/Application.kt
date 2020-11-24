package com.voting
import com.voting.db.dao.*

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.http.content.*
import io.ktor.html.*
import kotlinx.html.*
import java.io.*
import io.ktor.features.CachingHeaders
import io.ktor.http.*


val db = initdb()

/*
 * Typed session that will be used in this application
 */
data class MySession(val username: String)


/* 
 * secret for authenticating sessions
 */
val secretHashKey = File("hashKey").readBytes()

/*
 * default admin user
 */
val admin = createUser("admin", File("adminPass").readText(), true)

fun main(args: Array<String>): Unit = io.ktor.server.jetty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Sessions) {
        cookie<MySession>("SESSION") {
            transform(SessionTransportTransformerMessageAuthentication(secretHashKey))
        }
    }

    install(CachingHeaders) {
        options { outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Image.PNG -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                else -> null
            }
        }
    }

    routing {
        // serve static files
        // TODO intercept to check login
        static("static") {
            files("resources/static")
        }

        login()
        bracket()
        user()

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
                            a(href = "user") { +"View Users" }

                        }
                        br()
                        br()

                        +"Brackets:"
                        br()
                        for (bracket in getAllBrackets()) {
                            a(href = "/bracket/${bracket.id}") { +bracket.name }
                            br()
                        }
                    }
                }            
            }
        }

    }
}
