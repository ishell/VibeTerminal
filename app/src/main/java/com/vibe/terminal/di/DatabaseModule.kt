package com.vibe.terminal.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vibe.terminal.data.local.dao.ConversationDao
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 创建对话文件缓存表
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_files (
                    id TEXT PRIMARY KEY NOT NULL,
                    projectId TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    fileSize INTEGER NOT NULL,
                    lastModified INTEGER NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_files_projectId ON conversation_files(projectId)")

            // 创建对话会话表
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_sessions (
                    sessionId TEXT PRIMARY KEY NOT NULL,
                    fileId TEXT NOT NULL,
                    projectPath TEXT NOT NULL,
                    slug TEXT,
                    startTime INTEGER NOT NULL,
                    endTime INTEGER NOT NULL,
                    totalUserMessages INTEGER NOT NULL,
                    totalAssistantMessages INTEGER NOT NULL,
                    FOREIGN KEY (fileId) REFERENCES conversation_files(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_sessions_fileId ON conversation_sessions(fileId)")

            // 创建对话段落表
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_segments (
                    id TEXT PRIMARY KEY NOT NULL,
                    sessionId TEXT NOT NULL,
                    userMessage TEXT NOT NULL,
                    userMessagePreview TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    hasThinking INTEGER NOT NULL,
                    hasToolUse INTEGER NOT NULL,
                    hasCodeChange INTEGER NOT NULL,
                    assistantMessagesJson TEXT NOT NULL,
                    FOREIGN KEY (sessionId) REFERENCES conversation_sessions(sessionId) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_segments_sessionId ON conversation_segments(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_segments_timestamp ON conversation_segments(timestamp)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VibeDatabase {
        return Room.databaseBuilder(
            context,
            VibeDatabase::class.java,
            "vibe_terminal.db"
        )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideMachineDao(database: VibeDatabase): MachineDao {
        return database.machineDao()
    }

    @Provides
    fun provideProjectDao(database: VibeDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    fun provideConversationDao(database: VibeDatabase): ConversationDao {
        return database.conversationDao()
    }
}
