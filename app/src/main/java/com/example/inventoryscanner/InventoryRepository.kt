package com.example.inventoryscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class InventoryRepository(
    private val itemDao: InventoryItemDao,
    private val logDao: InventoryLogDao
) {

    fun observeItems(): Flow<List<InventoryItemEntity>> = itemDao.observeAll()

    suspend fun getItem(code: String): InventoryItemEntity? =
        withContext(Dispatchers.IO) { itemDao.getByCode(code) }

    suspend fun createItem(code: String, now: Long): InventoryItemEntity =
        withContext(Dispatchers.IO) {
            val entity = InventoryItemEntity(
                code = code,
                status = ItemDbStatus.AVAILABLE,
                takenCount = 0,
                lastActionTs = now,
                quantity = 0
            )
            itemDao.insert(entity)
            logDao.insert(
                InventoryLogEntity(
                    code = code,
                    action = LogAction.CREATE,
                    ts = now
                )
            )
            entity
        }

    suspend fun markTaken(item: InventoryItemEntity, now: Long): InventoryItemEntity =
        withContext(Dispatchers.IO) {
            val newCount = item.takenCount + 1
            itemDao.updateStatus(
                code = item.code,
                status = ItemDbStatus.CHECKED_OUT,
                takenCount = newCount,
                lastActionTs = now
            )
            logDao.insert(
                InventoryLogEntity(
                    code = item.code,
                    action = LogAction.TAKE,
                    ts = now
                )
            )
            item.copy(
                status = ItemDbStatus.CHECKED_OUT,
                takenCount = newCount,
                lastActionTs = now
            )
        }

    suspend fun markReturned(item: InventoryItemEntity, now: Long, auto: Boolean): InventoryItemEntity =
        withContext(Dispatchers.IO) {
            itemDao.updateStatus(
                code = item.code,
                status = ItemDbStatus.AVAILABLE,
                takenCount = item.takenCount,
                lastActionTs = now
            )
            logDao.insert(
                InventoryLogEntity(
                    code = item.code,
                    action = if (auto) LogAction.AUTO_RETURN else LogAction.RETURN,
                    ts = now
                )
            )
            item.copy(status = ItemDbStatus.AVAILABLE, lastActionTs = now)
        }

    suspend fun resetTakenCount(code: String) =
        withContext(Dispatchers.IO) { itemDao.resetTakenCount(code) }

    suspend fun resetAllTakenCounts() =
        withContext(Dispatchers.IO) { itemDao.resetAllTakenCounts() }

    suspend fun deleteItem(code: String, deleteLogs: Boolean) =
        withContext(Dispatchers.IO) {
            if (deleteLogs) itemDao.deleteLogsForCode(code)
            itemDao.deleteByCode(code)
        }

    suspend fun setQuantity(code: String, quantity: Int) =
        withContext(Dispatchers.IO) { itemDao.setQuantity(code, quantity) }

    suspend fun incQuantity(code: String, delta: Int = 1) =
        withContext(Dispatchers.IO) { itemDao.addQuantity(code, delta) }
    suspend fun decQuantity(code: String) =
        withContext(Dispatchers.IO) {
            val item = itemDao.getByCode(code)
            if (item != null && item.quantity > 0) {
                itemDao.addQuantity(code, -1)
            }
        }
}