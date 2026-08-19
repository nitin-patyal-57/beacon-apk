package com.walnut.beaconfinder.data.repository

import com.walnut.beaconfinder.data.db.FavoriteDao
import com.walnut.beaconfinder.data.db.FavoriteEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val dao: FavoriteDao
) {
    suspend fun getAll(): List<FavoriteEntity> = dao.getAll()

    suspend fun getByAddress(address: String): FavoriteEntity? = dao.getByAddress(address)

    suspend fun toggle(address: String, name: String?, protocol: String?): Boolean {
        val existing = dao.getByAddress(address)
        return if (existing != null) {
            dao.delete(existing)
            false
        } else {
            dao.insert(FavoriteEntity(address = address, name = name, protocol = protocol))
            true
        }
    }

    suspend fun isFavorite(address: String): Boolean = dao.getByAddress(address) != null
}
