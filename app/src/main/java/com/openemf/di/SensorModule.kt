package com.openemf.di

import com.openemf.sensors.api.EMFSensorModule
import com.openemf.sensors.impl.EMFSensorModuleImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing sensor dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds
    @Singleton
    abstract fun bindEMFSensorModule(
        impl: EMFSensorModuleImpl
    ): EMFSensorModule
}
