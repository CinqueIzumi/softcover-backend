package nl.rhaydus.feature.books

import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.core.model.SoftcoverException
import nl.rhaydus.plugins.requireIntParameter
import nl.rhaydus.plugins.userPrincipal

fun Route.bookRoutes(bookDataSource: BookDataSource) {
    get("books/{id}") {
        val id = call.requireIntParameter("id")

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
            ?: throw SoftcoverException.BadRequest("Invalid query parameter: ids")

        val books = bookDataSource.getBooksByIds(
            ids = ids,
            token = call.userPrincipal.token,
        )

        return@get call.respond(books)
    }
}