package com.voting

import com.voting.db.dao.Bracket
import com.voting.db.dao.Entry
import com.voting.db.dao.Round
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import redis.clients.jedis.Jedis

// jedis is our cache
val jedis = Jedis()

/*
 * cache all the round responses
 */
fun BODY.getRoundHTML(round: Round, bracket: Bracket) {
    val lookup = "bracket.${bracket.id}.round.${round.id}"
    var html: String? = jedis.get(lookup)

    // get html, either generate or get from redis
    if (html == null) {
        val stringBuffer = StringBuffer()
        stringBuffer.appendHTML().body {
            genRoundHTML(round)
        }
        jedis.set(lookup, stringBuffer.toString())
        html = stringBuffer.toString()
    }

    unsafe {
        +html
    }

}


/*
 * Generate all the HTML for a single round
 */
fun BODY.genRoundHTML(round: Round) {
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

    table {
        style = "padding: 10px;"
        tr {
            td {
                genEntryHTML(round, 0)
            }
            td {
                style = "padding: 10px;"
                h4 { +"VS" }
            }
            td {
                genEntryHTML(round, 1)
            }
        }
    }
    br()

}

/*
 * Generate all the HTML for a single entry
 * entrant is 0 for left, 1 for right
 */
fun kotlinx.html.TD.genEntryHTML(round: Round, entrant: Int) {

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
            br()
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
            +"0 Votes"
        } else {
            if (votes.size == 1) {
                +"1 Vote: "
            }
            else {
                +"${votes.size} Votes: "
            }

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
