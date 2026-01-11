package com.vibe.terminal.data.repository

import com.vibe.terminal.data.local.dao.MachineDao
import com.vibe.terminal.data.local.entity.MachineEntity
import com.vibe.terminal.data.security.CredentialEncryptionManager
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.repository.MachineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MachineRepositoryImpl @Inject constructor(
    private val machineDao: MachineDao,
    private val credentialEncryption: CredentialEncryptionManager
) : MachineRepository {

    override fun getAllMachines(): Flow<List<Machine>> {
        return machineDao.getAllMachines().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMachineById(id: String): Machine? {
        return machineDao.getMachineById(id)?.toDomain()
    }

    override fun getMachineByIdFlow(id: String): Flow<Machine?> {
        return machineDao.getMachineByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun saveMachine(machine: Machine) {
        machineDao.insertMachine(machine.toEntity())
    }

    override suspend fun updateMachine(machine: Machine) {
        machineDao.updateMachine(machine.toEntity().copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteMachine(id: String) {
        machineDao.deleteMachineById(id)
    }

    /**
     * 将数据库实体转换为领域模型
     * 解密敏感凭证字段
     */
    private fun MachineEntity.toDomain(): Machine = Machine(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authType = when (authType) {
            com.vibe.terminal.data.local.entity.AuthType.PASSWORD -> Machine.AuthType.PASSWORD
            com.vibe.terminal.data.local.entity.AuthType.SSH_KEY -> Machine.AuthType.SSH_KEY
        },
        // 解密凭证
        password = credentialEncryption.decrypt(password),
        privateKey = credentialEncryption.decrypt(privateKey),
        passphrase = credentialEncryption.decrypt(passphrase)
    )

    /**
     * 将领域模型转换为数据库实体
     * 加密敏感凭证字段
     */
    private fun Machine.toEntity(): MachineEntity = MachineEntity(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authType = when (authType) {
            Machine.AuthType.PASSWORD -> com.vibe.terminal.data.local.entity.AuthType.PASSWORD
            Machine.AuthType.SSH_KEY -> com.vibe.terminal.data.local.entity.AuthType.SSH_KEY
        },
        // 加密凭证
        password = credentialEncryption.encrypt(password),
        privateKey = credentialEncryption.encrypt(privateKey),
        passphrase = credentialEncryption.encrypt(passphrase)
    )
}
