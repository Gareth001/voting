package com.voting

import com.voting.db.dao.Bracket
import com.voting.db.dao.Round
import com.voting.db.dao.User
import kotlinx.html.*
import kotlinx.html.stream.appendHTML


fun BODY.sidebar(user: User, bracket: Bracket) {
    div(classes = "sidenav") {

        +"Welcome! Logged in as ${user.name}"
        br()

        h2 { a(href = "/") { +"Home" } }
        br()

        h2 { +"Viewing ${bracket.name}" }
        +"${bracket.threshold} votes to decide each round."
        br()

        if (bracket.winner != null) {
            a(href = "#Winner") { +"Winner" }
            br()
        }

        val rounds = bracket.getRounds().listIterator()

        for (i in 0..bracket.depth) {
            val finalLevel = bracket.getFinalLevel(i)
            a(href = "#${finalLevel}") { +finalLevel }

            // the following is for expanding to show all the rounds in a list
            +" (expand "

            input(type = InputType.checkBox) {
                attributes["id"] = "checkbox"
                attributes["checked"] = ""
            }

            +")"

            // add a link to each round that is in this bracket
            div() {
                attributes["id"] = "hidden"

                if (rounds.hasNext()) {
                    val round = rounds.next()
                    getSidebarRound(round, bracket)

                    while (rounds.hasNext()) {
                        val next = rounds.next()
                        if (next.shallowness != round.shallowness) {
                            rounds.previous()
                            break
                        }
                        getSidebarRound(next, bracket)

                    }
                }
            }

            br()

        }
    }

}


/*
* display a link alongside an icon which shows if the round has been resolved,
* voting is in progress or voting is locked.
* these are cached to save as many DB calls as possible
*/
fun DIV.getSidebarRound(round: Round, bracket: Bracket) {
    val lookup = "bracket.${bracket.id}.sidebar.${round.id}"
    var html: String? = jedis.get(lookup)

    // get html, either generate or get from redis
    if (html == null) {
        val stringBuffer = StringBuffer()
        stringBuffer.appendHTML().div {
            displaySidebarRound(round)
        }
        jedis.set(lookup, stringBuffer.toString())
        html = stringBuffer.toString()
    }

    unsafe {
        +html
    }

}


/*
* display a link alongside an icon which shows if the round has been resolved,
* voting is in progress or voting is locked
*/
fun DIV.displaySidebarRound(round: Round) {
    a(href = "#${round.number}") { +"Round ${round.number}" }
    if (round.resolved) {
        +"✅"
    } else if (round.hasEntrants()) {
        +"❌"
    } else {
        +"🔒"
    }
    br()
}
