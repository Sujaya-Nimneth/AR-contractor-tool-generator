package com.arcontractor.estimator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        SurfaceEntity::class,
        CutoutEntity::class,
        MaterialSpecEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun surfaceDao(): SurfaceDao
    abstract fun materialCatalogDao(): MaterialCatalogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private const val DATABASE_NAME = "ar-contractor.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN contractorName TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN contractorContact TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN contractorLicense TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'USD'"
                )
            }
        }
    }
}
