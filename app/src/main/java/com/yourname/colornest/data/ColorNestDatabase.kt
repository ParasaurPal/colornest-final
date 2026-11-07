package com.yourname.colornest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourname.colornest.data.dao.CategoryDao
import com.yourname.colornest.data.dao.LearnedPatternDao
import com.yourname.colornest.data.dao.PageDao
import com.yourname.colornest.data.dao.SettingDao
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.LearnedPattern
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.Setting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Page::class, Category::class, LearnedPattern::class, Setting::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ColorNestDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun learnedPatternDao(): LearnedPatternDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: ColorNestDatabase? = null

        fun getDatabase(context: Context): ColorNestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ColorNestDatabase::class.java,
                    "colornest_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val initialCategories = listOf(
                            Category(name = "Animals", parentId = null),
                            Category(name = "Mandalas & Patterns", parentId = null),
                            Category(name = "Fantasy & Mythical", parentId = null),
                            Category(name = "Botanical & Nature", parentId = null),
                            Category(name = "Architecture", parentId = null)
                        )
                        database.categoryDao().insertCategories(initialCategories)
                    }
                }
            }
        }
    }
}
