package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DseDao {

  // Questions
  @Query("SELECT * FROM questions")
  fun getAllQuestionsFlow(): Flow<List<QuestionEntity>>

  @Query("SELECT * FROM questions WHERE subject = :subject")
  fun getQuestionsBySubjectFlow(subject: String): Flow<List<QuestionEntity>>

  @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
  suspend fun getQuestionById(id: String): QuestionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQuestions(questions: List<QuestionEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQuestion(question: QuestionEntity)

  // Progress
  @Query("SELECT * FROM user_progress WHERE id = 'main_user' LIMIT 1")
  fun getUserProgressFlow(): Flow<UserProgressEntity?>

  @Query("SELECT * FROM user_progress WHERE id = 'main_user' LIMIT 1")
  suspend fun getUserProgressDirect(): UserProgressEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateUserProgress(progress: UserProgressEntity)

  // Mistakes
  @Query("SELECT * FROM mistakes ORDER BY timestamp DESC")
  fun getAllMistakesFlow(): Flow<List<MistakeEntity>>

  @Query("SELECT * FROM mistakes WHERE questionId = :questionId LIMIT 1")
  suspend fun getMistakeById(questionId: String): MistakeEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMistake(mistake: MistakeEntity)

  @Query("DELETE FROM mistakes WHERE questionId = :questionId")
  suspend fun deleteMistake(questionId: String)

  // Completed
  @Query("SELECT * FROM completed_questions")
  fun getAllCompletedQuestionsFlow(): Flow<List<CompletedQuestionEntity>>

  @Query("SELECT id FROM completed_questions WHERE subject = :subject AND isCorrect = 1")
  suspend fun getCompletedCorrectQuestionIds(subject: String): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCompletedQuestion(completed: CompletedQuestionEntity)

  @Query("DELETE FROM completed_questions")
  suspend fun clearCompletedQuestions()

  // Badges
  @Query("SELECT * FROM unlocked_badges ORDER BY unlockTimestamp DESC")
  fun getAllBadgesFlow(): Flow<List<BadgeEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBadge(badge: BadgeEntity)

  @Query("DELETE FROM unlocked_badges")
  suspend fun clearBadges()

  // Past Paper Resources
  @Query("SELECT * FROM past_paper_resources ORDER BY year DESC, titleChinese ASC")
  fun getAllPastPaperResourcesFlow(): Flow<List<PastPaperResourceEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPastPaperResources(resources: List<PastPaperResourceEntity>)

  @Query("UPDATE past_paper_resources SET isDownloaded = :isDownloaded, localFilePath = :localFilePath, downloadCount = :downloadCount WHERE id = :id")
  suspend fun updatePastPaperDownloadStatus(id: String, isDownloaded: Boolean, localFilePath: String?, downloadCount: Int)

  // Study Plan
  @Query("SELECT * FROM study_plan")
  fun getAllStudyPlansFlow(): Flow<List<StudyPlanEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStudyPlans(plans: List<StudyPlanEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStudyPlan(plan: StudyPlanEntity)

  // Study Tasks
  @Query("SELECT * FROM study_tasks ORDER BY timestamp DESC")
  fun getAllStudyTasksFlow(): Flow<List<StudyTaskEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStudyTask(task: StudyTaskEntity)

  @Query("DELETE FROM study_tasks WHERE id = :id")
  suspend fun deleteStudyTask(id: Int)

  @Query("UPDATE study_tasks SET isCompleted = :isCompleted WHERE id = :id")
  suspend fun updateTaskStatus(id: Int, isCompleted: Boolean)
}
