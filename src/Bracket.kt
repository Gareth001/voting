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
                            genRoundHTML(round)

                        }

                    }
                }            

            }
        }

        post("/{bracketid}") {
            val user = call.sessions.get<MySession>()?.username?.let {lookupUser(it)}

            val post = call.receive<Parameters>()
            val results: List<Int?>? = post["vote"]?.split("_")?.map { it.toIntOrNull() }

            if (results != null && user != null) {
                val round = results.getOrNull(0)
                val entrant = results.getOrNull(1)

                if (round != null && entrant != null) {
                    lookupRound(round)?.setVote(user, entrant)
                    lookupRound(round)?.tryResolve() // automatically resolve rounds
                }
            }

            // redirect to same page 
            call.respondRedirect("", permanent = false)

        }

    }
}

/*
Generate all the HTML for a single round
 */
fun kotlinx.html.BODY.genRoundHTML(round: Round) {
    h1 {
        attributes["id"] = "${round.number}" // id for same page redirect
        +"Round ${round.number}" 
    }
    
    if (!round.isFinale()) {
        +"The winner of this round goes to "
        a(href = "#${round.child?.number}") { +"Round ${round.child?.number}" }
        +"."

    }

    br()

    genEntryHTML(round, 0)

    br()
    h3 { +"VS" }
    br()

    genEntryHTML(round, 1)
    br()

}

/*
Generate all the HTML for a single entry
entrant is 0 for left, 1 for right
 */
fun kotlinx.html.BODY.genEntryHTML(round: Round, entrant: Int) {

    val entry: Entry? = if (entrant == 0) {
        round.left
    } else {
        round.right
    }

    for (parent in round.getParents()) {
        if (parent.childEntry == entrant) {
            +"The winner of "
            a(href = "#${parent.number}") { +"Round ${parent.number}" }
            +": "
            break
        }
    }

    // entry could be null, check if there was an entry
    val success: Entry? = entry?.apply { img(src = this.getImagePath()) }
    if (success == null) {                                
        +" (TBD)"
    }

    br()

    // display votes 
    val votes = round.getVotes(entrant)

    if (votes.size == 0) {
        +" No Votes"
    } else {
        +"Votes: "
    }

    round.getVotes(entrant).forEach {
        +"${it.name}"
    }

    // voting form, one form per entrant (only one button)
    // the round and entrant are encoded in the input
    if (round.hasEntrants() && !round.resolved) {
        form(action = "#${round.number}", method = FormMethod.post) {
            input(InputType.hidden, name="vote") { value = "${round.id}_$entrant" }
            input(InputType.submit, name="action") { value = "Vote" }
        }
    }

}
