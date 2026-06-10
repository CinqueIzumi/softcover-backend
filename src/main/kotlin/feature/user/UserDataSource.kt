package nl.rhaydus.feature.user

interface UserDataSource {
    suspend fun resolveUser(token: String): HardcoverUser?
}