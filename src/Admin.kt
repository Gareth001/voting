package com.voting

import com.voting.db.dao.lookupUser
import io.ktor.application.call
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.sessions.get
import io.ktor.sessions.sessions


fun Route.admin() {
    route("/admin") {

        post("/clear-cache") {
            val user = call.sessions.get<MySession>()?.username?.let { lookupUser(it) }

            if (user == null || !user.admin) {
                call.respondRedirect("/login", permanent = false)

            } else {
                jedis.flushAll()
                println("cleared redis cache")
                call.respondRedirect("/", permanent = false)

            }
        }
    }
}