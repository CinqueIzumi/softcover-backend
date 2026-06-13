package nl.rhaydus.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import nl.rhaydus.core.model.SoftcoverException
import nl.rhaydus.core.model.UserPrincipal
import nl.rhaydus.feature.books.BookDataSourceImpl
import nl.rhaydus.feature.books.bookRoutes
import nl.rhaydus.feature.editions.EditionDataSourceImpl
import nl.rhaydus.feature.editions.editionRoutes
import nl.rhaydus.feature.settings.SettingsRepositoryImpl
import nl.rhaydus.feature.settings.settingsRoutes
import nl.rhaydus.feature.user.meRoutes
import nl.rhaydus.feature.user.userDataSource
import nl.rhaydus.hardcover.hardcoverClient

fun Application.configureRouting() {
    val repo = SettingsRepositoryImpl(db = database)
    val bookDataSource = BookDataSourceImpl(client = hardcoverClient)
    val editionDataSource = EditionDataSourceImpl(hardcoverClient)

    routing {
        authenticate("external") {
            settingsRoutes(repo = repo)

            meRoutes(dataSource = userDataSource)

            bookRoutes(bookDataSource = bookDataSource)

            editionRoutes(editionDataSource = editionDataSource)
        }
    }
}

val ApplicationCall.userPrincipal: UserPrincipal
    get() = principal<UserPrincipal>() ?: throw SoftcoverException.Unauthorized()

fun ApplicationCall.requireParameter(name: String): String =
    parameters[name] ?: throw SoftcoverException.BadRequest("Missing path parameter: $name")

fun ApplicationCall.requireIntParameter(name: String): Int =
    parameters[name]?.toIntOrNull() ?: throw SoftcoverException.BadRequest("Invalid path parameter: $name")

fun ApplicationCall.requireIntListQueryParameter(name: String): List<Int> =
    request.queryParameters[name]
        ?.split(",")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() }
        ?: throw SoftcoverException.BadRequest("Invalid query parameter: $name")