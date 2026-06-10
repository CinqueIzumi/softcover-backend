package nl.rhaydus

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/books") {
            val books = listOf(
                Book(title = "Salem's lot", author = "Stephen King"),
                Book(title = "Salem's lot", author = "Stephen King"),
            )

            call.respond(books)
        }
    }
}