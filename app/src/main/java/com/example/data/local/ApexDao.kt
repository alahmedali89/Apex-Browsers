package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ApexDao {

    @Query("SELECT * FROM privacy_stats WHERE id = 1")
    fun observePrivacyStats(): Flow<PrivacyStatSummaryEntity?>

    @Query("SELECT * FROM privacy_stats WHERE id = 1")
    suspend fun getPrivacyStatsOnce(): PrivacyStatSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivacyStats(stats: PrivacyStatSummaryEntity)

    @Query("SELECT * FROM blocked_domain_logs ORDER BY timestamp DESC LIMIT 40")
    fun observeRecentBlockedLogs(): Flow<List<BlockedDomainLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedLog(log: BlockedDomainLogEntity)

    @Query("DELETE FROM blocked_domain_logs")
    suspend fun clearBlockedLogs()

    @Query("SELECT * FROM extraction_rules ORDER BY createdAt DESC")
    fun observeExtractionRules(): Flow<List<ExtractionRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtractionRule(rule: ExtractionRuleEntity): Long

    @Query("UPDATE extraction_rules SET lastRunCount = :count WHERE id = :id")
    suspend fun updateRuleRunCount(id: Long, count: Int)

    @Query("DELETE FROM extraction_rules WHERE id = :id")
    suspend fun deleteExtractionRule(id: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getBookmarksOnce(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    // Search History (starts empty on fresh install)
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 30")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchHistoryById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Top Visits (Recent top visits shortcuts)
    @Query("SELECT * FROM top_visits ORDER BY id ASC")
    fun observeTopVisits(): Flow<List<TopVisitEntity>>

    @Query("SELECT * FROM top_visits ORDER BY id ASC")
    suspend fun getTopVisitsOnce(): List<TopVisitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopVisit(topVisit: TopVisitEntity): Long

    @Query("DELETE FROM top_visits WHERE id = :id")
    suspend fun deleteTopVisitById(id: Long)

    // Browsing History (Full chronological web page visit log)
    @Query("SELECT * FROM browsing_history ORDER BY visitedAt DESC LIMIT 500")
    fun observeBrowsingHistory(): Flow<List<BrowsingHistoryEntity>>

    @Query("SELECT COUNT(*) FROM browsing_history")
    fun observeBrowsingHistoryCount(): Flow<Int>

    @Query("SELECT * FROM browsing_history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    suspend fun getLatestHistoryByUrl(url: String): BrowsingHistoryEntity?

    @Query("SELECT * FROM browsing_history ORDER BY visitedAt DESC LIMIT 1")
    suspend fun getMostRecentHistoryEntry(): BrowsingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrowsingHistory(entry: BrowsingHistoryEntity): Long

    @Query("DELETE FROM browsing_history WHERE id = :id")
    suspend fun deleteBrowsingHistoryById(id: Long)

    @Query("DELETE FROM browsing_history WHERE visitedAt >= :sinceMillis")
    suspend fun deleteBrowsingHistorySince(sinceMillis: Long)

    @Query("DELETE FROM browsing_history")
    suspend fun clearAllBrowsingHistory()

    // Fast Downloads (Dedicated Download Manager)
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT 300")
    fun observeDownloads(): Flow<List<DownloadItemEntity>>

    @Query("SELECT COUNT(*) FROM downloads")
    fun observeDownloadsCount(): Flow<Int>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(item: DownloadItemEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED' OR status = 'FAILED' OR status = 'CANCELED'")
    suspend fun clearCompletedDownloads()

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()

    // Dedicated Password Manager Vault
    @Query("SELECT * FROM saved_passwords ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeSavedPasswords(): Flow<List<SavedPasswordEntity>>

    @Query("SELECT COUNT(*) FROM saved_passwords")
    fun observeSavedPasswordsCount(): Flow<Int>

    @Query("SELECT * FROM saved_passwords ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getSavedPasswordsOnce(): List<SavedPasswordEntity>

    @Query("SELECT * FROM saved_passwords WHERE id = :id LIMIT 1")
    suspend fun getSavedPasswordById(id: Long): SavedPasswordEntity?

    @Query("SELECT * FROM saved_passwords WHERE host = :host ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getSavedPasswordsByHost(host: String): List<SavedPasswordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedPassword(item: SavedPasswordEntity): Long

    @Query("DELETE FROM saved_passwords WHERE id = :id")
    suspend fun deleteSavedPasswordById(id: Long)

    @Query("DELETE FROM saved_passwords")
    suspend fun clearAllSavedPasswords()

    // Unified Automation Workflows & Executions
    @Query("SELECT * FROM automation_workflows ORDER BY updatedAt DESC")
    fun observeAutomationWorkflows(): Flow<List<AutomationWorkflowEntity>>

    @Query("SELECT * FROM automation_workflows ORDER BY updatedAt DESC")
    suspend fun getAutomationWorkflowsOnce(): List<AutomationWorkflowEntity>

    @Query("SELECT * FROM automation_workflows WHERE id = :id LIMIT 1")
    suspend fun getAutomationWorkflowById(id: Long): AutomationWorkflowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationWorkflow(workflow: AutomationWorkflowEntity): Long

    @Query("DELETE FROM automation_workflows WHERE id = :id")
    suspend fun deleteAutomationWorkflowById(id: Long)

    @Query("SELECT * FROM automation_executions ORDER BY startedAt DESC LIMIT 100")
    fun observeAutomationExecutions(): Flow<List<AutomationExecutionEntity>>

    @Query("SELECT * FROM automation_executions WHERE workflowId = :workflowId ORDER BY startedAt DESC LIMIT 100")
    fun observeAutomationExecutionsForWorkflow(workflowId: Long): Flow<List<AutomationExecutionEntity>>

    @Query("SELECT * FROM automation_executions WHERE workflowId = :workflowId ORDER BY startedAt DESC")
    suspend fun getAutomationExecutionsForWorkflowOnce(workflowId: Long): List<AutomationExecutionEntity>

    @Query("SELECT * FROM automation_executions WHERE id = :executionId LIMIT 1")
    suspend fun getAutomationExecutionById(executionId: Long): AutomationExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationExecution(execution: AutomationExecutionEntity): Long

    @Query(
        """
        UPDATE automation_executions
        SET status = :status,
            completedSteps = :completedSteps,
            totalSteps = :totalSteps,
            verificationSummary = :verificationSummary,
            finishedAt = :finishedAt
        WHERE id = :executionId
        """
    )
    suspend fun updateAutomationExecutionCompletion(
        executionId: Long,
        status: String,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        finishedAt: Long
    ): Int

    @Query(
        """
        UPDATE automation_workflows
        SET lastExecutedAt = :lastExecutedAt,
            lastExecutionStatus = :lastExecutionStatus,
            lastExecutionSummary = :lastExecutionSummary
        WHERE id = :workflowId
        """
    )
    suspend fun updateWorkflowExecutionSummary(
        workflowId: Long,
        lastExecutedAt: Long,
        lastExecutionStatus: String,
        lastExecutionSummary: String
    )

    @Query("DELETE FROM automation_executions WHERE workflowId = :workflowId")
    suspend fun deleteExecutionsForWorkflow(workflowId: Long)

    @Transaction
    suspend fun deleteAutomationWorkflowWithExecutions(workflowId: Long) {
        deleteExecutionsForWorkflow(workflowId)
        deleteAutomationWorkflowById(workflowId)
    }

    @Transaction
    suspend fun startAutomationExecutionRecord(
        workflowId: Long,
        workflowName: String,
        domain: String,
        totalSteps: Int,
        startedAt: Long
    ): Long {
        updateWorkflowExecutionSummary(
            workflowId = workflowId,
            lastExecutedAt = startedAt,
            lastExecutionStatus = "RUNNING",
            lastExecutionSummary = "Running $totalSteps step${if (totalSteps == 1) "" else "s"}…"
        )
        return upsertAutomationExecution(
            AutomationExecutionEntity(
                workflowId = workflowId,
                workflowName = workflowName,
                domain = domain,
                status = "RUNNING",
                totalSteps = totalSteps,
                completedSteps = 0,
                verificationSummary = "Execution started",
                startedAt = startedAt
            )
        )
    }

    @Transaction
    suspend fun finishAutomationExecutionRecord(
        workflowId: Long,
        executionId: Long,
        status: String,
        completedSteps: Int,
        totalSteps: Int,
        verificationSummary: String,
        finishedAt: Long
    ) {
        updateWorkflowExecutionSummary(
            workflowId = workflowId,
            lastExecutedAt = finishedAt,
            lastExecutionStatus = status,
            lastExecutionSummary = verificationSummary
        )
        updateAutomationExecutionCompletion(
            executionId = executionId,
            status = status,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            verificationSummary = verificationSummary,
            finishedAt = finishedAt
        )
    }
}




