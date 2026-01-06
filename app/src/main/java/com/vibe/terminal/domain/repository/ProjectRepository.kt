package com.vibe.terminal.domain.repository

import com.vibe.terminal.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    fun getProjectsByMachine(machineId: String): Flow<List<Project>>
    suspend fun getProjectById(id: String): Project?
    fun getProjectByIdFlow(id: String): Flow<Project?>
    suspend fun saveProject(project: Project)
    suspend fun updateProject(project: Project)
    suspend fun updateLastConnected(id: String)
    suspend fun deleteProject(id: String)
}
