package com.yourname.colornest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourname.colornest.data.dao.CategoryDao
import com.yourname.colornest.data.dao.PageDao
import com.yourname.colornest.data.dao.SettingDao
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.Setting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Page::class, Category::class, Setting::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ColorNestDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun settingDao(): SettingDao

    companion object {
        /**
         * Version 1 contained the removed AI-only LearnedPattern table and
         * temporary AI fields on pages. Preserve user-created page data while
         * removing only those obsolete AI fields.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pages_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        categoryId INTEGER,
                        complexity TEXT NOT NULL,
                        time TEXT NOT NULL,
                        tool TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        isFavorite INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        progress INTEGER NOT NULL,
                        isUncategorized INTEGER NOT NULL,
                        tags TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO pages_new (
                        id, title, categoryId, complexity, time, tool, imageUrl,
                        isFavorite, isCompleted, progress, isUncategorized, tags, createdAt
                    )
                    SELECT
                        id, title, categoryId, complexity, time, tool, imageUrl,
                        isFavorite, isCompleted, progress, isUncategorized, tags, createdAt
                    FROM pages
                """.trimIndent())
                db.execSQL("DROP TABLE pages")
                db.execSQL("ALTER TABLE pages_new RENAME TO pages")
                db.execSQL("DROP TABLE IF EXISTS learned_patterns")
            }
        }

        /** Add nullable Drive identifiers without rebuilding or discarding existing rows. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN driveFileId TEXT")
                db.execSQL("ALTER TABLE categories ADD COLUMN driveFolderId TEXT")
            }
        }

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Never silently erase a user's coloring library on upgrade.
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
