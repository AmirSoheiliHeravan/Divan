package com.divan.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** مجموعه اقلام (خواربار، قبوض، حمل‌ونقل…) برای انتخاب اقلام تراکنش/فاکتور */
@Entity(tableName = "item_groups")
data class ItemGroup(
    @PrimaryKey val id: String,
    val name: String,
    val color: String = "#245C49",
    val sort: Int = 0,
)

/** یک قلم (آیتم) داخل هر مجموعه با قیمت پیش‌فرض */
@Entity(tableName = "item_entries", indices = [Index("groupId")])
data class ItemEntry(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val priceMinor: Long = 0,
    val isDeleted: Boolean = false,
)
