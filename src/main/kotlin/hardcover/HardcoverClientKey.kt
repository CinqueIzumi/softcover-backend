package nl.rhaydus.hardcover

import io.ktor.server.application.*
import io.ktor.util.*

private val HardcoverClientKey = AttributeKey<HardcoverClient>("HardcoverClient")

val Application.hardcoverClient: HardcoverClient
    get() = attributes[HardcoverClientKey]

fun Application.setHardcoverClient(client: HardcoverClient) =
    attributes.put(HardcoverClientKey, client)