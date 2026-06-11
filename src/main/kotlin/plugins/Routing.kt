package nl.rhaydus.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import nl.rhaydus.feature.books.BookDataSourceImpl
import nl.rhaydus.feature.books.bookRoutes
import nl.rhaydus.feature.settings.SettingsRepositoryImpl
import nl.rhaydus.feature.settings.settingsRoutes
import nl.rhaydus.feature.user.meRoutes
import nl.rhaydus.feature.user.userDataSource
import nl.rhaydus.hardcover.hardcoverClient

fun Application.configureRouting() {
    val repo = SettingsRepositoryImpl(db = database)
    val bookService = BookDataSourceImpl(client = hardcoverClient)

    routing {
        authenticate("external") {
            settingsRoutes(repo = repo)

            meRoutes(dataSource = userDataSource)

            bookRoutes(bookDataSource = bookService)
        }
    }
}
