package nl.rhaydus.feature.books

import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.plugins.requireIntListQueryParameter
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
        val ids = call.requireIntListQueryParameter("ids")

        val books = bookDataSource.getBooksByIds(
            ids = ids,
            token = call.userPrincipal.token,
        )

        return@get call.respond(books)
    }

    get("books/{id}/editions") {
        val bookId = call.requireIntParameter("id")

        val editions = bookDataSource.getEditionsByBookId(
            bookId = bookId,
            token = call.userPrincipal.token,
        )

        return@get call.respond(editions)
    }
}