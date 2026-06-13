package nl.rhaydus.core.model

sealed class SoftcoverException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class ResourceNotFound(message: String = "Resource not found") : SoftcoverException(message)
    class Unauthorized(message: String = "Unauthorized") : SoftcoverException(message)
    class BadRequest(message: String = "Bad request") : SoftcoverException(message)
}

fun <T : Any> T?.orNotFound(message: String = "Resource not found"): T =
    this ?: throw SoftcoverException.ResourceNotFound(message)
