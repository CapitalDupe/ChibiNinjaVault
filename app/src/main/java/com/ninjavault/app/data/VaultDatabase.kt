package com.ninjavault.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromFileType(type: VaultFileType): String = type.name

    @TypeConverter
    fun toFileType(value: String): VaultFileType = VaultFileType.valueOf(value)
}

@Database(entities = [VaultFileEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "ninja_run_vault.db"
                ).build().also { INSTANCE = it }
            }
    }
}
