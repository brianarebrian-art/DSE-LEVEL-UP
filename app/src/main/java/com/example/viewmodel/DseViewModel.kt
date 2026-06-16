package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiRequest(val contents: List<GeminiContent>)

@JsonClass(generateAdapter = true)
data class GeminiPartResponse(val text: String?)

@JsonClass(generateAdapter = true)
data class GeminiContentResponse(val parts: List<GeminiPartResponse>?)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContentResponse?)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

class DseViewModel(application: Application) : AndroidViewModel(application) {

  private val database = DseDatabase.getDatabase(application)
  private val repository = DseRepository(database.dseDao())

  val userProgress = repository.userProgress.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = UserProgressEntity()
  )

  val allMistakes = repository.allMistakes.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val allBadges = repository.allBadges.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val completedQuestions = repository.completedQuestions.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val allQuestions = repository.allQuestions.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val allPastPaperResources = repository.allPastPaperResources.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  // Mapping of resource ID to download progress (0.0f to 1.0f)
  private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

  // Current session scoped completed question IDs dynamically updated to avoid repetition
  private val _sessionCompletedQuestionIds = MutableStateFlow<Set<String>>(emptySet())
  val sessionCompletedQuestionIds: StateFlow<Set<String>> = _sessionCompletedQuestionIds.asStateFlow()

  // State flow for actively selected subject context
  private val _selectedSubject = MutableStateFlow("math")
  val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

  // State flow of active questions matching chosen subject excluding already completed
  val activeQuestions = combine(allQuestions, _selectedSubject, _sessionCompletedQuestionIds) { questions, subject, sessionCompleted ->
    questions.filter { it.subject == subject && !sessionCompleted.contains(it.id) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _currentQuestionIndex = MutableStateFlow(0)
  val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

  // Active question in context
  val currentQuestion = combine(activeQuestions, _currentQuestionIndex) { list, idx ->
    if (list.isNotEmpty() && idx < list.size) list[idx] else null
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  // AI Tutor response loading state
  private val _aiTutorExplanation = MutableStateFlow<String?>(null)
  val aiTutorExplanation: StateFlow<String?> = _aiTutorExplanation.asStateFlow()

  private val _aiTutorLoading = MutableStateFlow(false)
  val aiTutorLoading: StateFlow<Boolean> = _aiTutorLoading.asStateFlow()

  // --- PREMIUM STUDY FOCUS & ONLINE STUDY GROUPS ---
  private val _customFocusSubjects = MutableStateFlow(listOf("數學", "物理", "化學", "生物", "英文", "中文", "BAFS / ICT"))
  val customFocusSubjects: StateFlow<List<String>> = _customFocusSubjects.asStateFlow()

  private val _selectedFocusSubject = MutableStateFlow("數學")
  val selectedFocusSubject: StateFlow<String> = _selectedFocusSubject.asStateFlow()

  private val _isFocusTimerRunning = MutableStateFlow(false)
  val isFocusTimerRunning: StateFlow<Boolean> = _isFocusTimerRunning.asStateFlow()

  private val _isFocusLockActive = MutableStateFlow(false)
  val isFocusLockActive: StateFlow<Boolean> = _isFocusLockActive.asStateFlow()

  private val _focusSecondsElapsed = MutableStateFlow(0)
  val focusSecondsElapsed: StateFlow<Int> = _focusSecondsElapsed.asStateFlow()

  // Accumulated minutes mapped by subject (e.g., "數學" to 15.5f)
  private val _focusSubjectMinutes = MutableStateFlow<Map<String, Float>>(
    mapOf("數學" to 12.5f, "物理" to 6.2f, "化學" to 0f, "生物" to 2.5f, "英文" to 8f, "中文" to 4f)
  )
  val focusSubjectMinutes: StateFlow<Map<String, Float>> = _focusSubjectMinutes.asStateFlow()

  // Online Study Group Users
  private val _onlineGroupUsers = MutableStateFlow(listOf(
    StudyGroupUser("g1", "沙田自修戰神", "🟢 專注中 | 數學", true, 280, "沙田崇真中學"),
    StudyGroupUser("g2", "協恩全能少女", "🟢 專注中 | 英文", true, 265, "協恩中學"),
    StudyGroupUser("g3", "喇沙物理之王", "🟡 休息中", true, 240, "喇沙書院"),
    StudyGroupUser("g4", "拔萃商科狀元", "🔴 離線", false, 185, "拔萃男書院"),
    StudyGroupUser("g5", "你 (今日最勤奮)", "🟡 休息中", true, 45, "自強不息候選人")
  ))
  val onlineGroupUsers: StateFlow<List<StudyGroupUser>> = _onlineGroupUsers.asStateFlow()

  private val _isUserInGroup = MutableStateFlow(true)
  val isUserInGroup: StateFlow<Boolean> = _isUserInGroup.asStateFlow()

  private val _groupRoomName = MutableStateFlow("DSE 5** 黃金衝刺組 (04)")
  val groupRoomName: StateFlow<String> = _groupRoomName.asStateFlow()

  private var focusTimerJob: kotlinx.coroutines.Job? = null

  init {
    viewModelScope.launch {
      preloadInitialQuestions()
      preloadPastPaperResources()
    }
  }

  fun downloadPastPaper(resourceId: String) {
    if (_downloadProgress.value.containsKey(resourceId)) return
    viewModelScope.launch {
      var progress = 0.0f
      while (progress < 1.0f) {
        _downloadProgress.value = _downloadProgress.value + (resourceId to progress)
        kotlinx.coroutines.delay(150)
        progress += 0.2f
      }
      _downloadProgress.value = _downloadProgress.value + (resourceId to 1.0f)
      
      // Update database status
      val paper = allPastPaperResources.value.firstOrNull { it.id == resourceId }
      if (paper != null) {
        repository.updatePastPaperDownloadStatus(
          id = resourceId,
          isDownloaded = true,
          localFilePath = "/storage/emulated/0/Download/HKDSE_${paper.subject.uppercase()}_${paper.year}_${paper.paperType.uppercase()}.pdf",
          downloadCount = paper.downloadCount + 1
        )
      }
    }
  }

  fun startFocusTimer() {
    if (_isFocusTimerRunning.value) return
    _isFocusTimerRunning.value = true
    _focusSecondsElapsed.value = 0
    
    // Update self status in group or leaderboard
    updateUserStatusInGroup(isFocused = true)

    focusTimerJob = viewModelScope.launch {
      while (true) {
        kotlinx.coroutines.delay(1000)
        _focusSecondsElapsed.value += 1
      }
    }
  }

  fun stopFocusTimer() {
    focusTimerJob?.cancel()
    focusTimerJob = null
    _isFocusTimerRunning.value = false
    _isFocusLockActive.value = false

    val elapsedSec = _focusSecondsElapsed.value
    if (elapsedSec > 0) {
      val addedMinutes = elapsedSec / 60f
      val currentSub = _selectedFocusSubject.value
      
      // Update local subject focus minutes
      val updatedMap = _focusSubjectMinutes.value.toMutableMap()
      val previousMinutes = updatedMap[currentSub] ?: 0f
      updatedMap[currentSub] = previousMinutes + addedMinutes
      _focusSubjectMinutes.value = updatedMap

      // Award Points for focus time! E.g. +10 XP for starting focus, and +1 XP per 10 seconds focused
      viewModelScope.launch {
        val earnedXP = (elapsedSec / 10).coerceAtLeast(1)
        val oldProgress = repository.getUserProgressDirect() ?: UserProgressEntity()
        val newPoints = oldProgress.scorePoints + earnedXP
        repository.insertOrUpdateUserProgress(UserProgressEntity(
          id = "main_user",
          dailyStreak = oldProgress.dailyStreak.coerceAtLeast(1),
          lastActiveTimestamp = System.currentTimeMillis(),
          scorePoints = newPoints,
          totalQuestionsJoined = oldProgress.totalQuestionsJoined,
          totalCorrectAnswers = oldProgress.totalCorrectAnswers
        ))
      }
    }
    _focusSecondsElapsed.value = 0
    // Update self status to resting in the group
    updateUserStatusInGroup(isFocused = false)
  }

  fun setFocusSubject(subject: String) {
    _selectedFocusSubject.value = subject
  }

  fun addNewFocusSubject(subjectName: String) {
    if (subjectName.isBlank()) return
    val clean = subjectName.trim()
    if (!_customFocusSubjects.value.contains(clean)) {
      _customFocusSubjects.value = _customFocusSubjects.value + clean
      val updatedMap = _focusSubjectMinutes.value.toMutableMap()
      if (!updatedMap.containsKey(clean)) {
        updatedMap[clean] = 0f
      }
      _focusSubjectMinutes.value = updatedMap
    }
  }

  fun toggleFocusLock(active: Boolean) {
    _isFocusLockActive.value = active
  }

  fun joinOrCreateGroup(roomName: String) {
    _groupRoomName.value = roomName
    _isUserInGroup.value = true
  }

  fun leaveStudyGroup() {
    _isUserInGroup.value = false
  }

  fun addFriendToGroup(name: String, school: String) {
    val newFriend = StudyGroupUser(
      id = "custom_" + System.currentTimeMillis(),
      name = name,
      status = "🟢 專注中 | 數學",
      isOnline = true,
      focusedMinutesToday = (30..180).random(),
      schoolTag = school
    )
    _onlineGroupUsers.value = _onlineGroupUsers.value + newFriend
  }

  private fun updateUserStatusInGroup(isFocused: Boolean) {
    val currentSub = _selectedFocusSubject.value
    _onlineGroupUsers.update { list ->
      list.map { user ->
        if (user.id == "g5") { // "你"
          user.copy(
            status = if (isFocused) "🟢 專注中 | $currentSub" else "🟡 休息中",
            isOnline = true,
            focusedMinutesToday = user.focusedMinutesToday + (_focusSecondsElapsed.value / 60)
          )
        } else {
          user
        }
      }
    }
  }

  fun changeSubject(subject: String) {
    _selectedSubject.value = subject
    _currentQuestionIndex.value = 0
  }

  fun nextQuestion() {
    val size = activeQuestions.value.size
    if (size > 1) {
      // More questions left. Since we filter out completed ones, the current completed index item is removed. So index stays 0 or moves safely.
      _currentQuestionIndex.value = 0
    } else {
      _currentQuestionIndex.value = 0
    }
    _aiTutorExplanation.value = null
  }

  fun recordAnswer(questionId: String, isCorrect: Boolean, reasonTag: String = "", timeSpentSeconds: Int = 0) {
    viewModelScope.launch {
      val timestamp = System.currentTimeMillis()
      val q = repository.getQuestionById(questionId) ?: return@launch
      val scoreEarned = if (isCorrect) q.marks * 10 else 2

      // 1. insert completed tracking
      repository.insertCompletedQuestion(CompletedQuestionEntity(
        id = questionId,
        subject = q.subject,
        timestamp = timestamp,
        isCorrect = isCorrect,
        timeSpentSeconds = timeSpentSeconds,
        scoreEarned = scoreEarned
      ))

      // 2. update session completions immediately to fulfill the NO-REPETITION critical fix
      if (isCorrect) {
        _sessionCompletedQuestionIds.update { it + questionId }
      }

      // 3. Update scores, streaks, level progressions
      val oldProgress = repository.getUserProgressDirect() ?: UserProgressEntity()
      var newStreak = oldProgress.dailyStreak
      val now = System.currentTimeMillis()
      val oneDayMs = 24 * 60 * 60 * 1000L
      val windowLimit = (oneDayMs * 1.5).toLong()

      if (oldProgress.lastActiveTimestamp == 0L) {
        newStreak = 1
      } else {
        val diff = now - oldProgress.lastActiveTimestamp
        if (diff in 0L..windowLimit) {
          // within window, keep streak or wait for tomorrow
          if (diff > oneDayMs * 0.8) {
            newStreak += 1
          }
        } else {
          newStreak = 1 // reset
        }
      }

      val newPoints = oldProgress.scorePoints + scoreEarned
      val totalQuestions = oldProgress.totalQuestionsJoined + 1
      val totalCorrect = oldProgress.totalCorrectAnswers + (if (isCorrect) 1 else 0)

      repository.insertOrUpdateUserProgress(UserProgressEntity(
        id = "main_user",
        dailyStreak = newStreak,
        lastActiveTimestamp = now,
        scorePoints = newPoints,
        totalQuestionsJoined = totalQuestions,
        totalCorrectAnswers = totalCorrect
      ))

      // 4. Handle wrong answer tracking (錯題本 2.0)
      if (!isCorrect) {
        val existingMistake = repository.getMistakeById(questionId)
        val failureCount = (existingMistake?.timesFailed ?: 0) + 1
        repository.insertMistake(MistakeEntity(
          questionId = questionId,
          subject = q.subject,
          topic = q.topic,
          reasonTag = if (reasonTag.isNotEmpty()) reasonTag else "Concept Gap",
          timestamp = timestamp,
          timesFailed = failureCount,
          userNotes = "需要加強底層對抗邏輯。"
        ))
      } else {
        // If it was in mistakes, can clear or leave for reference. Let's leave for reference or delete.
        repository.deleteMistake(questionId)
      }

      // 5. Dopamine Badge Unlocks
      checkBadgeUnlocks(newStreak, newPoints, q, isCorrect)
    }
  }

  suspend fun deleteMistakeRecord(questionId: String) {
    repository.deleteMistake(questionId)
  }

  private suspend fun checkBadgeUnlocks(streak: Int, points: Int, question: QuestionEntity, isCorrect: Boolean) {
    val timestamp = System.currentTimeMillis()
    if (isCorrect) {
      // 1. Beginner badge
      repository.insertBadge(BadgeEntity(
        id = "badge_starter",
        title = "星級起航",
        description = "成功答對文憑試改寫題第 1 步！",
        iconName = "stars",
        unlockTimestamp = timestamp
      ))

      // 2. Methodology specific badges
      when (question.methodologyType) {
        "Transformation Thinking" -> repository.insertBadge(BadgeEntity(
          id = "badge_method_transform",
          title = "轉化思維大師",
          description = "完美掌握二次方程與代數轉換底層對抗思維模式！",
          iconName = "psychology",
          unlockTimestamp = timestamp
        ))
        "Rate of Change" -> repository.insertBadge(BadgeEntity(
          id = "badge_method_rate",
          title = "微積分勇士",
          description = "洞悉圖形平移與三階變化率中拐點的連續轉換直覺！",
          iconName = "trending_up",
          unlockTimestamp = timestamp
        ))
        "Condition Decomposition" -> repository.insertBadge(BadgeEntity(
          id = "badge_method_decompose",
          title = "條件拆解奇才",
          description = "輕松將複雜解析幾何邊界條件按部就班地解構、攻破！",
          iconName = "grid_view",
          unlockTimestamp = timestamp
        ))
        "Modeling Ability" -> repository.insertBadge(BadgeEntity(
          id = "badge_method_model",
          title = "數學抽象領袖",
          description = "將多項學科文字及比率問題精準建構成等比等差數學數列模型！",
          iconName = "analytics",
          unlockTimestamp = timestamp
        ))
      }
    }

    if (streak >= 3) {
      repository.insertBadge(BadgeEntity(
        id = "badge_streak_3",
        title = "超級自修狂人",
        description = "連續 3 天堅持登陸並答題演練，多巴胺熊熊燃燒！",
        iconName = "local_fire_department",
        unlockTimestamp = timestamp
      ))
    }
  }

  // Get dynamic linear interpolation predicted grade
  fun getPredictedGradeAndCutoff(subject: String): GradeForecast {
    // Cut-offs percentages definition (from PRD)
    // 5**: 92%, 5*: 85%, 5: 75%, 4: 60%, 3: 45%, 2: 30%, 1: 15%
    val subjectCompleted = completedQuestions.value.filter { it.subject == subject }
    val correctCountOrSimulated = subjectCompleted.count { it.isCorrect }
    val totalCount = subjectCompleted.size

    val scorePercentage = if (totalCount > 0) {
      (correctCountOrSimulated.toDouble() / totalCount * 100).toInt()
    } else {
      // Simulate base starts dynamically
      45 // Level 3 starter mock
    }

    val (currentGrade, nextGrade, diffText, progressFraction) = when {
      scorePercentage >= 92 -> {
        val nextDiff = 100 - scorePercentage
        val fraction = (scorePercentage - 92) / 8f
        val fractionClamped = fraction.coerceIn(0f, 1f)
        GradeForecast("5**", "MAX", "", fractionClamped, scorePercentage)
      }
      scorePercentage >= 85 -> {
        val nextDiff = 92 - scorePercentage
        val fraction = (scorePercentage - 85) / 7f
        GradeForecast("5*", "5**", "距離 5** 級只差 $nextDiff%", fraction, scorePercentage)
      }
      scorePercentage >= 75 -> {
        val nextDiff = 85 - scorePercentage
        val fraction = (scorePercentage - 75) / 10f
        GradeForecast("5", "5*", "距離 5* 級只差 $nextDiff%", fraction, scorePercentage)
      }
      scorePercentage >= 60 -> {
        val nextDiff = 75 - scorePercentage
        val fraction = (scorePercentage - 60) / 15f
        GradeForecast("4", "5", "距離 5 級只差 $nextDiff%", fraction, scorePercentage)
      }
      scorePercentage >= 45 -> {
        val nextDiff = 60 - scorePercentage
        val fraction = (scorePercentage - 45) / 15f
        GradeForecast("3", "4", "距離 4 級只差 $nextDiff%", fraction, scorePercentage)
      }
      scorePercentage >= 30 -> {
        val nextDiff = 45 - scorePercentage
        val fraction = (scorePercentage - 30) / 15f
        GradeForecast("2", "3", "距離 3 級只差 $nextDiff%", fraction, scorePercentage)
      }
      scorePercentage >= 15 -> {
        val nextDiff = 30 - scorePercentage
        val fraction = (scorePercentage - 15) / 15f
        GradeForecast("1", "2", "距離 2 級只差 $nextDiff%", fraction, scorePercentage)
      }
      else -> {
        val nextDiff = 15 - scorePercentage
        val fraction = scorePercentage / 15f
        GradeForecast("U", "1", "距離 1 級只差 $nextDiff%", fraction, scorePercentage)
      }
    }

    return GradeForecast(currentGrade, nextGrade, diffText, progressFraction, scorePercentage)
  }

  // AI Logic Tutor Analysis leveraging gemini-3.5-flash with proper secure secret keys
  fun requestAiTutorAnalysis(question: QuestionEntity, userSelectedReason: String = "") {
    viewModelScope.launch {
      _aiTutorLoading.value = true
      _aiTutorExplanation.value = "AI 正在以香港 DSE 閱卷員視角拆解該改寫試卷的考點和邏輯陷阱，請稍等..."
      withContext(Dispatchers.IO) {
        val customPrompt = """
          你是一位資深的香港中學文憑試（HKDSE）星級導師和前閱卷員。
          請幫學生對照並拆解以下改寫過的 ${question.subject.uppercase()} 科試題：
          
          課題：${question.topicChinese} (${question.topic})
          題型：單項選擇題
          難度：${question.difficulty}
          試題原文/改寫內容：
          "${question.questionText}"
          A. ${question.optionA}
          B. ${question.optionB}
          C. ${question.optionC}
          D. ${question.optionD}
          正確答案是：${question.correctAnswer}。
          
          底層思維邏輯框架：${question.methodologyType} (轉化思維或拆解轉換)
          題目出處：${question.originalRef}
          
          ${if(userSelectedReason.isNotEmpty()) "學生答錯此題，原因為：$userSelectedReason。" else ""}
          
          請提供以下三部分非常專業、生動有溫度的粵語/繁體中文（港式口語與學術交織尤佳，例如「你只要get到呢個位」、「唔使驚」等）星級指導：
          
          1. 🎯 【底層邏輯】：拆解到底這條試題背後，DSE想考學生的「底層邏輯」和「思維框架」是甚麼？（用1-2句直接篤破考評局的秘密）
          2. 💡 【解題痛點】：為甚麼一般學生會落入陷阱？(${if(userSelectedReason.isNotEmpty()) "特別是針對「" + userSelectedReason + "」這點，" else ""}我們日常操卷要怎樣克服？)
          3. 🚀 【改寫妙處】：簡短說明原考卷的哪一條歷屆題目被抽取了相同的邏輯因子，並完美轉換到現在這條新題上。
          
          字數在 350 字左右，排版使用 Emoji 與 markdown 格式以便展示。
        """.trimIndent()

        // Call Gemini REST Endpoint Safely
        try {
          val apiKey = com.example.BuildConfig.GEMINI_API_KEY
          if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _aiTutorExplanation.value = "⚠️ 未在 AI Studio 設置有效的 GEMINI_API_KEY，請點擊「提示」查看内置的星級口訣解析：\n\n${question.explanationDetailed}"
            _aiTutorLoading.value = false
            return@withContext
          }

          val mediaType = "application/json".toMediaType()
          val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
          val requestAdapter = moshi.adapter(GeminiRequest::class.java)

          val geminiReq = GeminiRequest(
            contents = listOf(
              GeminiContent(
                parts = listOf(GeminiPart(text = customPrompt))
              )
            )
          )
          val requestJson = requestAdapter.toJson(geminiReq)

          val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

          val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(requestJson.toRequestBody(mediaType))
            .build()

          val response = client.newCall(request).execute()
          if (response.isSuccessful) {
            val bodyString = response.body?.string() ?: ""
            val responseAdapter = moshi.adapter(GeminiResponse::class.java)
            val geminiResponse = responseAdapter.fromJson(bodyString)
            val generatedText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (!generatedText.isNullOrEmpty()) {
              _aiTutorExplanation.value = generatedText
            } else {
              _aiTutorExplanation.value = "⚠️ AI 導師暫時遊魂去咗搵 YY Lam 飲茶，請看內置的本題星級口訣：\n\n${question.explanationDetailed}"
            }
          } else {
            _aiTutorExplanation.value = "⚠️ 連接 AI 導師服務失敗 (代碼 ${response.code})。以下為內置解法：\n\n${question.explanationDetailed}"
          }
        } catch (e: Exception) {
          _aiTutorExplanation.value = "⚠️ 網絡連接超時或未設置金鑰。以下為內置口訣分析：\n\n${question.explanationDetailed}"
        } finally {
          _aiTutorLoading.value = false
        }
      }
    }
  }

  // Pre-populate rewritten questions across Math, Physics, Chemistry, English
  private suspend fun preloadInitialQuestions() {
    val existing = database.dseDao().getAllQuestionsFlow().firstOrNull()
    if (existing != null && existing.size >= 24) return

    val list = listOf(
      // --- MATH TOPIC 1: Transformation Thinking ---
      QuestionEntity(
        id = "math_eq_q1",
        subject = "math",
        topic = "Quadratic Equations",
        topicChinese = "二次方程兩根乘積關係",
        difficulty = "Easy",
        questionText = "已知 \\(\\alpha\\) 和 \\(\\beta\\) 為方程 \\(3x^2 + 5x - 2 = 0\\) 的非零根。求 \\((\\alpha + \\beta)\\) 與 \\(\\alpha\\beta\\) 乘積的值。",
        optionA = "25 / 9",
        optionB = "10 / 9",
        optionC = "-5 / 9",
        optionD = "3 / 5",
        correctAnswer = "B",
        explanationHint = "利用兩根之和 \\(\\alpha + \\beta = -b/a\\) 及 兩根之積 \\(\\alpha\\beta = c/a\\)。",
        explanationDetailed = "【名師口訣：兩根之和積，公式直接出，唔使估估下！】\n\n1. 二解方程對比其根係數關係 \$ax^2+bx+c=0\$。\n2. 兩根之和 \$\\alpha + \\beta = -b/a = -5/3\$\n3. 兩根之積 \$\\alpha\\beta = c/a = -2/3\$\n4. 所求乘積為 \$(\\alpha + \\beta) \\times \\alpha\\beta = (-5/3) \\times (-2/3) = 10/9\$。正確答案為 B。",
        methodologyType = "Transformation Thinking",
        stepNotes = "1. 識別 ax^2+bx+c=0 係數: a=3, b=5, c=-2\n2. 導出兩根和及乘積公式並運算之\n3. 乘以二者求得答案 10/9",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2020 DSE Math Paper 1 Q2 改寫（考評局百分百對抗考核點保持不變）"
      ),
      // --- MATH TOPIC 2: Rate of Change Intuition ---
      QuestionEntity(
        id = "math_eq_q2",
        subject = "math",
        topic = "Graph Shifting & Turning Points",
        topicChinese = "圖像平移及三次拐點",
        difficulty = "Medium",
        questionText = "已知三次函數 \\(f(x) = x^3 - 3x^2 + 4\\)。若將此圖像向下平移 2 單位，其新圖像的拐點（Point of Inflection）坐標為？",
        optionA = "(1, 2)",
        optionB = "(1, -1)",
        optionC = "(1, 0)",
        optionD = "(2, 0)",
        correctAnswer = "C",
        explanationHint = "三次函數的拐點位於第二階導數等於零的位置 \$f''(x) = 0\$。",
        explanationDetailed = "【名師口訣：求二階導數得拐點，平移只考Y坐標變動！】\n\n1. 微分一次 \$f'(x) = 3x^2 - 6x\$\n2. 微分兩次 \$f''(x) = 6x - 6\$\n3. 令 \$f''(x) = 0 \\implies x=1\$。代入原式求 \$y\$：\$f(1) = 1 - 3 + 4 = 2\$。故原圖像拐點為 (1, 2)。\n4. 向下平移 2 單位，Y 坐標減去 2。所以新拐點為 (1, 0)。正確答案為 C。",
        methodologyType = "Rate of Change",
        stepNotes = "1. 導出二階導數公式\n2. 解 x=1 拐點點位置\n3. 執行水平、垂直移動對稱運算 (1, 2-2) -> (1,0)",
        marks = 4,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2021 DSE Math Paper 1 Q5 改寫"
      ),
      // --- MATH TOPIC 3: Condition Decomposition ---
      QuestionEntity(
        id = "math_eq_q3",
        subject = "math",
        topic = "Coordinate Geometry Boundaries",
        topicChinese = "直線垂直與解析幾何邊界",
        difficulty = "Hard",
        questionText = "設 \\(L\\) 為直線 \\(3x + 4y - 12 = 0\\)。直線 \\(L_1\\) 與 \\(L\\) 垂直，且 \\(L_1\\) 與坐標軸在坐標平面上圍成的三角形面積為 6 單位。求 \\(L_1\\) 的可能方程之一。",
        optionA = "4x - 3y + 12 = 0",
        optionB = "4x - 3y - 6 = 0",
        optionC = "3x - 4y + 12 = 0",
        optionD = "4x + 3y - 12 = 0",
        correctAnswer = "A",
        explanationHint = "垂直直線的斜率之積為 -1。設新直線為 \\(4x - 3y + C = 0\\) 並求其截距。",
        explanationDetailed = "【名師口訣：垂直斜率變負倒數，面積公式推導C截距！】\n\n1. 直線 \$L\$ 的斜率為 \$-3/4\$。垂直線 \$L_1\$ 的斜率必為 \$4/3\$。\n2. 可設 \$L_1\$ 方程為 \$y = (4/3)x + k \\implies 4x - 3y + 3k = 0\$。設常數 \$C = 3k\$，即 \$4x - 3y + C = 0\$。\n3. 求 \$L_1\$ 的坐標截距：當 \$y=0\$ 時 \$x = -C/4\$；當 \$x=0\$ 時 \$y = C/3\$。\n4. 圍成三角形面積為 0.5 * |-C/4| * |C/3| = C^2/24 = 6, 故 C^2 = 144, 提取 C = 12 或 -12。\n5. 故 \$L_1\$ 的方程為 \$4x - 3y \\pm 12 = 0\$。選項 A 符合。正確答案為 A。",
        methodologyType = "Condition Decomposition",
        stepNotes = "1. 利用斜率性質 m1*m2 = -1 導出L1型為 4x - 3y + C = 0\n2. 運算代數截距計算三角形面積 0.5 * |x_int| * |y_int| = 6\n3. 提取 C = 12 或 -12 答案。",
        marks = 5,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2019 DSE Math Paper 2 Q33 改寫"
      ),
      // --- MATH TOPIC 4: Modeling sequence ---
      QuestionEntity(
        id = "math_eq_q4",
        subject = "math",
        topic = "Exponential Sequences",
        topicChinese = "荷葉面積等比數列遞增",
        difficulty = "Hard",
        questionText = "某池塘的荷葉每日以等比數列規律繁殖。在第 3 天，荷葉面積為 18 平方米；在第 6 天，其面積為 144 平方米。問在第 8 天，荷葉面積是多少平方米？",
        optionA = "288",
        optionB = "576",
        optionC = "720",
        optionD = "1152",
        correctAnswer = "B",
        explanationHint = "設第 n 天的荷葉面積為 \\(T_n = a \\times r^{n-1}\\)。利用方程比求比值 \\(r\\)。",
        explanationDetailed = "【名師口訣：等比相除求公比，幾何規律一擊即破！】\n\n1. 第 3 天：\$a r^2 = 18\$\n2. 第 6 天：\$a r^5 = 144\$\n3. 兩式相除：r^3 = 144/18 = 8, 得出 r = 2\n4. 代回第一個方程求 \$a\$：\$a (2)^2 = 18 \\implies a = 4.5\$平方米。\n5. 第 8 天面積為：\$T_8 = a r^7 = 4.5 \\times 128 = 576\$平方米。正確答案為 B。",
        methodologyType = "Modeling Ability",
        stepNotes = "1. 列出指數等比公式 T_n = a*r^(n-1)\n2. 方程比求解公比 r=2 及其基項 a=4.5\n3. 代入n=8 運算並獲得576。",
        marks = 5,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2023 DSE Math Paper 1 Q14 改寫（文字建模實戰）"
      ),

      // --- PHYSICS QUESTIONS ---
      QuestionEntity(
        id = "phys_q1",
        subject = "physics",
        topic = "Mechanics & Kinetic Energy",
        topicChinese = "牛頓定律與動能轉換",
        difficulty = "Medium",
        questionText = "一個質量為 2 kg 的物體從靜止狀態開始，在 10 N 的水平恆定淨力作用下移動了 5 m。求物體的最終動能（Kinetic Energy）。",
        optionA = "10 J",
        optionB = "25 J",
        optionC = "50 J",
        optionD = "100 J",
        correctAnswer = "C",
        explanationHint = "根據功與能原理（Work-Energy Theorem），淨力所做的功等於物體動能的增加量。",
        explanationDetailed = "【理科思維：淨力作功即為所得動能，根本不需要求速度！】\n\n1. 淨力做功公式：\$W = F \\times s\$\n2. 代入數值：\$W = 10 \\text{ N} \\times 5 \\text{ m} = 50 \\text{ J}\$\n3. 因為物體由靜止出發，初始動能 \$KE_i = 0\$。故最終動能為 50 J。正確答案為 C。",
        methodologyType = "General",
        stepNotes = "1. 功的定義: W = F * s\n2. 由於靜止出發，作功100%轉化為動能。",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2022 DSE Physics Q5 改寫"
      ),
      QuestionEntity(
        id = "phys_q2",
        subject = "physics",
        topic = "Electricity",
        topicChinese = "並聯電路電阻分析",
        difficulty = "Easy",
        questionText = "將兩個電阻值分別為 \\(6\\,\\Omega\\) 和 \\(12\\,\\Omega\\) 的電阻器並聯連接。求其等效電阻（Equivalent Resistance）。",
        optionA = "18 Ω",
        optionB = "9 Ω",
        optionC = "4 Ω",
        optionD = "3 Ω",
        correctAnswer = "C",
        explanationHint = "並聯等效電阻公式：\\(1 / R_{eq} = 1 / R_1 + 1 / R_2\\)。",
        explanationDetailed = "【DSE物理口訣：並聯越並越小，答案必然小於其中任何一個電阻！】\n\n1. 計算倒數之和：1/R_eq = 1/6 + 1/12 = 3/12 = 1/4\n2. 故等效電阻 \$R_{eq} = 4 \\,\\Omega\$。正確答案為 C。",
        methodologyType = "General",
        stepNotes = "1. 並聯公式倒數相加\n2. 倒數並運算得出 4 Ω",
        marks = 2,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2020 DSE Physics Q21 改寫"
      ),

      // --- CHEMISTRY QUESTIONS ---
      QuestionEntity(
        id = "chem_q1",
        subject = "chemistry",
        topic = "Metal Reactivity Series",
        topicChinese = "金屬反應活性序列",
        difficulty = "Easy",
        questionText = "下列哪一種金屬在與稀鹽酸反應時產生氫氣的速度最快？",
        optionA = "銅 (Copper)",
        optionB = "鐵 (Iron)",
        optionC = "鎂 (Magnesium)",
        optionD = "鋅 (Zinc)",
        correctAnswer = "C",
        explanationHint = "化學金屬活性順序：K > Na > Ca > Mg > Al > Zn > Fe ... > Cu。",
        explanationDetailed = "【活性排位背口訣：鉀鈉鈣鎂鋁、鋅鐵錫鉛氫、銅汞銀鉑金！】\n\n1. 活性越大，與酸反應時氣泡及熱量產生得越劇烈。\n2. 銅 (Cu) 在活性列中排在氫 (H) 之後，不會與稀鹽酸產生反應。\n3. 在鐵、鎂、鋅當中，鎂 (Mg) 活性最強，反應速度最快。正確答案為 C。",
        methodologyType = "General",
        stepNotes = "1. 比對 K, Na, Ca, Mg, Al 活性順序。\n2. 鎂在眾人中活性最強。",
        marks = 2,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2021 DSE Chem Q3 改寫"
      ),
      QuestionEntity(
        id = "chem_q2",
        subject = "chemistry",
        topic = "Acid and Base pH",
        topicChinese = "酸鹼濃度與pH計算",
        difficulty = "Medium",
        questionText = "若將 \\(0.01\\text{ M}\\) 的鹽酸（\\(\\text{HCl}\\)）稀釋100倍，其稀釋後新溶液的 \\(\\text{pH}\\) 值是多少？",
        optionA = "pH = 2",
        optionB = "pH = 3",
        optionC = "pH = 4",
        optionD = "pH = 5",
        correctAnswer = "C",
        explanationHint = "鹽酸為強酸。先計算稀釋後的氫離子濃度 \\([\\text{H}^+] = 0.01 / 100\\)，然後利用 \\(\\text{pH} = -\\log_{10}[\\text{H}^+]\\) 計算值。",
        explanationDetailed = "【稀釋口訣：稀釋10倍強酸，pH上升1度；100倍即上升2度！】\n\n1. 物種鹽酸在水中全游離。[H+] 初始 = \$0.01 \\text{ M} = 10^{-2} \\text{ M}\$，對應初始 pH 為 2。\n2. 稀釋100倍，體積增加100倍，[H+] 濃度縮減到初始的 \$1/100\$。\n3. 稀釋後 \$[H+] = 0.01 / 100 = 0.0001 \\text{ M} = 10^{-4} \\text{ M}\$。\n4. \$pH = -\\log(10^{-4}) = 4\$。正確答案為 C。",
        methodologyType = "General",
        stepNotes = "1. 計算稀釋後濃度: 0.01 / 100 = 10^-4 M\n2. 取負對數得 pH = 4。",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2018 DSE Chem Q10 改寫"
      ),

      // --- ENGLISH QUESTIONS ---
      QuestionEntity(
        id = "eng_q1",
        subject = "english",
        topic = "Reading Lexicon & Synonym",
        topicChinese = "學術閱讀字彙與同義詞辨析",
        difficulty = "Medium",
        questionText = "Identify the word that is closest in meaning to 'ubiquitous' as frequently found in academic reading comprehension of HKDSE Part A of Paper 1.",
        optionA = "Extremely ephemeral and fleeting",
        optionB = "Omnipresent and universally found",
        optionC = "Deeply problematic or controversial",
        optionD = "Highly exclusive to wealthy subsets",
        correctAnswer = "B",
        explanationHint = "The suffix -ous represents full of, and ubi- traces back to elements meaning 'everywhere'.",
        explanationDetailed = "【星級英文秘笈：背熟學術字彙，直接領跑閱讀卷！】\n\n'Ubiquitous' means present, appearing, or found everywhere. 'Omnipresent' carries exactly the same definition (present everywhere simultaneously). Option B is the perfect match. A represents fleeting, C means controversial, D denotes exclusive.",
        methodologyType = "General",
        stepNotes = "1. Parse English morphological roots: ubiq- means everywhere\n2. Match with omnipresent.",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "DSE Paper 1 Academic Reading Vocab 改寫"
      ),
      QuestionEntity(
        id = "eng_q2",
        subject = "english",
        topic = "Subject-Verb Agreement",
        topicChinese = "學術文體主謂一致性語法",
        difficulty = "Hard",
        questionText = "Complete the sentence: 'Neither the research assistants nor the principal investigator ________ prepared to deliver the presentation in the upcoming Hong Kong Academic Summit.'",
        optionA = "are",
        optionB = "is",
        optionC = "be",
        optionD = "have been",
        correctAnswer = "B",
        explanationHint = "For subjects joined by 'neither... nor...', the verb agrees with the subject closest to it.",
        explanationDetailed = "【星級文法口訣：Neither... nor... 靠近原則，睇最貼個主語！】\n\n1. 當多個主體由 Neither... nor... 連接時，動詞形式受最靠近的主語決定。\n2. 最貼近動詞空缺的主語是 'the principal investigator'（單數名詞）。\n3. 故此動詞必須為單數形式：is。正確答案為 B。",
        methodologyType = "General",
        stepNotes = "1. 識別 Neither ... nor 語法\n2. 識別靠近主語 'the principal investigator' (單數)\n3. 填寫單數動詞 'is'",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "DSE Paper 2 Writing Grammatical Accuracy 改寫"
      ),

      // --- 2012 DSE MATHS ADAPTED ---
      QuestionEntity(
        id = "dse_2012_math_q",
        subject = "math",
        topic = "Polynomials: Remainder Theorem",
        topicChinese = "多項式：餘數定理與因式定理",
        difficulty = "Medium",
        questionText = "設 \\(P(x) = 2x^3 - kx^2 + 3x - 2\\)。若 \\(P(x)\\) 能被 \\(x - 2\\) 整除，求當 \\(P(x)\\) 除以 \\(2x + 1\\) 時的餘數。",
        optionA = "-5",
        optionB = "-3.5",
        optionC = "-2",
        optionD = "0",
        correctAnswer = "A",
        explanationHint = "利用因式定理，代入 \\(P(2) = 0\\) 求出 \\(k\\) 的值。然後利用餘數定理，計算 \\(P(-1/2)\\) 即為餘數。",
        explanationDetailed = "【九合一口訣：整除代入等於零，求餘直接代入！】\n\n1. 根據因式定理，P(2) = 0 得：\n2(2)^3 - k(2)^2 + 3(2) - 2 = 16 - 4k + 6 - 2 = 0, \n解出 k = 5。\n2. 多項式為 P(x) = 2x^3 - 5x^2 + 3x - 2。\n3. 除以 2x + 1 的餘數為 P(-1/2)：\n餘數 = 2(-1/2)^3 - 5(-1/2)^2 + 3(-1/2) - 2 = -0.25 - 1.25 - 1.5 - 2 = -5。\n正確答案為 A。",
        methodologyType = "Condition Decomposition",
        stepNotes = "1. P(2)=0 求 k=5\n2. 代 x = -0.5 求 P(-0.5) = -5",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2012 DSE Math Paper 2 Q10 改寫"
      ),

      // --- 2013 DSE PHYSICS ADAPTED ---
      QuestionEntity(
        id = "dse_2013_phys_q",
        subject = "physics",
        topic = "Decibel Scale and Sound Intensity vs Distance",
        topicChinese = "聲強級分貝與距離平方反比定律",
        difficulty = "Hard",
        questionText = "在距離一個點聲源 3 m 處測得的聲強級為 80 dB。問在距離該聲源 30 m 處測得的聲強級是多少？",
        optionA = "8 dB",
        optionB = "50 dB",
        optionC = "60 dB",
        optionD = "70 dB",
        correctAnswer = "C",
        explanationHint = "聲壓強與距離平方反比 I ∝ 1/d^2。利用分貝公式 ΔL = 10 log10(I2 / I1) 運算。",
        explanationDetailed = "【理科思維：聲強距離10倍，分貝直接扣20 dB！】\n\n1. 聲強與距離的平方成反比：\nI2 / I1 = (3 / 30)^2 = 1/100。\n2. 聲強級變化為：\nΔL = 10 log10(1/100) = -20 dB。\n3. 30 m 處的聲強級為 80 dB - 20 dB = 60 dB。正確答案為 C。",
        methodologyType = "Rate of Change",
        stepNotes = "1. 距離10倍得強度1/100\n2. 算 10 * log10(1/100) = -20 dB\n3. 80 - 20 = 60 dB",
        marks = 4,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2013 DSE Physics Paper 1 Q15 改寫"
      ),

      // --- 2014 DSE CHEMISTRY ADAPTED ---
      QuestionEntity(
        id = "dse_2014_chem_q",
        subject = "chemistry",
        topic = "Mole Calculation & Stoichiometry",
        topicChinese = "摩爾計算、限量反應物與氣體體積",
        difficulty = "Medium",
        questionText = "將 4.86 g 的鎂帶（\\(\\text{Mg}\\)）與 200 mL 的 \\(1.0\\text{ M}\\) 鹽酸（\\(\\text{HCl}\\)）混合反應。求在室溫及常壓（R.T.P.）下釋放出的氫氣最大體積是多少？（常溫常壓下，氣體的摩爾體積 = \\(24.0\\text{ dm}^3/\\text{mol}\\)；相對原子質量：\\(\\text{Mg} = 24.3\\)）",
        optionA = "1.20 dm³",
        optionB = "2.40 dm³",
        optionC = "4.80 dm³",
        optionD = "5.14 dm³",
        correctAnswer = "B",
        explanationHint = "先計算兩個反應物的摩爾數，從而找出「限量反應物」（Limiting Reactant），再依比例計算產物 \\(\\text{H}_2\\) 氣體的摩爾數與體積。",
        explanationDetailed = "【化學名師口訣：計摩爾數先，搵出限量反應物，唔好衝動代錯數！】\n\n1. 反應方程：Mg(s) + 2HCl(aq) -> MgCl2(aq) + H2(g)。\n2. 摩爾數：\n- mol(Mg) = 4.86 / 24.3 = 0.20 mol。\n- mol(HCl) = 1.0 M * 0.2 L = 0.20 mol。\n3. 按 1:2 比例，0.20 mol 的 HCl 只需要 0.10 mol 的 Mg，因此 HCl 是限量反應物，Mg 過剩。\n4. 產生 H2 的摩爾數 = 1/2 * mol(HCl) = 0.10 mol。\n5. H2 的體積 = 0.10 mol * 24.0 dm³/mol = 2.40 dm³。正確答案為 B。",
        methodologyType = "General",
        stepNotes = "1. 平衡方程並算 Mg (0.2 mol) 與 HCl (0.2 mol)\n2. 判斷 HCl 為限量，H2 產生 0.1 mol\n3. 0.1 * 24.0 = 2.40 dm³",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2014 DSE Chem Paper 1 Q12 改寫"
      ),

      // --- 2015 DSE ENGLISH ADAPTED ---
      QuestionEntity(
        id = "dse_2015_eng_q",
        subject = "english",
        topic = "Academic Synonym in Reading Comprehension",
        topicChinese = "閱讀卷學術難字與上下文同義詞推導",
        difficulty = "Medium",
        questionText = "In academic articles reporting on public health intervention programs under Paper 1, what is the meaning of the word 'efficacy' in the sentence: 'The efficacy of the newly developed malaria vaccine was highly acclaimed by global clinical trials'?",
        optionA = "The chemical composition of the medicine",
        optionB = "The commercial profitability of distribution",
        optionC = "The ability to produce the intended or desired outcome",
        optionD = "The physical side effects of high dosages",
        correctAnswer = "C",
        explanationHint = "Look at the context of global clinical trials praising the vaccine. 'Efficacy' relates to efficiency and effective power.",
        explanationDetailed = "【DSE英文科星級秘笈：efficacy 代表『成效/功效』，等同於 effectiveness！】\n\n1. 'Efficacy' 指的是『功效』或『特定藥物/干預產生期望成效的能力』。\n2. Option C 'The ability to produce the intended or desired outcome' 完美詮釋了這個詞。\n3. 因此答案是 C。",
        methodologyType = "General",
        stepNotes = "1. Recognize 'efficacy' is an academic synonym for 'effectiveness'\n2. Correlate with Option C",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2015 DSE English Paper 1 Vocab 改寫"
      ),

      // --- 2016 DSE MATHS ADAPTED ---
      QuestionEntity(
        id = "dse_2016_math_q",
        subject = "math",
        topic = "Coordinate Geometry & Tangents",
        topicChinese = "解析幾何、圓形方程與切線相交邊界",
        difficulty = "Hard",
        questionText = "已知直線 \\(L: 3x - 4y + k = 0\\) 與圓 \\(C: x^2 + y^2 - 4x - 6y - 12 = 0\\) 相切。求 \\(k\\) 的所有可能值。",
        optionA = "k = 31 或 k = -19",
        optionB = "k = 25 或 k = -15",
        optionC = "k = 20 或 k = -30",
        optionD = "k = 18 或 k = -32",
        correctAnswer = "A",
        explanationHint = "一條直線能與圓相切，等同於該圓的中心到這條直線的比直距離等於圓的半徑。圓方程可配方得到中心坐標和半徑。",
        explanationDetailed = "【幾何口訣：切線相切，圓心到直線距離等於半徑，配方最基本！】\n\n1. 將圓的方程配方 (Completing Square)：\n\\((x-2)^2 + (y-3)^2 = 25\\)\n得出圓心坐標為 (2, 3)，半徑 r = 5。\n\n2. 圓心到直線的垂直距離等於半徑：\n\\(d = |3(2) - 4(3) + k| / \\sqrt{3^2 + (-4)^2} = 5\\)\n\\(|k - 6| = 25\\)\n\n3. 解絕對值方程：\n- k - 6 = 25 ⟹ k = 31\n- k - 6 = -25 ⟹ k = -19\n\n故 k 的可能值為 31 或 -19。正確答案為 A。",
        methodologyType = "Condition Decomposition",
        stepNotes = "1. 圓方程配方求圓心 (2,3) 及半徑 r=5\n2. 點到直線距離公式 d = |3*2 - 4*3 + k| / 5 = 5\n3. |k - 6| = 25 ⟹ k = 31 或 k = -19",
        marks = 5,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2016 DSE Math Paper 2 Q38 改寫"
      ),

      // --- 2017 DSE PHYSICS ADAPTED ---
      QuestionEntity(
        id = "dse_2017_phys_q",
        subject = "physics",
        topic = "Refraction & Total Internal Reflection",
        topicChinese = "折射率與全內反射臨界角",
        difficulty = "Medium",
        questionText = "一束光線由折射率為 \\(1.6\\) 的玻璃射向空氣。求當此光線由該玻璃射向空氣時，發生全內反射（Total Internal Reflection）的臨界角（Critical Angle）為多少？",
        optionA = "30.0°",
        optionB = "38.7°",
        optionC = "45.0°",
        optionD = "51.3°",
        correctAnswer = "B",
        explanationHint = "臨界角 \\(\\theta_c\\) 滿足公式 \\(\\sin(\\theta_c) = 1 / n\\)，其中 \\(n\\) 為玻璃折射率。在此處為 \\(n = 1.6\\)。",
        explanationDetailed = "【物理光學口訣：密去疏先有全反射，臨界角正弦值就是折射率的倒數！】\n\n1. 臨界角 θc 公式為：\nsin(θc) = 1 / n = 1 / 1.6 = 0.625。\n2. 計算反三角函數：\nθc = sin^-1(0.625) ≈ 38.7°。\n\n正確答案為 B。",
        methodologyType = "General",
        stepNotes = "1. 代入臨界角正弦公式 sin(theta_c) = 1 / 1.6 = 0.625\n2. 取 arcsin 獲得約 38.7°",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2017 DSE Physics Paper 1 Q22 改寫"
      ),

      // --- 2018 DSE CHEMISTRY ADAPTED ---
      QuestionEntity(
        id = "dse_2018_chem_q",
        subject = "chemistry",
        topic = "Chemical Bonding and Structure",
        topicChinese = "化學鍵、晶體結構與熔沸點對比",
        difficulty = "Medium",
        questionText = "下列哪一種第三週期（Period 3）的氧化物在常溫下以巨型共價網絡結構（Giant Covalent Network）存在，並且熔點最高？",
        optionA = "氧化鈉 (Sodium oxide)",
        optionB = "二氧化矽 (Silicon dioxide)",
        optionC = "氧化鋁 (Aluminium oxide)",
        optionD = "二氧化硫 (Sulfur dioxide)",
        correctAnswer = "B",
        explanationHint = "巨型共價網絡結構是由無數原子通過共價鍵互相連接構成的晶體。二氧化矽（或稱石英）就是最具代表性的例子。",
        explanationDetailed = "【化學背誦口訣：第三週期氧化物，二氧化矽巨型共價熔點高！】\n\n1. 氧化鈉 (Na2O) 和氧化鋁 (Al2O3) 是巨型離子結構。\n2. 二氧化硫 (SO2) 是簡單分子結構，分子間僅靠弱范德華力結合，常溫是氣體。\n3. 二氧化矽 (SiO2) 是巨型共價網絡結構，熔點極高。正確答案為 B。",
        methodologyType = "General",
        stepNotes = "1. 化學結構分析：離子、共價網絡、分子\n2. 識別二氧化矽為巨型共價晶體",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2018 DSE Chem Paper 1 Q7 改寫"
      ),

      // --- 2019 DSE ENGLISH ADAPTED ---
      QuestionEntity(
        id = "dse_2019_eng_q",
        subject = "english",
        topic = "Prepositions & Cohesive Connectives",
        topicChinese = "寫作語法：讓步介詞與高級轉折連接詞",
        difficulty = "Hard",
        questionText = "Complete the academic sentence below: '________ the prominent environmental and financial setbacks associated with nuclear fission, it remains a pivotal component of global zero-emission strategies.'",
        optionA = "In addition to",
        optionB = "Notwithstanding",
        optionC = "Consequently",
        optionD = "On the contrary",
        correctAnswer = "B",
        explanationHint = "The sentence describes negative aspects of nuclear fission (setbacks) but establishes that it remains highly important (pivotal component). This is a contrast or concession. Choose a preposition meaning 'despite'.",
        explanationDetailed = "【DSE星級語法秘笈：Notwithstanding 是引導讓步關係的高級介詞（相當於 Despite），後面加名詞短語！】\n\n1. 空格後是名詞短語 'the prominent setbacks...'。\n2. 語義上，前半句是缺點，後半句是其依然重要的事實，故形成讓步轉折關係。\n3. 'Notwithstanding'（儘管）在此處引導名詞短語，完美符合。正確答案為 B。",
        methodologyType = "General",
        stepNotes = "1. Contrast sentence logic: negative vs positive\n2. Match with Notwithstanding (Despite)",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2019 DSE English Writing Grammatical Accuracy 改寫"
      ),

      // --- 2020 DSE MATHS ADAPTED ---
      QuestionEntity(
        id = "dse_2020_math_q",
        subject = "math",
        topic = "Geometric Progressions",
        topicChinese = "等比數列、首兩項與無限項之和",
        difficulty = "Hard",
        questionText = "已知一無限等比數列 (Infinite Geometric Progression) 的首兩項之和為 \\(12\\)，且其無限項之和 (Sum to Infinity) 存在且為 \\(16\\)。求該數列的可能公比 \\(r\\)。",
        optionA = "r = 1/2 或 r = -1/2",
        optionB = "r = 1/2",
        optionC = "r = 1/4 或 r = -1/4",
        optionD = "r = 3/4",
        correctAnswer = "A",
        explanationHint = "設首項為 \\(a\\)，公比為 \\(r\\)。首兩項之和式：\\(a + ar = 12\\)。無限項之和式：\\(a / (1-r) = 16\\)。兩式聯立求解。",
        explanationDetailed = "【聯立等比方程，消去首項a直接得出公比二次方程！】\n\n1. 方程式：\n- 方程 (1)：a(1 + r) = 12\n- 方程 (2)：a / (1 - r) = 16 ⟹ a = 16(1 - r)\n2. 將方程 (2) 代入方程 (1)：\n16(1 - r)(1 + r) = 12 ⟹ 16(1 - r^2) = 12 ⟹ 1 - r^2 = 3/4 ⟹ r^2 = 1/4\n3. 解得：r = 1/2 或 r = -1/2 (兩者均滿足 |r| < 1)。正確答案為 A。",
        methodologyType = "Transformation Thinking",
        stepNotes = "1. 首兩項：a(1+r) = 12\n2. 無限和：a = 16(1-r)\n3. 聯立消去 a 求得 r^2 = 1/4 ⟹ r = ±1/2",
        marks = 4,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2020 DSE Math Paper 2 Q40 改寫"
      ),

      // --- 2021 DSE PHYSICS ADAPTED ---
      QuestionEntity(
        id = "dse_2021_phys_q",
        subject = "physics",
        topic = "Newton's Laws of Motion & Friction",
        topicChinese = "牛頓運動定律、斜坡匀速滑行與摩擦力",
        difficulty = "Easy",
        questionText = "一個質量為 \\(2\\text{ kg}\\) 的木塊以恆定速度 \\(3\\text{ m/s}\\) 沿著一個與水平成 \\(30^\\circ\\) 的粗糙斜坡下滑。求作用在木塊上的摩擦力是多少？（重力加速度 \\(g = 9.81\\text{ m/s}^2\\)）",
        optionA = "5.88 N",
        optionB = "9.81 N",
        optionC = "16.99 N",
        optionD = "19.62 N",
        correctAnswer = "B",
        explanationHint = "「恆定速度下滑」代表物體處予平衡狀態（Equilibrium），即合外力為零。下滑分力等於摩擦力。",
        explanationDetailed = "【物理概念：匀速即是合力為零！斜面上下滑分力等於摩擦力！】\n\n1. 因為木塊是匀速運動，所以合外力為零。\n2. 沿斜面向下的重力分力為：\nF_down = m g sinθ = 2 * 9.81 * sin(30°) = 9.81 N。\n3. 因此摩擦力大小也是 9.81 N。正確答案為 B。",
        methodologyType = "Condition Decomposition",
        stepNotes = "1. 匀速表示合外力為 0\n2. 摩擦力與重力沿斜坡分力相平衡 f = mg*sin(30°)\n3. 2 * 9.81 * 0.5 = 9.81 N",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2021 DSE Physics Paper 1 Q6 改寫"
      ),

      // --- 2022 DSE CHEMISTRY ADAPTED ---
      QuestionEntity(
        id = "dse_2022_chem_q",
        subject = "chemistry",
        topic = "Volumetric Neutralization Reaction",
        topicChinese = "酸鹼滴定、二元酸與摩爾濃度中和",
        difficulty = "Medium",
        questionText = "在一次滴定實驗中，需要 \\(30.0\\text{ mL}\\) 的 \\(0.12\\text{ M}\\) 氫氧化鈉溶液（\\(\\text{NaOH}\\)）來完全中和 \\(15.0\\text{ mL}\\) 的某二元酸（\\(\\text{H}_2\\text{A}\\)）水溶液。求該二元酸的摩爾濃度是多少？",
        optionA = "0.06 M",
        optionB = "0.12 M",
        optionC = "0.24 M",
        optionD = "0.48 M",
        correctAnswer = "B",
        explanationHint = "注意二元酸 \\(\\text{H}_2\\text{A}\\) 每個分子可以釋放兩個 \\(\\text{H}^+\\) 離子。寫出中和反應方程，摩爾比為 1:2。",
        explanationDetailed = "【滴定核心：二元酸中和二倍強鹼，反應摩爾比為 1:2！】\n\n1. 中和反應方程式：H2A(aq) + 2NaOH(aq) -> Na2A(aq) + 2H2O(l)。\n2. NaOH 摩爾數 = 0.12 mol/L * 0.030 L = 0.0036 mol。\n3. H2A 摩爾數為 NaOH 的一半 = 0.0018 mol。\n4. 二元酸的濃度 = 0.0018 mol / 0.015 L = 0.12 M。正確答案為 B。",
        methodologyType = "General",
        stepNotes = "1. 方程 H2A + 2NaOH -> Na2A + 2H2O\n2. mol(NaOH) = 0.0036, mol(H2A) = 0.0018\n3. 濃度 M = 0.0018 / 0.015 = 0.12 M",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2022 DSE Chem Paper 1 Q16 改寫"
      ),

      // --- 2023 DSE ENGLISH ADAPTED ---
      QuestionEntity(
        id = "dse_2023_eng_q",
        subject = "english",
        topic = "Participle Clauses",
        topicChinese = "寫作語法：分詞短語句首修飾與主被動主語一致",
        difficulty = "Hard",
        questionText = "Complete the sentence below to fulfill professional sentence variety in HKDSE Paper 2 writing: '________ by the unprecedented success of local startup hubs, the government decided to expand seed funding grants for youth initiatives.'",
        optionA = "Encouraging",
        optionB = "Encouraged",
        optionC = "Having encouraged",
        optionD = "Encourage",
        correctAnswer = "B",
        explanationHint = "Identify whether the main subject 'the government' is actively encouraging someone else or is passively being encouraged by the hub's success.",
        explanationDetailed = "【DSE寫作升級套路：句首分詞短語的主被動，由後面的主語『the government』決定！】\n\n1. 邏輯主體是 'the government'。\n2. 政府是被本地創業中心的成功所『鼓舞』，因此為被動語意，用過去分詞 (Past Participle)。\n3. 故正確答案為 B (Encouraged)。",
        methodologyType = "General",
        stepNotes = "1. Identify clausal reduction rules around verbs of feeling\n2. Since subject 'the government' feels encouraged, choose passive participle Encouraged",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2023 DSE English Writing Grammatical Accuracy 改寫"
      ),

      // --- 2024 DSE MATHS ADAPTED ---
      QuestionEntity(
        id = "dse_2024_math_q",
        subject = "math",
        topic = "System of Inequalities",
        topicChinese = "朝代圖案、線性規劃與不等式區域解答",
        difficulty = "Medium",
        questionText = "下列哪一個坐標點所在的區域滿足以下聯立不等式系統 (System of Inequalities)？\\(x + y \\ge 4\\), \\(2x - y \\le 6\\), \\(y \\ge 1\\)",
        optionA = "(1, 1)",
        optionB = "(3, 2)",
        optionC = "(5, 1)",
        optionD = "(2, 0)",
        correctAnswer = "B",
        explanationHint = "將各個選項的 x 和 y 坐標代入三個不等式，必須同時滿足三個不等式的坐標點才是正確答案。",
        explanationDetailed = "【名師解讀：不等式滿足區域，最快最穩陣的方法，就是直接代入四個選項！】\n\n1. 測試 (1, 1)：1+1=2 ≱ 4 (不符合)。\n2. 測試 (3, 2)：3+2=5 ≥ 4 (合格); 2(3)-2 = 4 ≤ 6 (合格); 2 ≥ 1 (合格)。\n3. 測試 (5, 1)：2(5)-1 = 9 ≰ 6 (不符合)。\n故只有 B 同時完全滿足三個關係。正確答案為 B。",
        methodologyType = "Condition Decomposition",
        stepNotes = "1. 代入法依序檢查各點\n2. 證明 (3, 2) 完全符合，即可選取",
        marks = 3,
        youtubeUrl = "https://www.youtube.com/watch?v=F5vEwMOfB9Y",
        originalRef = "2024 DSE Math Paper 2 Q22 改寫"
      ),

      // --- 2025 DSE PHYSICS ADAPTED ---
      QuestionEntity(
        id = "dse_2025_phys_q",
        subject = "physics",
        topic = "Faraday's and Lenz's Laws of Induction",
        topicChinese = "電磁感應、楞次定律與感應電流方向",
        difficulty = "Hard",
        questionText = "一根條形磁鐵由高處垂直下落，其北極（N極）朝下垂直穿過一個水平固定在空中的圓形閉合銅環。從銅環上方往下看，在磁鐵穿入及穿出銅環的兩段運動過程中，銅環中的感應電流方向分別是如何？",
        optionA = "先是逆時針，然後是順時針",
        optionB = "先是順時針，然後是逆時針",
        optionC = "全程維持逆時針方向",
        optionD = "全程維持順時針方向",
        correctAnswer = "A",
        explanationHint = "根據楞次定律（Lenz's Law），感應電流產生的磁場必在抗阻磁通量的變化。利用右手定則（Right-hand Grip Rule）判斷流向。",
        explanationDetailed = "【楞次定律抗拒變化：來拒去留！近就推開佢，走就吸引佢！】\n\n1. N極往下穿入時：向下磁通量增加，銅環頂部感應為N極排斥它。右手定則判定，逆時針 (Anti-clockwise) 電流。\n2. 磁鐵S極往下穿出時：向下磁通量減少，銅環頂部感應為N極（補充磁場/吸引它），電流方向此時為順時針 (Clockwise)。\n故正確答案為 A。",
        methodologyType = "Rate of Change",
        stepNotes = "1. N極進入頂部生N極抗阻：逆時針\n2. S極離開頂部生N極留助：順時針\n3. 結果：先逆時針，後順時針",
        marks = 4,
        youtubeUrl = "https://www.youtube.com/watch?v=nC8-F_b8wGs",
        originalRef = "2025 DSE Physics Paper 1 Q16 改寫"
      )
    )

    database.dseDao().insertQuestions(list)
  }

  private suspend fun preloadPastPaperResources() {
    val existing = database.dseDao().getAllPastPaperResourcesFlow().firstOrNull() ?: emptyList()
    if (existing.isNotEmpty()) return

    val resources = listOf(
      PastPaperResourceEntity(
        id = "2024_math_p1_lq",
        year = "2024",
        subject = "math",
        paperType = "lq",
        title = "2024 HKDSE Mathematics Compulsory Part Paper 1 (LQ)",
        titleChinese = "2024 HKDSE 數學必修部分 卷一 (問答題)",
        fileSize = "3.2 MB",
        downloadCount = 12450,
        syllabusKeypoints = "多項式、等比數列求和及應用、三維空間幾何、圓方程、坐報與二階平移等高頻大題全解。"
      ),
      PastPaperResourceEntity(
        id = "2024_math_p2_mc",
        year = "2024",
        subject = "math",
        paperType = "mc",
        title = "2024 HKDSE Mathematics Compulsory Part Paper 2 (MC)",
        titleChinese = "2024 HKDSE 數學必修部分 卷二 (選擇題)",
        fileSize = "1.8 MB",
        downloadCount = 14890,
        syllabusKeypoints = "指數與對數圖形截距、十六進制運算、聯立不等式區域限界、三角比性質與條件機率。"
      ),
      PastPaperResourceEntity(
        id = "2025_phys_p1_all",
        year = "2025",
        subject = "physics",
        paperType = "lq",
        title = "2025 HKDSE Physics Paper 1 (MC & LQ)",
        titleChinese = "2025 HKDSE 物理科 卷一 (選擇題與問答題)",
        fileSize = "4.5 MB",
        downloadCount = 8240,
        syllabusKeypoints = "力與運動、氣體動力學、熱傳導、波動與反射折射率、電磁感應楞次定律、放射性半衰期。"
      ),
      PastPaperResourceEntity(
        id = "2025_chem_p1_all",
        year = "2025",
        subject = "chemistry",
        paperType = "lq",
        title = "2025 HKDSE Chemistry Paper 1 (MC & LQ)",
        titleChinese = "2025 HKDSE 化學科 卷一 (選擇題與問答題)",
        fileSize = "4.1 MB",
        downloadCount = 7930,
        syllabusKeypoints = "微觀世界與化學鍵、金屬與酸鹼度、電化學電池及電解、碳化合物命名、化學反應速率。"
      ),
      PastPaperResourceEntity(
        id = "2023_eng_p1_read",
        year = "2023",
        subject = "english",
        paperType = "lq",
        title = "2023 HKDSE English Language Paper 1 (Reading)",
        titleChinese = "2023 HKDSE 英國語文 卷一 (閱讀理解資訊)",
        fileSize = "2.3 MB",
        downloadCount = 11200,
        syllabusKeypoints = "Part A Core theme, Part B1 & B2 advanced synonyms, inference cues, cohesion and text structures."
      ),
      PastPaperResourceEntity(
        id = "2023_eng_p2_write",
        year = "2023",
        subject = "english",
        paperType = "lq",
        title = "2023 HKDSE English Language Paper 2 (Writing)",
        titleChinese = "2023 HKDSE 英國語文 卷二 (寫作能力)",
        fileSize = "1.2 MB",
        downloadCount = 10560,
        syllabusKeypoints = "Professional sentence variety, grammatical accuracy, cohesion connectives, content-rich formatting."
      ),
      PastPaperResourceEntity(
        id = "2022_chin_p1_read",
        year = "2022",
        subject = "chinese",
        paperType = "lq",
        title = "2022 HKDSE Chinese Language Paper 1 (Reading)",
        titleChinese = "2022 HKDSE 中國語文 卷一 (閱讀理解)",
        fileSize = "2.8 MB",
        downloadCount = 9800,
        syllabusKeypoints = "白話散文象徵意蘊、文言文詞語對譯、實詞推斷與句式結構、課外思想對比分析。"
      ),
      PastPaperResourceEntity(
        id = "2022_chin_p2_write",
        year = "2022",
        subject = "chinese",
        paperType = "lq",
        title = "2022 HKDSE Chinese Language Paper 2 (Writing)",
        titleChinese = "2022 HKDSE 中國語文 卷二 (寫作能力)",
        fileSize = "1.1 MB",
        downloadCount = 9310,
        syllabusKeypoints = "論說文三要素結構、記敍抒情文情感線索與物象鋪墊，關鍵立意與深刻思考表達。"
      ),
      PastPaperResourceEntity(
        id = "2021_bio_p1_all",
        year = "2021",
        subject = "biology",
        paperType = "lq",
        title = "2021 HKDSE Biology Paper 1 (MC & LQ)",
        titleChinese = "2021 HKDSE 生物科 卷一 (選擇題與問答題)",
        fileSize = "3.9 MB",
        downloadCount = 6540,
        syllabusKeypoints = "生命化學分子、遺傳與DNA密碼、人類生理學、光合與細胞呼吸、生態系統及群落結構。"
      ),
      PastPaperResourceEntity(
        id = "2020_math_p1_lq",
        year = "2020",
        subject = "math",
        paperType = "lq",
        title = "2020 HKDSE Mathematics Compulsory Part Paper 1 (LQ)",
        titleChinese = "2020 HKDSE 數學必修部分 卷一 (問答題)",
        fileSize = "3.0 MB",
        downloadCount = 11050,
        syllabusKeypoints = "二次方程頂點平移、等差等比數列複合求和、幾何立體折疊難題、直綫截距與圓切點軌跡。"
      ),
      PastPaperResourceEntity(
        id = "2020_math_p2_mc",
        year = "2020",
        subject = "math",
        paperType = "mc",
        title = "2020 HKDSE Mathematics Compulsory Part Paper 2 (MC)",
        titleChinese = "2020 HKDSE 數學必修部分 卷二 (選擇題)",
        fileSize = "1.7 MB",
        downloadCount = 11980,
        syllabusKeypoints = "指數與對數坐標方程、複數性質運算、圓圖幾何性質、三角形心特性與條件概率計算。"
      ),
      PastPaperResourceEntity(
        id = "2019_chem_p1_all",
        year = "2019",
        subject = "chemistry",
        paperType = "lq",
        title = "2019 HKDSE Chemistry Paper 1 (MC & LQ)",
        titleChinese = "2019 HKDSE 化學科 卷一 (選擇題與問答題)",
        fileSize = "3.8 MB",
        downloadCount = 7120,
        syllabusKeypoints = "酸鹼中和滴定、限量反應物探討、化學反應平衡Kc、工業硝酸製程及速率平衡探討。"
      ),
      PastPaperResourceEntity(
        id = "2018_phys_p1_all",
        year = "2018",
        subject = "physics",
        paperType = "lq",
        title = "2018 HKDSE Physics Paper 1 (MC & LQ)",
        titleChinese = "2018 HKDSE 物理科 卷一 (選擇題與問答題)",
        fileSize = "4.0 MB",
        downloadCount = 6880,
        syllabusKeypoints = "萬有引力、軌道力學軌跡、牛頓運動定律、摩擦力作功、熱容量混合法及透鏡成像性質。"
      ),
      PastPaperResourceEntity(
        id = "2017_eng_p3_listening",
        year = "2017",
        subject = "english",
        paperType = "listening",
        title = "2017 HKDSE English Language Paper 3 (Listening)",
        titleChinese = "2017 HKDSE 英國語文 卷三 (聆聽及綜合能力)",
        fileSize = "5.2 MB",
        downloadCount = 12500,
        syllabusKeypoints = "Shorthand techniques, summary writing, email register conversion, formal letter structured formatting."
      ),
      PastPaperResourceEntity(
        id = "2016_math_p2_mc",
        year = "2016",
        subject = "math",
        paperType = "mc",
        title = "2016 HKDSE Mathematics Compulsory Part Paper 2 (MC)",
        titleChinese = "2016 HKDSE 數學必修部分 卷二 (選擇題)",
        fileSize = "1.6 MB",
        downloadCount = 10400,
        syllabusKeypoints = "多項式餘數定理、因式分解配線、二次圖形變化、三角函數極值、立體截面面積計量。"
      ),
      PastPaperResourceEntity(
        id = "2012_math_p1_lq",
        year = "2012",
        subject = "math",
        paperType = "lq",
        title = "2012 HKDSE Mathematics Compulsory Part Paper 1 (LQ)",
        titleChinese = "2012 HKDSE 數學必修部分 卷一 (問答題)",
        fileSize = "3.1 MB",
        downloadCount = 13200,
        syllabusKeypoints = "首屆DSE元祖。聯立方程式、二元不等式平面、圓方程及圓心垂直距離切線、餘數定理全解。"
      )
    )

    database.dseDao().insertPastPaperResources(resources)
  }
}

data class GradeForecast(
  val currentGrade: String,
  val nextGrade: String,
  val diffText: String,
  val progressFraction: Float,
  val scorePercentage: Int
)

data class StudyGroupUser(
  val id: String,
  val name: String,
  val status: String,
  val isOnline: Boolean,
  val focusedMinutesToday: Int,
  val schoolTag: String
)
