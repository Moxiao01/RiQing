package com.riqing.core.data.di

import android.content.Context
import androidx.room.Room
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.database.dao.CourseDao
import com.riqing.core.database.dao.EventDao
import com.riqing.core.database.dao.EventExceptionDao
import com.riqing.core.database.dao.PeriodSlotDao
import com.riqing.core.database.dao.SemesterDao
import com.riqing.core.database.dao.TodoDao
import com.riqing.core.database.dao.UndoSnapshotDao
import com.riqing.core.datastore.AgentChatStore
import com.riqing.core.datastore.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RiQingDatabase =
        Room.databaseBuilder(context, RiQingDatabase::class.java, "riqing.db")
            .addMigrations(RiQingDatabase.MIGRATION_1_2, RiQingDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideAgentChatStore(@ApplicationContext context: Context): AgentChatStore =
        AgentChatStore(context)

    @Provides
    fun provideSemesterDao(db: RiQingDatabase): SemesterDao = db.semesterDao()

    @Provides
    fun providePeriodSlotDao(db: RiQingDatabase): PeriodSlotDao = db.periodSlotDao()

    @Provides
    fun provideCourseDao(db: RiQingDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideEventDao(db: RiQingDatabase): EventDao = db.eventDao()

    @Provides
    fun provideEventExceptionDao(db: RiQingDatabase): EventExceptionDao = db.eventExceptionDao()

    @Provides
    fun provideTodoDao(db: RiQingDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideUndoSnapshotDao(db: RiQingDatabase): UndoSnapshotDao = db.undoSnapshotDao()
}
