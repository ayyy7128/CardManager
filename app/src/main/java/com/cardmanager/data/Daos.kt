package com.cardmanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── 分组 DAO ────────────────────────────────────────────
@Dao
interface CardGroupDao {
    @Query("SELECT * FROM card_groups ORDER BY sortOrder ASC")
    fun getAllGroups(): Flow<List<CardGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: CardGroup)

    @Update
    suspend fun update(group: CardGroup)

    @Delete
    suspend fun delete(group: CardGroup)

    @Query("DELETE FROM cards WHERE groupId = :groupId")
    suspend fun deleteCardsInGroup(groupId: String)

    @Query("SELECT * FROM cards WHERE groupId = :groupId")
    suspend fun getCardsInGroupOnce(groupId: String): List<Card>

    @Query("DELETE FROM card_groups")
    suspend fun deleteAll()
}

// ─── 卡片 DAO ─────────────────────────────────────────────
@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY sortOrder ASC")
    fun getAllCards(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE groupId = :groupId ORDER BY sortOrder ASC")
    fun getCardsInGroup(groupId: String): Flow<List<Card>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: Card)

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("UPDATE cards SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("DELETE FROM cards")
    suspend fun deleteAll()
}

// ─── 任务 DAO ─────────────────────────────────────────────
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

// ─── 储蓄罐 DAO ───────────────────────────────────────────
@Dao
interface PiggyDao {
    @Query("SELECT * FROM piggy_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<PiggyEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PiggyEntry)

    @Update
    suspend fun update(entry: PiggyEntry)

    @Delete
    suspend fun delete(entry: PiggyEntry)

    @Query("DELETE FROM piggy_entries WHERE cardId = :cardId")
    suspend fun deleteForCard(cardId: String)

    @Query("DELETE FROM piggy_entries")
    suspend fun deleteAll()
}

// ─── 设置 DAO ─────────────────────────────────────────────
@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): AppSetting?

    @Query("SELECT * FROM settings")
    suspend fun getAllOnce(): List<AppSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSetting)
}
