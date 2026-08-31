package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.DownloadStatus
import com.example.data.model.FileType

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = runCatching {
        DownloadStatus.valueOf(value)
    }.getOrDefault(DownloadStatus.QUEUED)

    @TypeConverter
    fun fromFileType(fileType: FileType): String = fileType.name

    @TypeConverter
    fun toFileType(value: String): FileType = runCatching {
        FileType.valueOf(value)
    }.getOrDefault(FileType.OTHER)
}

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omniget_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
