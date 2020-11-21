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
import org.mindrot.jbcrypt.BCrypt


fun Route.login() {
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

}