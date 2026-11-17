package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.ProxyProtocol

class ProxyTypeConverters {
    @TypeConverter
    fun fromProtocol(protocol: ProxyProtocol): String = protocol.name

    @TypeConverter
    fun toProtocol(value: String): ProxyProtocol =
        ProxyProtocol.entries.firstOrNull { it.name == value } ?: ProxyProtocol.SOCKS5
}

@Database(
    entities = [ProxyProfileEntity::class, PerAppRuleEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ProxyTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "proxygate_db"
                ).build().also { instance = it }
            }
        }
    }
}
