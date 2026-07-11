package dev.re7gog.b_sideloader.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.data.local.AppsDatabase
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import dev.re7gog.b_sideloader.data.repository.RoomAppsRepository
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppsDatabaseModule {
    @Provides
    @Singleton
    fun provideAppsDatabase(@ApplicationContext context: Context): AppsDatabase {
        return Room.databaseBuilder(context, AppsDatabase::class.java, "apps_database")
            // Not released yet: recreate the DB on schema change during development. Once released,
            // add .addMigrations(AppsDatabase.MIGRATION_1_2, ...) here so real user data survives.
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideAppsDao(db: AppsDatabase): AppsDao = db.appsDao()

    @Provides
    @Singleton
    fun provideAppsRepository(db: AppsDatabase, dao: AppsDao): AppsRepository {
        return RoomAppsRepository(db, dao)
    }
}
