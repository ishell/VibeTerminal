package com.vibe.terminal.data.repository

import com.vibe.terminal.data.local.dao.ProjectDao
import com.vibe.terminal.data.local.entity.ProjectEntity
import com.vibe.terminal.domain.model.AssistantType
import com.vibe.terminal.domain.model.Project
import com.vibe.terminal.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao
) : ProjectRepository {

    override fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getProjectsByMachine(machineId: String): Flow<List<Project>> {
        return projectDao.getProjectsByMachine(machineId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getProjectById(id: String): Project? {
        return projectDao.getProjectById(id)?.toDomain()
    }

    override fun getProjectByIdFlow(id: String): Flow<Project?> {
        return projectDao.getProjectByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun saveProject(project: Project) {
        projectDao.insertProject(project.toEntity())
    }

    override suspend fun updateProject(project: Project) {
        projectDao.updateProject(project.toEntity().copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun updateLastConnected(id: String) {
        projectDao.updateLastConnected(id)
    }

    override suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }

    private fun ProjectEntity.toDomain(): Project = Project(
        id = id,
        name = name,
        machineId = machineId,
        zellijSession = zellijSession,
        workingDirectory = workingDirectory,
        assistantType = try {
            AssistantType.valueOf(assistantType)
        } catch (e: Exception) {
            AssistantType.CLAUDE_CODE
        },
        lastConnected = lastConnected
    )

    private fun Project.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        machineId = machineId,
        zellijSession = zellijSession,
        workingDirectory = workingDirectory,
        assistantType = assistantType.name,
        lastConnected = lastConnected
    )
}
