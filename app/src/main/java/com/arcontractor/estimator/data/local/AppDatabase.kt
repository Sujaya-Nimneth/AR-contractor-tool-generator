package com.arcontractor.estimator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProjectEntity::class,
        SurfaceEntity::class,
        CutoutEntity::class,
        MaterialSpecEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun surfaceDao(): SurfaceDao
    abstract fun materialCatalogDao(): MaterialCatalogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).build()

        private const val DATABASE_NAME = "ar-contractor.db"
    }
}
