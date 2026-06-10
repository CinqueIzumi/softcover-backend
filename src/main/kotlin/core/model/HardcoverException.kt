package nl.rhaydus.core.model

sealed class HardcoverException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class GraphQLError(message: String) : HardcoverException(message)

    class EmptyResponse : HardcoverException("Hardcover returned no data")

    class Unauthorized : HardcoverException("Hardcover rejected the credentials")

    class Unavailable(cause: Throwable) : HardcoverException("Could not reach Hardcover", cause)
}