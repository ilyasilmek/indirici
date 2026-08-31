package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadStatus
import com.example.data.model.FileType
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE fileType = :fileType ORDER BY createdAt DESC")
    fun getDownloadsByType(fileType: FileType): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadByIdDirect(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun getQueuedDownloadsDirect(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getCurrentlyDownloadingDirect(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadEntity>): List<Long>

    @Update
    suspend fun update(item: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, speedBytesPerSec = 0 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBytesPerSec = :speed, etaSeconds = :eta, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, downloaded: Long, total: Long, speed: Long, eta: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET status = 'COMPLETED', completedAt = :completedAt, speedBytesPerSec = 0, filePath = :filePath WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long, filePath: String)

    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :error, speedBytesPerSec = 0 WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
