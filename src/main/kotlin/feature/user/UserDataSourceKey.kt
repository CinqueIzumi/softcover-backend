package nl.rhaydus.feature.user

import io.ktor.server.application.*
import io.ktor.util.*

private val UserDataSourceKey = AttributeKey<UserDataSource>("UserDataSourceKey")

val Application.userDataSource: UserDataSource
    get() = attributes[UserDataSourceKey]

fun Application.setUserDataSource(dataSource: UserDataSource) =
    attributes.put(UserDataSourceKey, dataSource)