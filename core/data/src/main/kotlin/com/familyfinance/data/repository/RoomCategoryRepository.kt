package com.familyfinance.data.repository

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.toDomain
import com.familyfinance.data.db.toEntity
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomCategoryRepository @Inject constructor(
    private val db: FinanceDatabase,
    private val device: DeviceIdentity,
    private val changes: LocalChanges,
) : CategoryRepository {
    private val dao get() = db.categoryDao()

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUsage(): Flow<Map<String, Int>> =
        db.transactionDao().observeCategoryUsage().map { rows -> rows.associate { it.categoryUid to it.count } }

    override suspend fun save(category: Category) = db.withTransaction {
        val stamped = category.copy(updatedAt = nextVersion(dao.get(category.uid)?.updatedAt), deviceId = device.deviceId)
        dao.upsert(stamped.toEntity())
        changes.record(stamped)
    }

    override suspend fun delete(uid: String): Boolean = db.withTransaction {
        val stored = dao.get(uid) ?: return@withTransaction false
        if (db.transactionDao().countForCategory(uid) > 0 || dao.childCount(uid) > 0) return@withTransaction false
        val deleted = stored.copy(updatedAt = nextVersion(stored.updatedAt), deleted = true, deviceId = device.deviceId)
        dao.upsert(deleted)
        changes.record(deleted.toDomain())
        true
    }

    override suspend fun merge(fromUid: String, toUid: String): Int = db.withTransaction {
        require(fromUid != toUid) { "Cannot merge a category into itself" }
        val from = dao.get(fromUid) ?: return@withTransaction 0
        require(dao.childCount(fromUid) == 0) { "Merge a category without subcategories" }
        // Each moved operation is a normal edit: newer version, this device, queued for the other phone.
        val moved = db.transactionDao().withCategory(fromUid).map {
            it.copy(categoryUid = toUid, updatedAt = nextVersion(it.updatedAt), deviceId = device.deviceId)
        }
        moved.chunked(500).forEach { db.transactionDao().upsert(it) }
        val hidden = from.copy(archived = true, updatedAt = nextVersion(from.updatedAt), deviceId = device.deviceId)
        dao.upsert(hidden)
        changes.record(moved.map { it.toDomain() } + hidden.toDomain())
        moved.size
    }
}
