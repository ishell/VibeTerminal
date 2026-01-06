package com.vibe.terminal.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibe.terminal.data.local.dao.MachineDao
import com.vibe.terminal.data.local.dao.ProjectDao
import com.vibe.terminal.data.local.entity.MachineEntity
import com.vibe.terminal.data.local.entity.ProjectEntity

@Database(
    entities = [
        MachineEntity::class,
        ProjectEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun machineDao(): MachineDao
    abstract fun projectDao(): ProjectDao
}
