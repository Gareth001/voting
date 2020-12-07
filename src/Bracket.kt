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
                        h1 { +"Viewing ${bracket.name}" }

                        // provide all links to skip to final groups
                        h3 { +"Skip to:" }

                        for (i in 0..bracket.depth) {
                            val finalLevel = bracket.getFinalLevel(i)
                            a(href = "#${finalLevel}") { +finalLevel }
                            br()
                        }

                        // variable to store the current group e.g. semi finals, finals
                        var prevShallowness = -1

                        for (round in bracket.getRounds()) {
                            // see if we're in a new depth in the bracket tree
                            if (round.shallowness != prevShallowness) {
                                // show final level
                                h2 { 
                                    val finalLevel = bracket.getFinalLevel(round.shallowness) 
                                    attributes["id"] = "${finalLevel}" // assign id for same page redirect
                                    +finalLevel
                                }
                                prevShallowness = round.shallowness
                            }

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
 * Generate all the HTML for a single round
 */
fun kotlinx.html.BODY.genRoundHTML(round: Round) {
    h3 {
        attributes["id"] = "${round.number}" // id for same page redirect
        +"Round ${round.number}" 
    }
    
    if (!round.isFinale()) {
        +"The winner of this round goes to "
        val child = round.child?.number
        a(href = "#${child}") { +"Round ${child}" }
        +"."

    }

    br()

    genEntryHTML(round, 0)

    br()
    h4 { +"VS" }
    br()

    genEntryHTML(round, 1)
    br()

}

/*
 * Generate all the HTML for a single entry
 * entrant is 0 for left, 1 for right
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

    if (round.hasEntrants()) {
        // display votes
        val votes = round.getVotes(entrant)

        if (votes.isEmpty()) {
            +" No Votes"
        } else {
            +"Votes: "

            // display first voter
            +votes[0].name

            // comma separated
            if (votes.size > 1) {
                votes.subList(1, votes.size).forEach {
                    +", "
                    +it.name
                }
            }
        }

        if (!round.resolved) {
            // voting form, one form per entrant (only one button)
            // the round and entrant are encoded in the input
            form(action = "#${round.number}", method = FormMethod.post) {
                input(InputType.hidden, name="vote") { value = "${round.id}_$entrant" }
                input(InputType.submit, name="action") { value = "Vote" }
            }
        }
    }

}
