package com.voting

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import kotlinx.css.*
import kotlinx.css.properties.*


fun Route.css() {
    get("/style.css") {
        call.respondCss {
            body {
                backgroundColor = Color("#2C2F33")
                color = Color.white
                lineHeight = LineHeight("150%")
            }

            "*" {
                margin = "auto"
                textAlign = TextAlign.center
            }

            a {
                color = Color("#7289DA")
            }

            ".sidenav" {
                height = LinearDimension("100%")
                width = 250.px
                position = Position.fixed
                zIndex = 1
                top = LinearDimension("0")
                left = LinearDimension("0")
                backgroundColor = Color("#23272A")
                overflowX = Overflow.hidden
                paddingTop = 20.px
            }

            ".main" {
                marginLeft = 160.px
                padding = "0px 10px"
            }

            // for the hidden lists in the side bars
            "#hidden" {
                display = Display.none
            }

            // for the hidden lists in the side bars
            "#checkbox:checked + #hidden" {
                display = Display.block
            }


        }
    }

}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
