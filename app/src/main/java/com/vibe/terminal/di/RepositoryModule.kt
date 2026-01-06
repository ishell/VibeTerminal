package com.vibe.terminal.di

import com.vibe.terminal.data.repository.MachineRepositoryImpl
import com.vibe.terminal.data.repository.ProjectRepositoryImpl
import com.vibe.terminal.domain.repository.MachineRepository
import com.vibe.terminal.domain.repository.ProjectRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMachineRepository(
        impl: MachineRepositoryImpl
    ): MachineRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(
        impl: ProjectRepositoryImpl
    ): ProjectRepository
}
