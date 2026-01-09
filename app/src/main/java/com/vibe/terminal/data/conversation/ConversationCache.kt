package com.vibe.terminal.data.conversation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话历史本地缓存管理
 * 缓存JSONL文件到本地，支持增量追加以优化网络传输
 */
@Singleton
class ConversationCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "conversations").also { it.mkdirs() }
    }

    private val mutex = Mutex()

    // 缓存元数据 (文件路径 -> CacheMetadata)
    private val metadataMap = mutableMapOf<String, CacheMetadata>()

    /**
     * 缓存元数据
     */
    data class CacheMetadata(
        val remoteModTime: Long,
        val fileSize: Long,
        val lastOffset: Long
    )

    /**
     * 获取缓存文件路径
     */
    private fun getCacheFile(sessionId: String, projectPath: String): File {
        val encodedPath = projectPath.replace("/", "_")
        return File(cacheDir, "${encodedPath}_${sessionId}.jsonl")
    }

    /**
     * 获取元数据文件
     */
    private fun getMetadataFile(): File = File(cacheDir, "metadata_v2.txt")

    init {
        // 加载元数据
        loadMetadata()
    }

    private fun loadMetadata() {
        try {
            val metaFile = getMetadataFile()
            if (metaFile.exists()) {
                metaFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        metadataMap[parts[0]] = CacheMetadata(
                            remoteModTime = parts[1].toLongOrNull() ?: 0L,
                            fileSize = parts[2].toLongOrNull() ?: 0L,
                            lastOffset = parts[3].toLongOrNull() ?: 0L
                        )
                    } else if (parts.size == 2) {
                        // 兼容旧格式
                        metadataMap[parts[0]] = CacheMetadata(
                            remoteModTime = parts[1].toLongOrNull() ?: 0L,
                            fileSize = 0L,
                            lastOffset = 0L
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    private suspend fun saveMetadata() = withContext(Dispatchers.IO) {
        try {
            val metaFile = getMetadataFile()
            metaFile.writeText(
                metadataMap.entries.joinToString("\n") { (key, meta) ->
                    "${key}|${meta.remoteModTime}|${meta.fileSize}|${meta.lastOffset}"
                }
            )
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * 检查缓存是否有效（远程文件未更新）
     * @return true 如果缓存有效，false 如果需要重新下载
     */
    suspend fun isCacheValid(
        sessionId: String,
        projectPath: String,
        remoteModTime: Long
    ): Boolean = mutex.withLock {
        val cacheFile = getCacheFile(sessionId, projectPath)
        if (!cacheFile.exists()) return false

        val key = "${projectPath}/${sessionId}"
        val cachedMeta = metadataMap[key] ?: return false
        return cachedMeta.remoteModTime >= remoteModTime
    }

    /**
     * 检查是否需要增量更新
     * @return 如果需要更新，返回应该从哪个偏移量开始读取；如果不需要返回 null
     */
    suspend fun getIncrementalOffset(
        sessionId: String,
        projectPath: String,
        remoteFileSize: Long
    ): Long? = mutex.withLock {
        val cacheFile = getCacheFile(sessionId, projectPath)
        if (!cacheFile.exists()) return null

        val key = "${projectPath}/${sessionId}"
        val cachedMeta = metadataMap[key] ?: return null

        // 如果远程文件变大了，返回当前偏移量用于增量读取
        return if (remoteFileSize > cachedMeta.fileSize) {
            cachedMeta.lastOffset
        } else {
            null
        }
    }

    /**
     * 获取缓存的文件内容
     */
    suspend fun getCachedContent(
        sessionId: String,
        projectPath: String
    ): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cacheFile = getCacheFile(sessionId, projectPath)
            if (cacheFile.exists()) {
                cacheFile.readText()
            } else {
                null
            }
        }
    }

    /**
     * 保存内容到缓存（完整覆盖）
     */
    suspend fun saveToCache(
        sessionId: String,
        projectPath: String,
        content: String,
        remoteModTime: Long
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cacheFile = getCacheFile(sessionId, projectPath)
            cacheFile.writeText(content)

            val key = "${projectPath}/${sessionId}"
            val contentBytes = content.toByteArray(Charsets.UTF_8)
            metadataMap[key] = CacheMetadata(
                remoteModTime = remoteModTime,
                fileSize = contentBytes.size.toLong(),
                lastOffset = contentBytes.size.toLong()
            )
            saveMetadata()
        }
    }

    /**
     * 追加增量内容到缓存
     */
    suspend fun appendToCache(
        sessionId: String,
        projectPath: String,
        incrementalContent: String,
        newFileSize: Long,
        remoteModTime: Long
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cacheFile = getCacheFile(sessionId, projectPath)

            // 追加内容
            FileOutputStream(cacheFile, true).use { fos ->
                fos.write(incrementalContent.toByteArray(Charsets.UTF_8))
            }

            val key = "${projectPath}/${sessionId}"
            metadataMap[key] = CacheMetadata(
                remoteModTime = remoteModTime,
                fileSize = newFileSize,
                lastOffset = newFileSize
            )
            saveMetadata()
        }
    }

    /**
     * 清除指定项目的缓存
     */
    suspend fun clearProjectCache(projectPath: String) = mutex.withLock {
        val encodedPath = projectPath.replace("/", "_")
        cacheDir.listFiles()?.filter {
            it.name.startsWith(encodedPath)
        }?.forEach {
            it.delete()
        }

        // 清除元数据
        metadataMap.keys.filter { it.startsWith(projectPath) }.forEach {
            metadataMap.remove(it)
        }
        saveMetadata()
    }

    /**
     * 清除所有缓存
     */
    suspend fun clearAllCache() = mutex.withLock {
        cacheDir.listFiles()?.forEach { it.delete() }
        metadataMap.clear()
        saveMetadata()
    }

    /**
     * 获取缓存大小（字节）
     */
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
