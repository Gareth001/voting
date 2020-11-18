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


fun Route.bracket() {
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
}