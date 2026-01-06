package com.vibe.terminal.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vibe.terminal.data.local.entity.MachineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MachineDao {

    @Query("SELECT * FROM machines ORDER BY updatedAt DESC")
    fun getAllMachines(): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE id = :id")
    suspend fun getMachineById(id: String): MachineEntity?

    @Query("SELECT * FROM machines WHERE id = :id")
    fun getMachineByIdFlow(id: String): Flow<MachineEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachine(machine: MachineEntity)

    @Update
    suspend fun updateMachine(machine: MachineEntity)

    @Delete
    suspend fun deleteMachine(machine: MachineEntity)

    @Query("DELETE FROM machines WHERE id = :id")
    suspend fun deleteMachineById(id: String)
}
