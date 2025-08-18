package com.example.inventoryscanner.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class ItemDbStatus {
    AVAILABLE, CHECKED_OUT
}

@Entity(
    tableName = "inventory_items",
    indices = [Index(value = ["code"], unique = true)]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "name") val name: String? = null,
    @ColumnInfo(name = "category") val category: String? = null,
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "status") val status: ItemDbStatus = ItemDbStatus.AVAILABLE,
    @ColumnInfo(name = "taken_count") val takenCount: Int = 0,
    @ColumnInfo(name = "last_action_ts") val lastActionTs: Long = 0,
    @ColumnInfo(name = "quantity") val quantity: Int = 0
)

enum class LogAction {
    CREATE, TAKE, RETURN, AUTO_RETURN
}

@Entity(
    tableName = "inventory_logs",
    indices = [Index(value = ["code"])]
)
data class InventoryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "action") val action: LogAction,
    @ColumnInfo(name = "ts") val ts: Long
)

@Dao
interface InventoryItemDao {

    @Query("SELECT * FROM inventory_items WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: InventoryItemEntity): Long

    @Update
    suspend fun update(item: InventoryItemEntity)

    @Query("""
        UPDATE inventory_items
        SET status = :status,
            taken_count = :takenCount,
            last_action_ts = :lastActionTs
        WHERE code = :code
    """)
    suspend fun updateStatus(
        code: String,
        status: ItemDbStatus,
        takenCount: Int,
        lastActionTs: Long
    )

    @Query("SELECT * FROM inventory_items ORDER BY code")
    suspend fun getAll(): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items ORDER BY code")
    fun observeAll(): Flow<List<InventoryItemEntity>>

    // Сброс счётчика для одного предмета
    @Query("UPDATE inventory_items SET taken_count = 0 WHERE code = :code")
    suspend fun resetTakenCount(code: String)

    // Сброс счётчиков для всех
    @Query("UPDATE inventory_items SET taken_count = 0")
    suspend fun resetAllTakenCounts()

    // Удаление предмета
    @Query("DELETE FROM inventory_items WHERE code = :code")
    suspend fun deleteByCode(code: String)

    // Удаление логов предмета
    @Query("DELETE FROM inventory_logs WHERE code = :code")
    suspend fun deleteLogsForCode(code: String)

    // Количество
    @Query("UPDATE inventory_items SET quantity = :quantity WHERE code = :code")
    suspend fun setQuantity(code: String, quantity: Int)

    @Query("UPDATE inventory_items SET quantity = quantity + :delta WHERE code = :code")
    suspend fun addQuantity(code: String, delta: Int)
}

@Dao
interface InventoryLogDao {
    @Insert
    suspend fun insert(log: InventoryLogEntity)

    @Query("SELECT * FROM inventory_logs WHERE code = :code ORDER BY ts DESC")
    suspend fun logsForCode(code: String): List<InventoryLogEntity>

    @Query("SELECT * FROM inventory_logs ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<InventoryLogEntity>
}