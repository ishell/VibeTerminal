package com.vibe.terminal.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vibe.terminal.data.local.dao.ConversationDao
import com.vibe.terminal.data.local.dao.MachineDao
import com.vibe.terminal.data.local.dao.ProjectDao
import com.vibe.terminal.data.local.dao.TaskCompletionDao
import com.vibe.terminal.data.local.entity.ConversationFileEntity
import com.vibe.terminal.data.local.entity.ConversationSegmentEntity
import com.vibe.terminal.data.local.entity.ConversationSessionEntity
import com.vibe.terminal.data.local.entity.MachineEntity
import com.vibe.terminal.data.local.entity.ProjectEntity
import com.vibe.terminal.data.local.entity.TaskCompletionEntity

@Database(
    entities = [
        MachineEntity::class,
        ProjectEntity::class,
        ConversationFileEntity::class,
        ConversationSessionEntity::class,
        ConversationSegmentEntity::class,
        TaskCompletionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun machineDao(): MachineDao
    abstract fun projectDao(): ProjectDao
    abstract fun conversationDao(): ConversationDao
    abstract fun taskCompletionDao(): TaskCompletionDao

    companion object {
        // Migration from version 3 to 4: Add assistantType column to projects table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN assistantType TEXT NOT NULL DEFAULT 'CLAUDE_CODE'")
            }
        }

        // Migration from version 4 to 5: Add task_completions table
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_completions (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        taskHash TEXT NOT NULL,
                        taskContent TEXT NOT NULL,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_completions_projectId ON task_completions(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_completions_taskHash ON task_completions(taskHash)")
            }
        }

        // Migration from version 5 to 6: Add isDeleted column to task_completions table
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_completions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
