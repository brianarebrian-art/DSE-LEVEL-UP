package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    QuestionEntity::class,
    UserProgressEntity::class,
    MistakeEntity::class,
    CompletedQuestionEntity::class,
    BadgeEntity::class
  ],
  version = 2,
  exportSchema = false
)
abstract class DseDatabase : RoomDatabase() {

  abstract fun dseDao(): DseDao

  companion object {
    @Volatile
    private var INSTANCE: DseDatabase? = null

    fun getDatabase(context: Context): DseDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          DseDatabase::class.java,
          "dse_level_up_database"
        )
        .fallbackToDestructiveMigration()
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
