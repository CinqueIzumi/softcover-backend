package nl.rhaydus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)