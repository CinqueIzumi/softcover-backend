package nl.rhaydus.feature.editions

import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.plugins.requireIntListQueryParameter
import nl.rhaydus.plugins.requireIntParameter
import nl.rhaydus.plugins.userPrincipal

fun Route.editionRoutes(editionDataSource: EditionDataSource) {
    get("editions/{editionId}/book-id") {
        val editionId = call.requireIntParameter("editionId")

        val bookId = editionDataSource.getBookIdByEditionId(
            editionId = editionId,
            token = call.userPrincipal.token,
        )

        val response = BookIdResponse(bookId = bookId)

        return@get call.respond(response)
    }

    get("editions") {
        val editionIds = call.requireIntListQueryParameter(name = "ids")

        val editions = editionDataSource.getEditionsByIds(
            ids = editionIds,
            token = call.userPrincipal.token,
        )

        return@get call.respond(editions)
    }
}