package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PrivacyStatSummaryEntity::class,
        BlockedDomainLogEntity::class,
        ExtractionRuleEntity::class,
        BookmarkEntity::class,
        SearchHistoryEntity::class,
        TopVisitEntity::class,
        BrowsingHistoryEntity::class,
        DownloadItemEntity::class,
        SavedPasswordEntity::class,
        AutomationWorkflowEntity::class,
        AutomationExecutionEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class ApexDatabase : RoomDatabase() {

    abstract fun apexDao(): ApexDao

    companion object {
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_executions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workflowId` INTEGER NOT NULL,
                        `workflowName` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `totalSteps` INTEGER NOT NULL,
                        `completedSteps` INTEGER NOT NULL,
                        `verificationSummary` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        FOREIGN KEY(`workflowId`) REFERENCES `automation_workflows`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `automation_executions_new` (
                        `id`, `workflowId`, `workflowName`, `domain`, `status`,
                        `totalSteps`, `completedSteps`, `verificationSummary`, `startedAt`, `finishedAt`
                    )
                    SELECT
                        e.`id`, e.`workflowId`, e.`workflowName`, e.`domain`, e.`status`,
                        e.`totalSteps`, e.`completedSteps`, e.`verificationSummary`, e.`startedAt`, e.`finishedAt`
                    FROM `automation_executions` AS e
                    WHERE EXISTS (
                        SELECT 1 FROM `automation_workflows` AS w WHERE w.`id` = e.`workflowId`
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `automation_executions`")
                db.execSQL("ALTER TABLE `automation_executions_new` RENAME TO `automation_executions`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_executions_workflowId` ON `automation_executions` (`workflowId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_automation_executions_startedAt` ON `automation_executions` (`startedAt`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: ApexDatabase? = null

        fun getInstance(context: Context): ApexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApexDatabase::class.java,
                    "apex_browser_fresh.db"
                )
                    .addMigrations(MIGRATION_12_13)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
