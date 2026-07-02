package com.example.aicreationassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.aicreationassistant.data.local.dao.FavoriteDao
import com.example.aicreationassistant.data.local.dao.HistoryDao
import com.example.aicreationassistant.data.local.entity.FavoriteEntity
import com.example.aicreationassistant.data.local.entity.HistoryEntity

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
}
