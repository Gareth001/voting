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
import java.time.Duration
import java.time.LocalDateTime


fun Route.bracket() {
    route("/bracket") {

        get("/new") {
            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            if (user == null || !user.admin) {
                call.respondRedirect("/login", permanent = false)

            } else {
                call.respondHtml {
                    body {
                        +"Welcome! Logged in as ${user.name}"

                        // take multiple image files 
                        form(action = "new", method = FormMethod.post, encType = FormEncType.multipartFormData) {
                            +"Image Files (1 per entrant, must have at least 2 entrants): "
                            input(InputType.file, name = "file[]") {
                                attributes["multiple"] = "true"
                            }
                            br()

                            +"Bracket Name: "
                            input(InputType.text, name = "name") 
                            br()

                            +"Number of votes to decide each round (this must be an odd number): "
                            input(InputType.number, name = "threshold") 
                            br()

                            input(InputType.submit) { value = "Create New Bracket" }

                        }

                    }
                }            

            }
        }

        post("/new") {
            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            if (user == null || !user.admin) {
                call.respondRedirect("/login", permanent = false)

            } else {
                val multipart = call.receiveMultipart()

                var bracketName: String? = null
                var threshold: Int? = null
                val entries: MutableList<Entry> = mutableListOf()

                // loop over each item in the response
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            // items from the inputs
                            if (part.name == "name") {
                                bracketName = part.value
                            }
                            if (part.name == "threshold") {
                                threshold = part.value.toIntOrNull()
                            }
                        }
                        is PartData.FileItem -> {
                            // if a file, create the corresponding entry
                            entries.add(createEntry(part.streamProvider()))
                        }
                    }
                    part.dispose()
                }

                // must have at least 2 entrants, otherwise there'd be no point
                if (entries.size > 1) {
                    
                    // create bracket
                    val bracket: Bracket? = bracketName?.run {
                        threshold?.let {
                            // threshold must be odd
                            if (it % 2 == 1 && it > 0) {
                                createBracket(this, it)
                            } else {
                                null
                            }
                        }
                    }

                    if (bracket == null) {
                        // unsuccessful - clear all entries
                        entries.forEach { it.remove() }
                        call.respondRedirect("/bracket/new", permanent = false)

                    } else {
                        bracket.createRounds(entries)
                        call.respondRedirect("/bracket/${bracket.id.value}", permanent = false)

                    }

                } else {
                    // unsuccessful - clear all entries
                    entries.forEach { it.remove() }
                    call.respondRedirect("/bracket/new", permanent = false)
                    
                }
            }
        }


        get("/{bracketid}") {
            val start = LocalDateTime.now()
            println("start request ${start}")

            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            // get bracket from id
            val bracket: Bracket? = call.parameters["bracketid"]?.toIntOrNull()?.let { lookupBracket(it) } 

            if (user == null) {
                call.respondRedirect("/login", permanent = false)

            } else if (bracket == null) {
                call.respondRedirect("/", permanent = false)

            } else {

                call.respondHtml {
                    head {
                        link(rel = "stylesheet", href = "/static/style.css", type = "text/css")
                    }
                    body {

                        sidebar(user, bracket)

                        mainContent {
                            // see if there's a winner
                            if (bracket.winner != null) {
                                h2 {
                                    attributes["id"] = "Winner" // assign id for same page redirect
                                    +"${bracket.name} WINNER!"
                                }

                                fun BODY.party() {
                                    div(classes = "marquee") { 
                                        p { +"\uD83C\uDF89\uD83C\uDF8A".repeat(6) }
                                        p { 
                                            style = "left: 300px;"
                                            +"\uD83C\uDF89\uD83C\uDF8A".repeat(6) 
                                        }
                                    }
                                }

                                party()
                                bracket.winner?.apply { img(src = this.getImagePath()) }
                                party()
                                br
                            }

                            // variable to store the current group e.g. semi finals, finals
                            var prevShallowness = -1

                            for (round in bracket.getRounds()) {
                                // see if we're in a new depth in the bracket tree
                                if (round.shallowness != prevShallowness) {
                                    // show final level
                                    h2 { 
                                        val finalLevel = bracket.getFinalLevel(round.shallowness) 
                                        attributes["id"] = finalLevel // assign id for same page redirect
                                        +finalLevel
                                    }
                                    prevShallowness = round.shallowness
                                }

                                getRoundHTML(round, bracket)

                            }

                            // see if there's a winner 
                            if (bracket.winner != null) {
                                h2 { +"Winner" }
                                bracket.winner?.apply { img(src = this.getImagePath()) }
                            }
                        }

                    }
                }            

            }

            println("finish request in ${Duration.between(start, LocalDateTime.now()).toMillis()}ms")

        }

        post("/{bracketid}") {
            val user = call.sessions.get<MySession>()?.username?.let { lookupUser(it) }

            val post = call.receive<Parameters>()
            val results: List<Int>? = post["vote"]?.split("_")?.mapNotNull { it.toIntOrNull() }

            // get bracket and verify round is in bracket
            val bracket: Bracket? = call.parameters["bracketid"]?.toIntOrNull()?.let { lookupBracket(it) }

            if (results != null && user != null && bracket != null) {
                val round = results.getOrNull(0)?.let { lookupRound(it) }
                val entrant = results.getOrNull(1)

                // also check that round is in this bracket before voting
                if (round != null && entrant != null && round.hasEntrants() &&
                        round.id in bracket.getRounds().map { it.id }.toSet()) {
                    round.setVote(user, entrant)
                    val result = round.tryResolve() // automatically resolve rounds

                    // invalidate cache of this round
                    jedis.del("bracket.${bracket.id}.round.${round.id}")
                    jedis.del("bracket.${bracket.id}.sidebar.${round.id}")

                    // maybe invalidate cache of child round
                    if (result != null) {
                        round.child?.let {
                            jedis.del("bracket.${bracket.id}.round.${it.id}")
                            jedis.del("bracket.${bracket.id}.sidebar.${it.id}")
                        }
                    }

                    if (result != null && round.isFinale()) {
                        bracket.winner = result

                    }

                }
            }

            // redirect to same page 
            call.respondRedirect("", permanent = false)

        }

    }
}


/* 
 * wrap the main content in this element to make the sidebar work
 */
fun kotlinx.html.BODY.mainContent(html: () -> Unit) {
    div(classes = "main") {
        html()
    }
}