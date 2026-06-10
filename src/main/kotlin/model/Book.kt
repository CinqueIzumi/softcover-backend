package nl.rhaydus.model

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/books")
data class Book(val title: String = "", val author: String = "")