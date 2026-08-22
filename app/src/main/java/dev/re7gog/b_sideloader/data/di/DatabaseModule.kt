package dev.re7gog.b_sideloader.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.data.local.AppsDatabase
import dev.re7gog.b_sideloader.data.local.SelfAppSeed
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppsDatabase(@ApplicationContext context: Context): AppsDatabase =
        Room.databaseBuilder(context, AppsDatabase::class.java, AppsDatabase.DB_NAME)
            .addMigrations(*AppsDatabase.MIGRATIONS)
            // A database created at the current version runs no migration, so the app's own row
            // has to be seeded here too; the 1 -> 2 migration covers everyone else.
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = SelfAppSeed.insertInto(db)
            })
            // No destructive fallback: with schemas exported and migrations tested, a missing
            // migration should fail loudly in development rather than wipe a user's app list.
            .build()

    @Provides
    fun provideAppsDao(database: AppsDatabase): AppsDao = database.appsDao()
}
