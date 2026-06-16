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
    if (!existing.isNullOrEmpty()) return

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
      )
    )

    database.dseDao().insertQuestions(list)
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
