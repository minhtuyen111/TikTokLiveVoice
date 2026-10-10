package com.tlv.app.history

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Chỉ lưu số liệu tổng hợp của phiên, không lưu nội dung bình luận. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val channel: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val received: Int = 0,
    val spoken: Int = 0,
    val lastState: String = "",
)

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: SessionEntity): Long
    @Update suspend fun update(s: SessionEntity)
    @Query("SELECT * FROM sessions WHERE ownerId = :owner ORDER BY startedAt DESC LIMIT 100")
    fun observe(owner: String): Flow<List<SessionEntity>>
    @Query("DELETE FROM sessions WHERE ownerId = :owner")
    suspend fun clear(owner: String)
    @Query("DELETE FROM sessions WHERE ownerId = :owner AND id NOT IN (SELECT id FROM sessions WHERE ownerId = :owner ORDER BY startedAt DESC LIMIT 100)")
    suspend fun trim(owner: String)
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun sessions(): SessionDao

    companion object {
        fun create(c: Context) = Room.databaseBuilder(c, AppDb::class.java, "tlv.db")
            .fallbackToDestructiveMigration().build()
    }
}
