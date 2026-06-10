package nl.rhaydus.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import nl.rhaydus.core.model.ErrorResponse
import nl.rhaydus.core.model.HardcoverException
import nl.rhaydus.core.model.SoftcoverException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<SoftcoverException> { call: ApplicationCall, cause: SoftcoverException ->
            val status = when (cause) {
                is SoftcoverException.ResourceNotFound -> HttpStatusCode.NotFound
            }

            call.respond(status, ErrorResponse(cause.message ?: "Unexpected error"))
        }

        exception<HardcoverException> { call: ApplicationCall, cause: HardcoverException ->
            val status = when (cause) {
                is HardcoverException.GraphQLError -> HttpStatusCode.UnprocessableEntity
                is HardcoverException.Unauthorized -> HttpStatusCode.Unauthorized

                is HardcoverException.EmptyResponse,
                is HardcoverException.Unavailable,
                    -> HttpStatusCode.BadGateway
            }

            call.respond(status, ErrorResponse(cause.message ?: "Unexpected error"))
        }
    }
}