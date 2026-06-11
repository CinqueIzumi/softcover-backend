package nl.rhaydus.feature.books

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.plugins.userPrincipal

fun Route.bookRoutes(bookDataSource: BookDataSource) {
    get("books/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest)

        val book = bookDataSource.getBookById(
            id = id,
            token = call.userPrincipal.token,
        )

        return@get call.respond(book)
    }

    get("books") {
        val ids = call.request.queryParameters["ids"]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: return@get call.respond(HttpStatusCode.BadRequest)

        val books = bookDataSource.getBooksByIds(
            ids = ids,
            token = call.userPrincipal.token,
        )

        return@get call.respond(books)
    }
}