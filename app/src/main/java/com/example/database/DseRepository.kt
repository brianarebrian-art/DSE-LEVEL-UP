package com.example.database

import kotlinx.coroutines.flow.Flow

class DseRepository(private val dseDao: DseDao) {

  val allQuestions: Flow<List<QuestionEntity>> = dseDao.getAllQuestionsFlow()

  fun getQuestionsBySubject(subject: String): Flow<List<QuestionEntity>> {
    return dseDao.getQuestionsBySubjectFlow(subject)
  }

  suspend fun getQuestionById(id: String): QuestionEntity? {
    return dseDao.getQuestionById(id)
  }

  suspend fun insertQuestions(questions: List<QuestionEntity>) {
    dseDao.insertQuestions(questions)
  }

  suspend fun insertQuestion(question: QuestionEntity) {
    dseDao.insertQuestion(question)
  }

  val userProgress: Flow<UserProgressEntity?> = dseDao.getUserProgressFlow()

  suspend fun getUserProgressDirect(): UserProgressEntity? {
    return dseDao.getUserProgressDirect()
  }

  suspend fun insertOrUpdateUserProgress(progress: UserProgressEntity) {
    dseDao.insertOrUpdateUserProgress(progress)
  }

  val allMistakes: Flow<List<MistakeEntity>> = dseDao.getAllMistakesFlow()

  suspend fun getMistakeById(questionId: String): MistakeEntity? {
    return dseDao.getMistakeById(questionId)
  }

  suspend fun insertMistake(mistake: MistakeEntity) {
    dseDao.insertMistake(mistake)
  }

  suspend fun deleteMistake(questionId: String) {
    dseDao.deleteMistake(questionId)
  }

  val completedQuestions: Flow<List<CompletedQuestionEntity>> = dseDao.getAllCompletedQuestionsFlow()

  suspend fun getCompletedCorrectQuestionIds(subject: String): List<String> {
    return dseDao.getCompletedCorrectQuestionIds(subject)
  }

  suspend fun insertCompletedQuestion(completed: CompletedQuestionEntity) {
    dseDao.insertCompletedQuestion(completed)
  }

  suspend fun clearCompletedQuestions() {
    dseDao.clearCompletedQuestions()
  }

  val allBadges: Flow<List<BadgeEntity>> = dseDao.getAllBadgesFlow()

  suspend fun insertBadge(badge: BadgeEntity) {
    dseDao.insertBadge(badge)
  }

  suspend fun clearBadges() {
    dseDao.clearBadges()
  }

  // Past Paper Resources
  val allPastPaperResources: Flow<List<PastPaperResourceEntity>> = dseDao.getAllPastPaperResourcesFlow()

  suspend fun insertPastPaperResources(resources: List<PastPaperResourceEntity>) {
    dseDao.insertPastPaperResources(resources)
  }

  suspend fun updatePastPaperDownloadStatus(id: String, isDownloaded: Boolean, localFilePath: String?, downloadCount: Int) {
    dseDao.updatePastPaperDownloadStatus(id, isDownloaded, localFilePath, downloadCount)
  }

  // Study plans and tasks
  val allStudyPlans: Flow<List<StudyPlanEntity>> = dseDao.getAllStudyPlansFlow()
  val allStudyTasks: Flow<List<StudyTaskEntity>> = dseDao.getAllStudyTasksFlow()

  suspend fun insertStudyPlans(plans: List<StudyPlanEntity>) {
    dseDao.insertStudyPlans(plans)
  }

  suspend fun insertStudyPlan(plan: StudyPlanEntity) {
    dseDao.insertStudyPlan(plan)
  }

  suspend fun insertStudyTask(task: StudyTaskEntity) {
    dseDao.insertStudyTask(task)
  }

  suspend fun deleteStudyTask(id: Int) {
    dseDao.deleteStudyTask(id)
  }

  suspend fun updateTaskStatus(id: Int, isCompleted: Boolean) {
    dseDao.updateTaskStatus(id, isCompleted)
  }
}
