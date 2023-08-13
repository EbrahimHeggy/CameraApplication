package dev.ebrahim.cameraapplication.databsae


import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow



@Entity(tableName = "image_table")
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uri: String,
    val captureTime: Long
)



@Dao
interface ImageDao {

    @Insert
    suspend fun insertImage(imageEntity: ImageEntity)

    @Query("SELECT * FROM image_table ORDER BY captureTime DESC")
    fun getAllImages(): Flow<List<ImageEntity>>
}


@Database(entities = [ImageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
}
