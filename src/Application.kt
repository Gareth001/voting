package com.voting
import com.voting.db.dao.*

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.http.content.*
import io.ktor.html.*
import kotlinx.html.*
import java.io.File
import io.ktor.features.CachingHeaders
import io.ktor.http.*


/*
 * Typed session that will be used in this application
 */
data class MySession(val username: String)

/* 
 * secret for authenticating sessions
 */
val secretHashKey = System.getenv("HASH_KEY")?.toByteArray() ?: File("hashKey").readBytes()

fun main(args: Array<String>): Unit = io.ktor.server.jetty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    // initiate database and create admin user in case none exists
    initdb(testing)

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
        static("static") {
            files("resources/static")
        }

        login()
        bracket()
        user()
        admin()

        get("/") {

            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            if (user == null) {
                call.respondRedirect("/login", permanent = false)

            } else {
                call.respondHtml {
                    head {
                        link(rel = "stylesheet", href = "/static/style.css", type = "text/css")
                    }
                    body {
                        +"Welcome! Logged in as ${user.name}"
                        br()

                        if (user.admin) {
                            a(href = "bracket/new") { +"Create new bracket" }
                            br()
                            a(href = "user") { +"View Users" }
                            form(action = "/admin/clear-cache", method = FormMethod.post) {
                                input(InputType.submit) { value = "Clear redis cache" }
                            }

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
