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

                        // take multiple image files 
                        form(action = "new", method = FormMethod.post, encType = FormEncType.multipartFormData) {
                            +"Image Files (1 per entrant): "
                            input(InputType.file, name = "file[]") {
                                attributes["multiple"] = "true"
                            }
                            br()

                            +"Bracket Name: "
                            input(InputType.text, name = "name") 
                            br()

                            +"Voting threshold for each round: "
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

            if (user == null || user.admin == false) {
                call.respondRedirect("/login", permanent = false)

            } else {
                val multipart = call.receiveMultipart()

                var bracketName: String? = null
                var threshold: Int? = null
                var entries: MutableList<Entry> = mutableListOf()

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
                            createBracket(this, it)
                        }
                    }

                    if (bracket == null) {
                        // unsucessful - clear all entries
                        entries.forEach { it.remove() }
                        call.respondRedirect("/bracket/new", permanent = false)

                    } else {
                        bracket.createRounds(entries)
                        call.respondRedirect("/bracket/${bracket.id.value}", permanent = false)

                    }

                } else {
                    // unsucessful - clear all entries
                    entries.forEach { it.remove() }
                    call.respondRedirect("/bracket/new", permanent = false)
                    
                }
            }
        }


        get("/{bracketid}") {
            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            // get bracket from id
            val bracket: Bracket? = call.parameters["bracketid"]?.toIntOrNull()?.let { lookupBracket(it) } 

            if (user == null) {
                call.respondRedirect("/login", permanent = false)

            } else if (bracket == null) {
                call.respondRedirect("/", permanent = false)

            } else {

                call.respondHtml {
                    body {
                        +"Welcome! Logged in as ${user.name}"
                        br()
                        +"Viewing bracket ${bracket.name}"
                        br()

                        for (round in bracket.getRounds()) {
                            h1 { +"Round ${round.number}" }
                            br()

                            val left: Entry? = round.left?.apply { img(src = this.getImagePath()) }
                            if (left == null) {                                
                                for (parent in round.getParents()) {
                                    if (parent.childEntry == 0) {
                                        +"The winner of round ${parent.number} (TBD)"
                                        break
                                    }
                                }
                            }

                            br()
                            +"VS"
                            br()

                            val right: Entry? = round.right?.apply { img(src = this.getImagePath()) }
                            if (right == null) {
                                for (parent in round.getParents()) {
                                    if (parent.childEntry == 1) {
                                        +"The winner of round ${parent.number} (TBD)"
                                    }
                                }
                            }
                            br()

                        }

                    }
                }            

            }
        }

    }
}