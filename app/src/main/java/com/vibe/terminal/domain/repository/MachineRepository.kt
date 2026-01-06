package com.vibe.terminal.domain.repository

import com.vibe.terminal.domain.model.Machine
import kotlinx.coroutines.flow.Flow

interface MachineRepository {
    fun getAllMachines(): Flow<List<Machine>>
    suspend fun getMachineById(id: String): Machine?
    fun getMachineByIdFlow(id: String): Flow<Machine?>
    suspend fun saveMachine(machine: Machine)
    suspend fun updateMachine(machine: Machine)
    suspend fun deleteMachine(id: String)
}
