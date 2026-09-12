package com.riqing.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.riqing.core.database.dao.CourseDao
import com.riqing.core.database.dao.EventDao
import com.riqing.core.database.dao.EventExceptionDao
import com.riqing.core.database.dao.PeriodSlotDao
import com.riqing.core.database.dao.SemesterDao
import com.riqing.core.database.dao.TodoDao
import com.riqing.core.database.dao.UndoSnapshotDao
import com.riqing.core.database.entity.CourseEntity
import com.riqing.core.database.entity.EventEntity
import com.riqing.core.database.entity.EventExceptionEntity
import com.riqing.core.database.entity.PeriodSlotEntity
import com.riqing.core.database.entity.SemesterEntity
import com.riqing.core.database.entity.TodoEntity
import com.riqing.core.database.entity.UndoSnapshotEntity

@Database(
    entities = [
        SemesterEntity::class,
        PeriodSlotEntity::class,
        CourseEntity::class,
        EventEntity::class,
        EventExceptionEntity::class,
        TodoEntity::class,
        UndoSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class RiQingDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun periodSlotDao(): PeriodSlotDao
    abstract fun courseDao(): CourseDao
    abstract fun eventDao(): EventDao
    abstract fun eventExceptionDao(): EventExceptionDao
    abstract fun todoDao(): TodoDao
    abstract fun undoSnapshotDao(): UndoSnapshotDao

    companion object {
        /** v2：日程支持「每周按星期几重复」，新增 repeat_weekdays_json 列。 */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN repeat_weekdays_json TEXT")
            }
        }

        /** v3：课程支持「一周多节」（多星期），新增 weekdays_json 列，存量行按原 weekday 初始化。 */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN weekdays_json TEXT")
                db.execSQL("UPDATE courses SET weekdays_json = '[' || weekday || ']'")
            }
        }
    }
}
