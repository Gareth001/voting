package com.voting

import com.voting.db.dao.*

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*


fun Route.user() {
    route("/user") {
        get {
            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            if (user == null || !user.admin) {
                call.respondRedirect("/login", permanent = false)

            } else {
                call.respondHtml {
                    body {
                        +"Welcome! Logged in as ${user.name}"
                        br()
                        a(href = "/user/new") { +"Register a new user" }
                        br()

                        +"Existing Users:"
                        br()

                        for (otherUser in getAllUsers()) {
                            +otherUser.name
                            br()
                        }
                    }
                }
            }
        }

        get("/new") {
            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            if (user == null || !user.admin) {
                call.respondRedirect("/login", permanent = false)

            } else {
                call.respondHtml {
                    body {
                        +"Welcome! Logged in as ${user.name}"
                        br()

                        +"Register a new user: "
                        form(action = "/user/new", method = FormMethod.post) {

                            +"username: "
                            input(InputType.text, name = "username") 
                            br()

                            +"password: "
                            input(InputType.password, name = "password")
                            br()

                            +"admin: "
                            input(InputType.checkBox, name = "admin")
                            br()

                            input(InputType.submit) { value = "Create" }
                        }
                    }
                }            
            }
        }

        post("/new") {
            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            val post = call.receive<Parameters>()

            if (user == null || !user.admin) {
                call.respondRedirect("", permanent = false)

            } else {
                // try create new user
                val newUser = post["username"]?.let {post["password"]?.run {
                    createUser(it, this, post["admin"] == "on")
                } }

                if (newUser == null) {
                    call.respondRedirect("", permanent = false)
                } else {
                    call.respondRedirect("/user", permanent = false)
                }

            }

        }
    }

}