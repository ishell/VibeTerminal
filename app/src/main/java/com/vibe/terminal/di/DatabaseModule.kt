package com.vibe.terminal.di

import android.content.Context
import androidx.room.Room
import com.vibe.terminal.data.local.dao.MachineDao
import com.vibe.terminal.data.local.dao.ProjectDao
import com.vibe.terminal.data.local.database.VibeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VibeDatabase {
        return Room.databaseBuilder(
            context,
            VibeDatabase::class.java,
            "vibe_terminal.db"
        ).build()
    }

    @Provides
    fun provideMachineDao(database: VibeDatabase): MachineDao {
        return database.machineDao()
    }

    @Provides
    fun provideProjectDao(database: VibeDatabase): ProjectDao {
        return database.projectDao()
    }
}
