package com.podcast.app.di

import com.podcast.app.mcp.bridge.MCPCommandHandler
import com.podcast.app.mcp.bridge.MCPCommandHandlerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MCPModule {

    @Binds
    @Singleton
    abstract fun bindMCPCommandHandler(
        impl: MCPCommandHandlerImpl
    ): MCPCommandHandler
}
