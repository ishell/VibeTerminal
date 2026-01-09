package com.vibe.terminal.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibe.terminal.data.local.dao.ConversationDao
import com.vibe.terminal.data.local.dao.MachineDao
import com.vibe.terminal.data.local.dao.ProjectDao
import com.vibe.terminal.data.local.entity.ConversationFileEntity
import com.vibe.terminal.data.local.entity.ConversationSegmentEntity
import com.vibe.terminal.data.local.entity.ConversationSessionEntity
import com.vibe.terminal.data.local.entity.MachineEntity
import com.vibe.terminal.data.local.entity.ProjectEntity

@Database(
    entities = [
        MachineEntity::class,
        ProjectEntity::class,
        ConversationFileEntity::class,
        ConversationSessionEntity::class,
        ConversationSegmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun machineDao(): MachineDao
    abstract fun projectDao(): ProjectDao
    abstract fun conversationDao(): ConversationDao
}
