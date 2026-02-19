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
            .fallbackToDestructiveMigration(true).build()  // TODO: Add migration support
    }

    @Provides
    fun provideAppsDao(db: AppsDatabase): AppsDao = db.appsDao()

    @Provides
    @Singleton
    fun provideAppsRepository(db: AppsDatabase): AppsRepository {
        return RoomAppsRepository(db, db.appsDao())
    }
}
