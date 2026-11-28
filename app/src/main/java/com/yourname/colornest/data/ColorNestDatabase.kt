package com.yourname.colornest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourname.colornest.data.dao.CategoryDao
import com.yourname.colornest.data.dao.PageDao
import com.yourname.colornest.data.dao.SettingDao
import com.yourname.colornest.data.dao.TagDao
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.Setting
import com.yourname.colornest.data.model.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Page::class, Category::class, Setting::class, TagEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ColorNestDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun settingDao(): SettingDao
    abstract fun tagDao(): TagDao

    companion object {
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
                db.execSQL("DELETE FROM settings WHERE key IN ('aiAutoTagUploads', 'aiCategoryLearning')")
                db.execSQL("DROP TABLE IF EXISTS learned_patterns")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN driveFileId TEXT")
                db.execSQL("ALTER TABLE categories ADD COLUMN driveFolderId TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN thumbnailUrl TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN isPreviewOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pages ADD COLUMN driveThumbnailLink TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN whiteBackgroundProcessed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pages ADD COLUMN whiteBackgroundVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pages ADD COLUMN syncId TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN aiSuggestedTags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pages ADD COLUMN aiScannedAt INTEGER")
                db.execSQL("ALTER TABLE pages ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE pages SET updatedAt = createdAt WHERE updatedAt = 0")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags (name)")
            }
        }


        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove obsolete AI-only metadata without touching user pages or files.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pages_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        categoryId INTEGER,
                        complexity TEXT NOT NULL,
                        time TEXT NOT NULL,
                        tool TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        thumbnailUrl TEXT,
                        isPreviewOnly INTEGER NOT NULL,
                        driveThumbnailLink TEXT,
                        isFavorite INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        progress INTEGER NOT NULL,
                        isUncategorized INTEGER NOT NULL,
                        tags TEXT NOT NULL,
                        driveFileId TEXT,
                        whiteBackgroundProcessed INTEGER NOT NULL,
                        whiteBackgroundVersion INTEGER NOT NULL,
                        syncId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO pages_new (
                        id, title, categoryId, complexity, time, tool, imageUrl,
                        thumbnailUrl, isPreviewOnly, driveThumbnailLink, isFavorite,
                        isCompleted, progress, isUncategorized, tags, driveFileId,
                        whiteBackgroundProcessed, whiteBackgroundVersion, syncId,
                        createdAt, updatedAt
                    )
                    SELECT
                        id, title, categoryId, complexity, time, tool, imageUrl,
                        thumbnailUrl, isPreviewOnly, driveThumbnailLink, isFavorite,
                        isCompleted, progress, isUncategorized, tags, driveFileId,
                        whiteBackgroundProcessed, whiteBackgroundVersion, syncId,
                        createdAt, updatedAt
                    FROM pages
                """.trimIndent())
                db.execSQL("DROP TABLE pages")
                db.execSQL("ALTER TABLE pages_new RENAME TO pages")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
