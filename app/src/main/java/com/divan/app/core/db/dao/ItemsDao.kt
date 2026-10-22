package com.divan.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.divan.app.core.db.entity.ItemEntry
import com.divan.app.core.db.entity.ItemGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemsDao {
    @Query("SELECT * FROM item_groups ORDER BY sort, name")
    fun groups(): Flow<List<ItemGroup>>

    @Query("SELECT * FROM item_entries WHERE isDeleted = 0 ORDER BY name")
    fun items(): Flow<List<ItemEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(g: ItemGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(i: ItemEntry)

    @Query("UPDATE item_entries SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteItem(id: String)

    @Query("UPDATE item_entries SET isDeleted = 1 WHERE groupId = :gid")
    suspend fun softDeleteItemsOfGroup(gid: String)

    @Query("DELETE FROM item_groups WHERE id = :gid")
    suspend fun deleteGroup(gid: String)
}
