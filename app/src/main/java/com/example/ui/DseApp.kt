package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.BadgeEntity
import com.example.database.MistakeEntity
import com.example.database.QuestionEntity
import com.example.viewmodel.DseViewModel
import com.example.viewmodel.GradeForecast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// --- Screens enum ---
enum class DseScreen(val title: String, val icon: ImageVector) {
  DASHBOARD("學習進度", Icons.Default.Dashboard),
  BANK("歷屆題庫", Icons.Default.AutoStories),
  PRACTICE("學科挑戰", Icons.Default.BarChart),
  MISTAKES("錯題本", Icons.Default.BookmarkBorder),
  REVISION("考試指南", Icons.Default.LibraryBooks),
  LEADERBOARD("排行榜", Icons.Default.Leaderboard)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DseMainApp(viewModel: DseViewModel) {
  var currentTab by remember { mutableStateOf(DseScreen.DASHBOARD) }

  // State flows
  val progress by viewModel.userProgress.collectAsStateWithLifecycle()
  val mistakes by viewModel.allMistakes.collectAsStateWithLifecycle()
  val badges by viewModel.allBadges.collectAsStateWithLifecycle()
  val activeSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()
  val completedQuestions by viewModel.completedQuestions.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.TrendingUp,
              contentDescription = "Logo",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              "DSE Level Up",
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.SansSerif,
              letterSpacing = 0.5.sp
            )
          }
        },
        actions = {
          Row(
            modifier = Modifier
              .padding(end = 12.dp)
              .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
              )
              .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.LocalFireDepartment,
              contentDescription = "Streak",
              tint = Color(0xFFFF5722),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              "${progress?.dailyStreak ?: 1} 日",
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              fontSize = 14.sp
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
      ) {
        DseScreen.values().forEach { tab ->
          NavigationBarItem(
            selected = currentTab == tab,
            onClick = { currentTab = tab },
            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
            label = { Text(tab.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
          )
        }
      }
    },
    contentWindowInsets = WindowInsets.safeDrawing
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background)
    ) {
      AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
          fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "TabTransition"
      ) { targetScreen ->
        when (targetScreen) {
          DseScreen.DASHBOARD -> DashboardScreen(
            viewModel = viewModel,
            progress = progress ?: com.example.database.UserProgressEntity(),
            badges = badges,
            completedList = completedQuestions,
            onStartPractice = { subject ->
              viewModel.changeSubject(subject)
              currentTab = DseScreen.PRACTICE
            },
            onNavigateToTab = { currentTab = it },
            getGradeForecast = { viewModel.getPredictedGradeAndCutoff(it) }
          )
          DseScreen.BANK -> BankScreen(viewModel = viewModel)
          DseScreen.PRACTICE -> PracticeScreen(viewModel = viewModel)
          DseScreen.MISTAKES -> MistakesScreen(viewModel = viewModel, mistakes = mistakes, onGoToChallenge = {
            viewModel.changeSubject(it)
            currentTab = DseScreen.PRACTICE
          })
          DseScreen.REVISION -> RevisionScreen()
          DseScreen.LEADERBOARD -> LeaderboardScreen(userPoints = progress?.scorePoints ?: 120)
        }
      }
    }
  }
}

// --- SCREEN 1: DASHBOARD ---
@Composable
fun DashboardScreen(
  viewModel: DseViewModel,
  progress: com.example.database.UserProgressEntity,
  badges: List<BadgeEntity>,
  completedList: List<com.example.database.CompletedQuestionEntity>,
  onStartPractice: (String) -> Unit,
  onNavigateToTab: (DseScreen) -> Unit,
  getGradeForecast: (String) -> GradeForecast
) {
  var showCreatorStory by remember { mutableStateOf(false) }
  var showEducatorLab by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {

    // Headline with candidate accent
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "「我分析了 10 年 DSE Math，發現每年只考 12 個核心邏輯。」",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            lineHeight = 24.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            "12 個底層思維框架。掌握邏輯，拒絕死記硬背！",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .background(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
              )
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Campaign,
              contentDescription = "Trust Campaign",
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              "由 2026 DSE 考生成立 | 永久免費 | 無廣告",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }
      }
    }

    // Daily tasks recommendation & Streak
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Points Box
        Card(
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
          )
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text("多巴胺積分", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "${progress.scorePoints} XP",
              fontSize = 24.sp,
              fontWeight = FontWeight.Black,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }

        // Questions Answered Box
        Card(
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
          )
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text("已答對題目數", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "${progress.totalCorrectAnswers} / ${progress.totalQuestionsJoined}",
              fontSize = 22.sp,
              fontWeight = FontWeight.Black,
              color = MaterialTheme.colorScheme.onTertiaryContainer
            )
          }
        }
      }
    }

    // 1. Weekly efficiency and timer trends chart
    item {
      StudyTimerAndPointsChart(completedList = completedList)
    }

    // 2. Intelligent subject weakness diagnose section
    item {
      SubjectsWeaknessesDiagnose(
        completedList = completedList,
        onStartPractice = onStartPractice,
        getGradeForecast = getGradeForecast
      )
    }

    // Auxiliary navigation shortcuts
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Card(
          modifier = Modifier.weight(1f).clickable { showCreatorStory = true },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(imageVector = Icons.Default.Portrait, contentDescription = "Story", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("👋 2026 自修生自白", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }

        Card(
          modifier = Modifier.weight(1f).clickable { showEducatorLab = true },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = "Lab", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("🛠️ 出題實驗室", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Badge showcases (Dopamine Awards)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "已解鎖多巴胺徽章",
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          "解鎖進度 ${badges.size} / 5",
          fontSize = 12.sp,
          color = Color.Gray
        )
      }
    }

    if (badges.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Empty",
              tint = Color.Gray,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("暫未解鎖任何徽章。快去答對一條題目吧！", fontSize = 12.sp, color = Color.Gray)
          }
        }
      }
    } else {
      item {
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(badges) { badge ->
            Card(
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
              )
            ) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFF9800), shape = CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = when(badge.iconName) {
                      "local_fire_department" -> Icons.Default.LocalFireDepartment
                      "stars" -> Icons.Default.Stars
                      "psychology" -> Icons.Default.Psychology
                      "trending_up" -> Icons.Default.TrendingUp
                      "grid_view" -> Icons.Default.GridView
                      else -> Icons.Default.Analytics
                    },
                    contentDescription = "Badge Icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Text(badge.description, fontSize = 9.sp, lineHeight = 11.sp, color = Color.DarkGray)
                }
              }
            }
          }
        }
      }
    }

    // Footnote with disclaimer
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            "⚖️ 平台免責聲明與法律合規",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            "本平台提供之試題均為獨立改寫版本，旨在協助考生練習應試技巧，並非香港考試及評核局（HKEAA）官方試題。官方歷次試題請前往 HKEAA 官方網站下載。等級預測係按歷史Cut-off進行線性插值計算之指標，僅供參考，最終成績以 HKEAA 官方公布為準。",
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
        }
      }
    }
  }

  // Creator Story Dialog
  if (showCreatorStory) {
    Dialog(onDismissRequest = { showCreatorStory = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("👋 做平台嘅初衷：自修生戰白", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { showCreatorStory = false }) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            item {
              Text(
                "我是 2026 文憑試的應屆考生。在漫長的 Study Leave 期間，相信大家都曾依賴 DSE.life 搵返昔日的歷屆試題。當初平台突然因無奈下架，大家那種頓失支柱、手足無措的感覺，我深有同感。\n\n" +
                  "當時我就在想，與其盲目去死記背誦每一道歷屆題目（甚至多數人重做時只是記住了標準答案，卻沒有get到背後的原理），我們為甚麼不能做一個真正的「底層思維練習系統」？\n\n" +
                  "這，就是 DSE Level Up 創立的初衷。我們用 AI 改寫了歷屆考試所有主要題型——修改了情境、數字與背景，但100%保留了考評局官方最愛的考評陷阱與邏輯考點。這樣，大家就擁有了一個完全合規、不用擔心侵權、又可以真正驗證自己是否學會了『底層邏輯』的操卷神器！\n\n" +
                  "📧 合作及聯絡電郵：brianarebrian@gmail.com\n" +
                  "📸 官方 Instagram：@dse_level_up_hk",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Color.Black
              )
            }
          }
        }
      }
    }
  }

  // Educator Lab Dialog
  if (showEducatorLab) {
    Dialog(onDismissRequest = { showEducatorLab = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("🛠️ 獨立出題實驗室", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { showEducatorLab = false }) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          Box(modifier = Modifier.weight(1f)) {
            AdminScreen(viewModel = viewModel)
          }
        }
      }
    }
  }
}

// --- NEW COMPONENT: TIMER AND CHART TRENDS ---
@Composable
fun StudyTimerAndPointsChart(completedList: List<com.example.database.CompletedQuestionEntity>) {
  val lastExercises = completedList.sortedByDescending { it.timestamp }.take(8).reversed()

  if (lastExercises.isEmpty()) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
      Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text("✨ 開始完成挑戰，此處將自動生成您的練習耗時與得分趨勢圖表！", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
      }
    }
    return
  }

  Card(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("📈 學習效率與用時趨勢 (最近 8 次)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
      Text("藍色反映答題時長 (秒)，彩色柱條反映獲得積分 (XP)。實施高強度考場控制！", fontSize = 11.sp, color = Color.Gray)
      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        lastExercises.forEachIndexed { idx, item ->
          val timeSec = item.timeSpentSeconds.coerceIn(1, 180)
          val score = item.scoreEarned.coerceIn(0, 100)

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
          ) {
            Text("${score}xp", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(2.dp))

            Box(
              modifier = Modifier
                .width(12.dp)
                .height((score * 0.8f).dp.coerceIn(8.dp, 60.dp))
                .background(
                  brush = Brush.verticalGradient(
                    colors = if (item.isCorrect) {
                      listOf(Color(0xFF81C784), Color(0xFF4CAF50))
                    } else {
                      listOf(Color(0xFFE57373), Color(0xFFF44336))
                    }
                  ),
                  shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                )
            )

            Spacer(modifier = Modifier.height(3.dp))

            Box(
              modifier = Modifier
                .width(12.dp)
                .height((timeSec * 0.3f).dp.coerceIn(4.dp, 40.dp))
                .background(Color(0xFF2196F3).copy(alpha = 0.8f), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text("${timeSec}秒", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(modifier = Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text("答對 XP", fontSize = 9.sp, color = Color.DarkGray)

        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.size(6.dp).background(Color(0xFFF44336), CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text("重試 XP", fontSize = 9.sp, color = Color.DarkGray)

        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.size(6.dp).background(Color(0xFF2196F3).copy(alpha = 0.8f), CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text("作答用時 (秒)", fontSize = 9.sp, color = Color.DarkGray)
      }
    }
  }
}

// --- NEW COMPONENT: SUBJECT GRAPH AND WEAKPOINT DIAGNOSIS ---
@Composable
fun SubjectsWeaknessesDiagnose(
  completedList: List<com.example.database.CompletedQuestionEntity>,
  onStartPractice: (String) -> Unit,
  getGradeForecast: (String) -> GradeForecast
) {
  val subjects = listOf(
    "math" to "📐 數學科 (必修科)",
    "physics" to "⚡ 物理科 (選修科)",
    "chemistry" to "🧪 化學科 (選修科)",
    "english" to "🇬🇧 英文科 (核心科)"
  )

  var weakestSubject: String? = null
  var weakestCorrectPercentage = 101
  var strongestSubject: String? = null
  var strongestCorrectPercentage = -1

  val summaryList = subjects.map { (subKeyword, title) ->
    val subList = completedList.filter { it.subject == subKeyword }
    val total = subList.size
    val correct = subList.count { it.isCorrect }
    val percentage = if (total > 0) (correct.toDouble() / total * 100).toInt() else 0

    if (total > 0) {
      if (percentage < weakestCorrectPercentage) {
        weakestCorrectPercentage = percentage
        weakestSubject = subKeyword
      }
      if (percentage > strongestCorrectPercentage) {
        strongestCorrectPercentage = percentage
        strongestSubject = subKeyword
      }
    }
    Triple(subKeyword, title, percentage)
  }

  Card(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("📊 不同科目強弱環節診斷 (Weakpoint Analysis)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
      Text("智能提煉您在文憑試學門的底層邏輯缺陷與優勢區", fontSize = 11.sp, color = Color.Gray)
      Spacer(modifier = Modifier.height(12.dp))

      if (weakestSubject != null) {
        val weakestTitle = subjects.firstOrNull { it.first == weakestSubject }?.second ?: weakestSubject
        val strongestTitle = subjects.firstOrNull { it.first == strongestSubject }?.second ?: strongestSubject

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3CD), RoundedCornerShape(8.dp))
            .padding(12.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = "Warn", tint = Color(0xFFD39E00), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("🚨 薄弱環節診斷與戰略指引：", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF856404))
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            "檢測到您在 ${weakestTitle} 中的答對率為 ${weakestCorrectPercentage}%，此為當前攻星主要瓶頸！考評局極易利用該科對抗思維（例如微積分或代數群組平移）使同學失分，建議立即加強學科練習！",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Color(0xFF856404)
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (strongestSubject != weakestSubject) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFFD4EDDA), RoundedCornerShape(8.dp))
              .padding(12.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Strong", tint = Color(0xFF28A745), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("👑 星級學科優勢：", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF155724))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "恭喜！您在 ${strongestTitle} 答對率已臻 ${strongestCorrectPercentage}%，正在穩步向 Level 5* / 5** 進發，繼續保持優勢！",
              fontSize = 11.sp,
              lineHeight = 15.sp,
              color = Color(0xFF155724)
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8F0FE), RoundedCornerShape(8.dp))
            .padding(12.dp)
        ) {
          Text("💡 學前診斷指引：", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1A73E8))
          Text("當前暫無充足的答題記錄。請前往「學科挑戰」答題，完卷後我們將即時激活 AI 大數據診斷，洞察科目攻關缺陷！", fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFF1A73E8))
        }
        Spacer(modifier = Modifier.height(12.dp))
      }

      // Progress indicators
      summaryList.forEach { (subKeyword, title, percentage) ->
        val forecast = getGradeForecast(subKeyword)
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("答對率: ${percentage}%  |  預估級位: ", fontSize = 10.sp, color = Color.Gray)
              Text(forecast.currentGrade, fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = percentage / 100f,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (percentage < 50) Color(0xFFDC3545) else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
          )
        }
      }
    }
  }
}

// --- SCREEN 2: NEW DSE QUESTION BANK (題庫分類瀏覽與詳析) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(viewModel: DseViewModel) {
  val questions by viewModel.allQuestions.collectAsStateWithLifecycle()
  var searchKeyword by remember { mutableStateOf("") }
  var selectedSubjectFilter by remember { mutableStateOf("all") }
  var selectedYearFilter by remember { mutableStateOf("all") }

  // Collapsible Methodology dialog control
  var showMethodologyDialog by remember { mutableStateOf(false) }

  val subjectsList = listOf(
    "all" to "🌐 所有科目",
    "math" to "📐 數學科",
    "physics" to "⚡ 物理科",
    "chemistry" to "🧪 化學科",
    "english" to "🇬🇧 英文科"
  )

  val yearsList = listOf(
    "all" to "📅 所有考情",
    "2023" to "2023 歷屆改寫",
    "2020" to "2020 歷屆改寫",
    "2019" to "2019 歷屆改寫",
    "2018" to "2018 歷屆改寫",
    "mock" to "📝 模擬/自定義題"
  )

  // Filtering Logic
  val filteredQuestions = questions.filter { q ->
    val matchSubject = selectedSubjectFilter == "all" || q.subject == selectedSubjectFilter
    val matchYear = when (selectedYearFilter) {
      "all" -> true
      "mock" -> q.originalRef.contains("模擬") || q.id.startsWith("custom_") || !q.originalRef.any { it.isDigit() }
      else -> q.originalRef.contains(selectedYearFilter)
    }
    val matchKeyword = searchKeyword.isEmpty() ||
        q.questionText.contains(searchKeyword, ignoreCase = true) ||
        q.topicChinese.contains(searchKeyword, ignoreCase = true) ||
        q.originalRef.contains(searchKeyword, ignoreCase = true)

    matchSubject && matchYear && matchKeyword
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      "📚 DSE 考評局改寫與模擬題庫",
      fontWeight = FontWeight.Black,
      fontSize = 20.sp,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      "提供完美的題目答案解析與詳細解題步驟（Step Notes）。按學科與年份精緻分類，助同學徹底攻略邏輯！",
      fontSize = 12.sp,
      color = Color.Gray
    )

    // Expandable Methodology Guideline shortcut
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      modifier = Modifier
        .fillMaxWidth()
        .clickable { showMethodologyDialog = true }
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(imageVector = Icons.Default.Psychology, contentDescription = "Learn", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("🧠 深入了解：HKDSE 4 大必修對抗思維方法論", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.primary)
      }
    }

    // Search bar
    OutlinedTextField(
      value = searchKeyword,
      onValueChange = { searchKeyword = it },
      placeholder = { Text("搜尋題文、課題、考點或出處...") },
      modifier = Modifier.fillMaxWidth(),
      leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
      shape = RoundedCornerShape(12.dp)
    )

    // Filters row 1: Subjects
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(subjectsList) { (key, name) ->
        FilterChip(
          selected = selectedSubjectFilter == key,
          onClick = { selectedSubjectFilter = key },
          label = { Text(name, fontSize = 11.sp) }
        )
      }
    }

    // Filters row 2: Years
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(yearsList) { (key, name) ->
        FilterChip(
          selected = selectedYearFilter == key,
          onClick = { selectedYearFilter = key },
          label = { Text(name, fontSize = 11.sp) }
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
      "篩選結果：共計 ${filteredQuestions.size} 條改寫/模擬題符合條件：",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = Color.Gray
    )

    if (filteredQuestions.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text("沒有匹配的題目，請嘗試放寬篩選 🔍", fontSize = 12.sp, color = Color.Gray)
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f).fillMaxWidth()
      ) {
        items(filteredQuestions) { question ->
          var expanded by remember { mutableStateOf(false) }

          Card(
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = question.topicChinese,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = "對照考評: ${question.originalRef} | 難度: ${question.difficulty}",
                    fontSize = 10.sp,
                    color = Color.Gray
                  )
                }
                IconButton(onClick = { expanded = !expanded }) {
                  Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand"
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = question.questionText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = Color.Black
              )

              if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                Text("📝 題目選項：", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                val choices = listOf(
                  "A" to question.optionA,
                  "B" to question.optionB,
                  "C" to question.optionC,
                  "D" to question.optionD
                )
                choices.forEach { (key, text) ->
                  val isCorrect = key == question.correctAnswer
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 3.dp)
                      .background(if (isCorrect) Color(0xFFE8F5E9) else Color.Transparent, RoundedCornerShape(4.dp))
                      .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "選項 $key: $text",
                      fontSize = 12.sp,
                      color = if (isCorrect) Color(0xFF2E7D32) else Color.Black,
                      fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detail steps box
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                ) {
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Correct", tint = Color(0xFF28A745), modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("🎯 正確答案為: [ ${question.correctAnswer} ]", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF2E7D32))
                    }
                    if (question.methodologyType != "General") {
                      Spacer(modifier = Modifier.height(4.dp))
                      Text("🧠 底層邏輯：${question.methodologyType}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("📋 詳細解題步驟 (Step Notes)：", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    
                    val stepsList = question.stepNotes.split("\n")
                    stepsList.forEach { step ->
                      if (step.trim().isNotEmpty()) {
                        Text(
                          text = step,
                          fontSize = 11.sp,
                          lineHeight = 15.sp,
                          color = Color.Black,
                          modifier = Modifier.padding(vertical = 2.dp)
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("💡 答案解析 (Detailed Solution)：", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    Text(
                      text = question.explanationDetailed,
                      fontSize = 11.sp,
                      lineHeight = 15.sp,
                      color = Color.DarkGray
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Methodology Dialog
  if (showMethodologyDialog) {
    Dialog(onDismissRequest = { showMethodologyDialog = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("🧠 HKDSE 4 大底層對抗思維方法論", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { showMethodologyDialog = false }) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            val methodologyFrameworks = listOf(
              MethodologyItem(
                title = "1. 轉化思維 (Transformation Thinking)",
                desc = "考評局極喜歡考不真正求出具體未知數，而是提取公共變項、利用根與係數公式做群組整體代入的能力。",
                official = "2023 數學 Paper 1 Q1: 解 \$2x^2 + 3x - 5 = 0\$",
                rewritten = "改寫本: 解 \$3x^2 + 5x - 2 = 0\$ 的根，並直接推求配合二次對稱的群組關係乘積模板。核心轉換公式均為根與係數代入（考點高質量對抗）。"
              ),
              MethodologyItem(
                title = "2. 變化率直覺 (Rate of Change)",
                desc = "高等題中針對三次函數或斜率等比，考查學生在拐點（Points of Inflection）或連續導數臨界值間的空間直覺。",
                official = "2023 Q3: \$f(x)=x^3-3x^2+4\$ 求解極大值與極小值。",
                rewritten = "改寫本: 已知 \$f(x)=x^3-3x^2+4\$ ，若整體向下平移 2 單位，其新的二階拐點 \$f''(x)=0\$ 的位置坐標在哪裏？這一步完美對接了同樣的導數解析轉換，比死記公式更能培養知覺。"
              ),
              MethodologyItem(
                title = "3. 條件拆解 (Condition Decomposition)",
                desc = "一條解析幾何或外心坐標通常包裹了三層簡單公式。文憑試考的不是難度，而是如何把復合條件逐步剝離。",
                official = "2020 DSE Math Q32: 複合直線幾何面積及斜率互補計算。",
                rewritten = "改寫本: 垂直直線 L1(4x-3y+C=0) 與 Slope = -3/4 重合且與平面軸線圍成 6 單位三角形，這需要拆分成第一垂直條件、第二常數截距表達、第三二次方程截距解答三步（將題形條件原汁原味繼承並改造案例）。"
              ),
              MethodologyItem(
                title = "4. 建模抽象能力 (Modeling Ability)",
                desc = "把荷葉繁殖、高楼租金、原子衰變等現實字眼，剥離噪音，正確翻譯成等比數列為首的數學等式。",
                official = "2019 DSE Math Sequence word problems (等差/等比應用題)。",
                rewritten = "改寫本: 荷葉繁殖面積等比級數遞增。在第 3 天為 18，第 6 天為 144，第 8 天預估為多少？其底層等比模型 \$T_n = a r^{n-1}\$ 在所有題型不變，僅僅將背景換成極具想像力的自然繁殖，助同學舉一反三！"
              )
            )

            items(methodologyFrameworks) { item ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(item.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(item.desc, fontSize = 11.sp, lineHeight = 15.sp, color = Color.Black)
                  Spacer(modifier = Modifier.height(6.dp))
                  Text("📄 考評原型: ${item.official}", fontSize = 10.sp, color = Color.Gray)
                  Text("🚀 Level Up 改寫方案: ${item.rewritten}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                }
              }
            }
          }
        }
      }
    }
  }
}

data class MethodologyItem(
  val title: String,
  val desc: String,
  val official: String,
  val rewritten: String
)

// --- SCREEN 3: PRACTICE CHALLENGE_PAGE ---
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PracticeScreen(viewModel: DseViewModel) {
  val selectedSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()
  val activeQuestions by viewModel.activeQuestions.collectAsStateWithLifecycle()
  val currentQuestionIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()
  val currentQ by viewModel.currentQuestion.collectAsStateWithLifecycle()

  // Score stats and attempts
  val progress by viewModel.userProgress.collectAsStateWithLifecycle()

  // State control for current question answering details
  var selectedChoice by remember { mutableStateOf<String?>(null) }
  var answerVerified by remember { mutableStateOf(false) }
  var showMistakeDialog by remember { mutableStateOf(false) }
  var showCorrectVisualConfetti by remember { mutableStateOf(false) }
  var elapsedSeconds by remember { mutableStateOf(0) }
  val scope = rememberCoroutineScope()

  // Timer stopwatch logic
  LaunchedEffect(currentQ, answerVerified) {
    if (currentQ != null && !answerVerified) {
      elapsedSeconds = 0
      while (true) {
        kotlinx.coroutines.delay(1000)
        elapsedSeconds += 1
      }
    }
  }

  // API tutor analysis results
  val aiExplanation by viewModel.aiTutorExplanation.collectAsStateWithLifecycle()
  val aiLoading by viewModel.aiTutorLoading.collectAsStateWithLifecycle()

  // Subject chooser list
  val subjectsList = listOf(
    "math" to "📐 數學",
    "physics" to "⚡ 物理",
    "chemistry" to "🧪 化學",
    "english" to "🇬🇧 英文"
  )

  // Auto clean states whenever the question OR subject changes
  LaunchedEffect(currentQ) {
    selectedChoice = null
    answerVerified = false
    showCorrectVisualConfetti = false
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    // 1. Selector segment horizontal bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      subjectsList.forEach { (subWord, subName) ->
        val active = selectedSubject == subWord
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
          ),
          modifier = Modifier
            .weight(1f)
            .clickable {
              viewModel.changeSubject(subWord)
            }
        ) {
          Box(
            modifier = Modifier
              .padding(vertical = 10.dp)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              subName,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    // Checking if all questions of chosen subject are completed!
    if (currentQ == null) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp)
          .shadow(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .background(Color(0xFF4CAF50), shape = CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(36.dp))
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text("本階段所有改寫題目均已圓滿解答！", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
          Spacer(modifier = Modifier.height(8.dp))
          Text("【底層邏輯大師】恭喜你完成 session！我們已經在您的後台更新了文憑試 Cut-off 水平，目前的等級對應預測為：${viewModel.getPredictedGradeAndCutoff(selectedSubject).currentGrade}", textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
          Spacer(modifier = Modifier.height(18.dp))
          Button(onClick = { viewModel.nextQuestion() }, modifier = Modifier.testTag("reset_questions_btn")) {
            Text("重新演練本學科")
          }
        }
      }
    } else {
      val question = currentQ!!

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Topic badges
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                "改寫題號：#${question.id.uppercase()}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                "⏱️ $elapsedSeconds 秒",
                fontSize = 12.sp,
                color = if (elapsedSeconds > 100) Color(0xFFDC3545) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              val isMethod = question.methodologyType != "General"
              if (isMethod) {
                Box(
                  modifier = Modifier
                    .background(Color(0xFFFF9800), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                  Text("🔐 底層邏輯: ${question.methodologyType}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
              }
              Box(
                modifier = Modifier
                  .background(
                    color = when(question.difficulty) {
                      "Easy" -> Color(0xFF4CAF50)
                      "Medium" -> Color(0xFFFFC107)
                      else -> Color(0xFFF44336)
                    },
                    shape = RoundedCornerShape(12.dp)
                  )
                  .padding(horizontal = 10.dp, vertical = 3.dp)
              ) {
                Text(question.difficulty, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // The question statement container
        item {
          Card(
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                question.topicChinese,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                question.questionText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                "出處對照: ${question.originalRef}",
                fontSize = 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Light
              )
            }
          }
        }

        // The four MCQ choices options list with ripple Feedback
        val choices = listOf(
          "A" to question.optionA,
          "B" to question.optionB,
          "C" to question.optionC,
          "D" to question.optionD
        )

        items(choices) { (key, optionText) ->
          val isSelected = selectedChoice == key
          val correctKey = question.correctAnswer
          val isCorrect = key == correctKey

          val containerColor = when {
            answerVerified && isSelected && isCorrect -> Color(0xFFD4EDDA) // Highlight success
            answerVerified && isSelected && !isCorrect -> Color(0xFFF8D7DA) // Highlight fail
            answerVerified && !isSelected && isCorrect -> Color(0xFFD4EDDA) // Guide correct
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
          }

          val borderStrokeColor = when {
            answerVerified && isSelected && isCorrect -> Color(0xFF28A745)
            answerVerified && isSelected && !isCorrect -> Color(0xFFDC3545)
            isSelected -> MaterialTheme.colorScheme.primary
            else -> Color.LightGray.copy(alpha = 0.5f)
          }

          Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier
              .fillMaxWidth()
              .shadow(2.dp, RoundedCornerShape(8.dp))
              .clickable(enabled = !answerVerified) {
                selectedChoice = key
              }
              .testTag("option_${key.lowercase()}")
          ) {
            Row(
              modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                    shape = CircleShape
                  ),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  key,
                  fontWeight = FontWeight.Black,
                  fontSize = 13.sp,
                  color = if (isSelected) Color.White else Color.Black
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                optionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
              )
            }
          }
        }

        // Verification Check Buttons
        item {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                if (selectedChoice == null) return@Button
                val verdict = selectedChoice == question.correctAnswer
                answerVerified = true
                if (verdict) {
                  showCorrectVisualConfetti = true
                  viewModel.recordAnswer(question.id, true, timeSpentSeconds = elapsedSeconds)
                } else {
                  showMistakeDialog = true
                  viewModel.recordAnswer(question.id, false, timeSpentSeconds = elapsedSeconds)
                }
              },
              enabled = selectedChoice != null && !answerVerified,
              modifier = Modifier
                .weight(1f)
                .testTag("submit_answer_btn")
            ) {
              Text("提交答案 (Instant Check)", fontWeight = FontWeight.Bold)
            }

            if (answerVerified) {
              Button(
                onClick = {
                  viewModel.nextQuestion()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier
                  .weight(1f)
                  .testTag("next_question_btn")
              ) {
                Text("下一挑戰題 🚀", fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // AI Tutor dynamic analysis launcher row
        if (answerVerified) {
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.PrecisionManufacturing,
                      contentDescription = "AI Mode",
                      tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🤖 AI 考試導師即時邏輯大合剖", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  "前考評閱卷大師在線！自動根據這題的底層思維骨架進行拆解，助你在真實 DSE 考卷中融會貫通！",
                  fontSize = 11.sp,
                  color = Color.DarkGray,
                  lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Button(
                    onClick = { viewModel.requestAiTutorAnalysis(question) },
                    enabled = !aiLoading,
                    modifier = Modifier.weight(1f)
                  ) {
                    Text(if (aiLoading) "正在剖析中..." else "發起 AI 星級導師解析")
                  }
                }
              }
            }
          }
        }

        // Render AI explanation bubble nicely
        if (aiExplanation != null) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
              modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
            ) {
              Column(
                modifier = Modifier
                  .padding(16.dp)
                  .fillMaxWidth()
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(bottom = 8.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(8.pngWidth)
                      .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("AI 導師解析回報如下：", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }

                Text(
                  aiExplanation ?: "",
                  fontSize = 12.sp,
                  lineHeight = 16.sp,
                  color = Color.Black
                )

                Spacer(modifier = Modifier.height(10.dp))

                // YouTube tutorial link support
                Row(
                  modifier = Modifier
                    .fillModifierWithLink()
                    .clickable { /* Simulate play video instruction */ },
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = "Youtube Tutorial",
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    "唔卡關！點擊播放 Herman Yeung / YY 相似課程重點解碼 📺",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // COPING DIALOG FOR WRONG ANSWERS: Mistake Tag categorization workbook 2.0
  if (showMistakeDialog) {
    Dialog(onDismissRequest = { showMistakeDialog = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "🔴 答錯了！請標註這題的「錯誤原因」",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFDC3545)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            "錯題本 2.0 自動記錄：文憑試考生只有直面自己的習慣缺陷，才能考取 5**！",
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = Color.Gray
          )
          Spacer(modifier = Modifier.height(16.dp))

          val reasons = listOf(
            "Carelessness" to "粗心大意 (概念會但手誤)",
            "Concept Gap" to "概念不清 (理解有盲點)",
            "Calculation Error" to "計算出錯 (公式套錯或算錯)",
            "Time Pressure" to "時間不足 (限時內未能解答)"
          )

          reasons.forEach { (tagUrl, display) ->
            Button(
              onClick = {
                currentQ?.let { viewModel.recordAnswer(it.id, false, tagUrl) }
                showMistakeDialog = false
              },
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant)
            ) {
              Text(display, color = Color.Black, fontSize = 12.sp)
            }
          }
        }
      }
    }
  }

  // DOPAMINE CONFETTI SIMULATOR for correct replies
  if (showCorrectVisualConfetti) {
    ConfettiAnimationOverlay {
      showCorrectVisualConfetti = false
    }
  }
}

private fun Modifier.fillModifierWithLink(): Modifier = this
  .fillMaxWidth()
  .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
  .padding(10.dp)

private val Int.pngWidth: androidx.compose.ui.unit.Dp
  get() = this.dp

// --- SCREEN 4: MISTAKES DIRECTORY (錯題本 2.0) ---
@Composable
fun MistakesScreen(
  viewModel: DseViewModel,
  mistakes: List<MistakeEntity>,
  onGoToChallenge: (String) -> Unit
) {
  val scope = rememberCoroutineScope()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      "🗃️ 錯題本 2.0 內控庫",
      fontSize = 20.sp,
      fontWeight = FontWeight.Black,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      "直面你錯得最多的學門課題，自動引導你再練習同類題！",
      fontSize = 12.sp,
      color = Color.Gray
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (mistakes.isEmpty()) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.Mood,
            contentDescription = "Perfect status",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text("暫無錯誤題目記錄！", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Text("所有的挑戰你都完美應對，繼續保持，衝擊 Level 5**！", textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(mistakes) { mistake ->
          Card(
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .background(Color(0xFFDC3545).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Text(
                    "答錯：${mistake.timesFailed} 次",
                    color = Color(0xFFDC3545),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                Box(
                  modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Text(
                    reasonDescription(mistake.reasonTag),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))
              Text(
                "學科: ${mistake.subject.uppercase()}  |  課題: ${mistake.topic}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "備忘記錄：${mistake.userNotes}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
              )

              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                OutlinedButton(
                  onClick = {
                    scope.launch { viewModel.deleteMistakeRecord(mistake.questionId) }
                  },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                  modifier = Modifier.padding(end = 6.dp)
                ) {
                  Text("我知道錯了 (刪除記錄)", fontSize = 11.sp)
                }

                Button(
                  onClick = { onGoToChallenge(mistake.subject) }
                ) {
                  Text("重做這門課 🚀", fontSize = 11.sp)
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun reasonDescription(tag: String): String = when(tag) {
  "Carelessness" -> "粗心大意 (概念會但手誤)"
  "Concept Gap" -> "概念不清 (理解有盲點)"
  "Calculation Error" -> "計算出錯 (公式套錯或算錯)"
  "Time Pressure" -> "時間不足 (限時內未能解答)"
  else -> "未指定盲點"
}

// --- SCREEN 5: LEADERBOARD SCREEN ---
@Composable
fun LeaderboardScreen(userPoints: Int) {
  val mockLeaderboard = listOf(
    LeaderboardLeader("1", "沙田區 DSE 神探", "Level 5**", 1320, true),
    LeaderboardLeader("2", "屯門拔尖狂魔", "Level 5**", 1190, false),
    LeaderboardLeader("3", "喇沙溫書戰士", "Level 5*", 980, false),
    LeaderboardLeader("4", "你 (今日最勤奮)", "Level 5", userPoints, false),
    LeaderboardLeader("5", "協恩自修公主", "Level 4", 620, false),
    LeaderboardLeader("6", "九龍塘狀元", "Level 4", 450, false)
  ).sortedByDescending { it.points }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      "👑 匿名榜單戰績",
      fontSize = 20.sp,
      fontWeight = FontWeight.Black,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      "同屆有 ${mockLeaderboard.size * 34 + 12} 位 2026 考生在這裏瘋狂操卷，你並不孤單！",
      fontSize = 12.sp,
      color = Color.Gray
    )
    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(mockLeaderboard) { leader ->
        val isSelf = leader.name.startsWith("你")
        val containerColor = if (isSelf) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surface
        }

        Card(
          colors = CardDefaults.cardColors(containerColor = containerColor),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .padding(16.dp)
              .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .background(
                    color = when (leader.rank) {
                      "1" -> Color(0xFFFFD700)
                      "2" -> Color(0xFFC0C0C0)
                      "3" -> Color(0xFFCD7F32)
                      else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape
                  ),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  leader.rank,
                  fontWeight = FontWeight.Black,
                  fontSize = 14.sp,
                  color = Color.White
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(leader.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("預估考位: ${leader.gradeTarget}", fontSize = 11.sp, color = Color.Gray)
              }
            }

            Text(
              "${leader.points} XP分",
              fontWeight = FontWeight.Black,
              fontSize = 16.sp,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }
    }
  }
}

data class LeaderboardLeader(
  val rank: String,
  val name: String,
  val gradeTarget: String,
  val points: Int,
  val specialBadge: Boolean
)

// --- SCREEN 5: REVISION SCREEN (最新資訊與應試指南) ---
@Composable
fun RevisionScreen() {
  var selectedSubTab by remember { mutableStateOf(0) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      "📣 HKDSE 最新考情與通關指南",
      fontWeight = FontWeight.Black,
      fontSize = 20.sp,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      "收錄考評局官方最新動態、核心考綱更新，並提煉頂尖狀元高分溫習法與時間分配術。",
      fontSize = 12.sp,
      color = Color.Gray
    )

    // Sub-tab selectors
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      val tabs = listOf("📅 考情發佈", "📚 狀元溫習術", "⏳ 考場時間術")
      tabs.forEachIndexed { idx, title ->
        val selected = selectedSubTab == idx
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
          ),
          modifier = Modifier
            .weight(1f)
            .clickable { selectedSubTab = idx }
        ) {
          Box(
            modifier = Modifier
              .padding(vertical = 8.dp)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              title,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      when (selectedSubTab) {
        0 -> { // Exam Bulletins
          item {
            Text("🗓️ DSE 2026/2027 官方最新日程與考綱", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("📅 2026 核心科目考試日程表", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                val schedule = listOf(
                  "4月9日 (四)" to "🇬🇧 英國語文 (紙一/紙二 閱讀與寫作)",
                  "4月10日 (五)" to "🇬🇧 英國語文 (紙三 聆聽與綜合)",
                  "4月13日 (一)" to "📐 數學必修部分 (紙一/紙二)",
                  "4月15日 (三)" to "🇨🇳 中國語文 (核心考驗)",
                  "4月17日 (五)" to "⚡ 物理科 (選修挑戰)",
                  "4月20日 (一)" to "🧪 化學科 (選修挑戰)"
                )
                schedule.forEach { (date, subject) ->
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(date, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(subject, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                  }
                }
              }
            }
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("📝 考評局報名須知與入閘指南", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  "1. 准考證核對：准考證一般於2月下旬發放，務必核對個人姓名、應考科目及試場編號。\n" +
                    "2. 身份證明：應試當天必須攜帶有效香港身份證正本，以及官方准考證正本入閘。平板或電子身份證恕不接納。\n" +
                    "3. 收音機檢查：應考英文科紙三聆聽時，須自備合格收音機及耳機，並確保電池充足、調頻(FM)正常運作。試場不提供額外收音設備。\n" +
                    "4. 計算機標籤：數學/理科應試計算機背面必須具備官方「H.K.E.A.A. APPROVED」紅色印刷或綠色標籤，否則不可帶入考場。",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("📑 主要學科最新考綱更新", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  "• 📐 數學必修：近年題型加大了二次方程平移、圓的方程在動點軌跡中與截距的綜合考察，減少了純幾何幾何證明的比重。\n" +
                    "• 🇬🇧 英文科：寫作部分更重思維的多角度切入，不再僅看詞彙深奧度，對邏輯連貫性（Cohesion）要求顯著提高。\n" +
                    "• ⚡ 物理科：加強了電磁感應（EMI）與放射性半衰期的定性解釋題的分數比重；減少了極端複雜公式計算。",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
        }
        1 -> { // Study Methods
          item {
            Text("📚 港式文憑試神級溫習法指南", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("🧠 費曼學習法 (Feynman Technique)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "【概念轉化成直覺之王】\n" +
                    "溫習一個複雜概念時（如物理的『冷次定律』或數學的『外心坐標推导』），試著將它用最直白的字眼、像解釋給 5 歲小童聽一樣寫在草稿紙上。一旦你在某些字眼卡住，即代表該處為你的邏輯盲點。Level Up 平台的改寫題便是利用此技術，讓考生在重新解答中自動填補思維漏洞！",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("🔄 主動回想與間隔重複 (Spaced Repetitive Retrieval)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "【大腦記憶對抗忘記】\n" +
                    "溫習完一個課題後，在第1天、第3天、第7天分別做 2 條對應改寫題，便是最具效率的間隔訓練法！",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("🍅 極致專注番茄鐘操卷法 (Pomodoro Deep Study)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "【防疲勞與多巴胺管理】\n" +
                    "將手機置於另一個房間，設定 25 分鐘不被打擾的無雜訊答題，時間一到強制休息 5 分鐘。休息時切忌刷社交平台，應喝水或拉展，使大腦分泌健康多巴胺，以利下一個深思回合！",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
        }
        2 -> { // Exam Strategy
          item {
            Text("⏳ 考場時間分配與 MC 3-Pass 答題法", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("🎯 MC 3-Pass 答題操卷術", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  "DSE MC 卷限時極度緊迫，切忌遇到難題便卡死。請嚴格執行 3-Pass 策略：\n\n" +
                    "• 1st Pass (秒殺題)：只重做一看便知算法的送分題與基礎概念題。一旦需要思考多於 15 秒，立即標記、跳過。這能確保基本盤的所有易取分數安穩入袋。\n\n" +
                    "• 2nd Pass (邏輯題)：此時心態放鬆、基本分已穩。重回標記題目，攻克具有中度運算與轉換的題目（如 Level Up 提供的 4 大對抗思維考題），每題限時 90 秒解答。\n\n" +
                    "• 3rd Pass (難題衝刺)：最後 10 分鐘，將精力投注於高難度或需要大運算量的壓軸幾何/二次分析題，或使用排除法、代入特值法、計算器程序（如神級常數等）作答考位。",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("🔋 考前半小時多巴胺管理法 (The Pre-Exam Dopamine Alignment)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "【穩定多巴胺降低焦慮】\n" +
                    "踏進考場前 30 分鐘，停止與合考同學高聲討論難題或答案，那只會激發游離的壓力與恐慌。此時最好戴上耳機聆聽純音樂，默默看一遍本平台的「錯題本(Mistakes)」，重溫自己的常犯代數 and 條件遺漏，將心率降到 75bpm，用最沉穩理性的姿態接招！",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
        }
      }
    }
  }
}

// --- SCREEN 6: CREATOR STORY (主創故事) ---
@Composable
fun AboutScreen() {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Text(
        "👋 做平台嘅初衷：這是一位2026 DSE考生的自白",
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        lineHeight = 26.sp
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "由 DSE.life 下架說起...",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            "我是 2026 文憑試的應屆考生。在漫長的 Study Leave 期間，相信大家都曾依賴 DSE.life 搵返昔日的歷屆試題。當初平台突然因無奈下架，大家那種頓失支柱、手足無措的感覺，我深有同感。\n\n" +
              "當時我就在想，與其盲目去死記背誦每一道歷屆題目（甚至多數人重做時只是記住了標準答案，卻沒有get到背後的原理），我們為甚麼不能做一個真正的「底層思維練習系統」？\n\n" +
              "這，就是 DSE Level Up 創立的初衷。我們用 AI 改寫了歷屆考試所有主要題型——修改了情境、數字與背景，但100%保留了考評局官方最愛的考評陷阱與邏輯考點。這樣，大家就擁有了一個完全合規、不用擔心侵權、又可以真正驗證自己是否學會了『底層邏輯』的操卷神器！",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color.Black
          )
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "平台的三大承諾",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.secondary
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            "1. 🔒 【合規安全】：不抄襲官方試卷原文，採用獨立改寫邏輯，杜絕侵權爭議。\n" +
              "2. 💰 【永久免費】：本項目完全不收費，沒有烦人廣告干擾，純粹出於好心想幫同屆同窗一齊衝星！\n" +
              "3. 🧠 【方法論導向】：不提供公式灌輸，利用 AI 閱卷官為每一層步驟做拆解，鍛鍊扺抗盲點的能力。",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Color.DarkGray
          )
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "期待你的回饋與支持",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "如果你有任何好玩的建議，或者想幫忙錄製名師解說、上架新學科題目，非常歡迎你聯絡我！我只是個普通的自修生戰友，希望能聽到你的聲音：\n\n" +
              "📧 合作及聯絡電郵：brianarebrian@gmail.com\n" +
              "📸 官方 Instagram：@dse_level_up_hk",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
        }
      }
    }
  }
}

// --- SCREEN 7: ADMIN LAB FOR EDUCATOR SIMULATOR ---
@Composable
fun AdminScreen(viewModel: DseViewModel) {
  var subjectInput by remember { mutableStateOf("math") }
  var topicInput by remember { mutableStateOf("") }
  var topicChineseInput by remember { mutableStateOf("") }
  var questionTextByInput by remember { mutableStateOf("") }
  var optA by remember { mutableStateOf("") }
  var optB by remember { mutableStateOf("") }
  var optC by remember { mutableStateOf("") }
  var optD by remember { mutableStateOf("") }
  var correctOpt by remember { mutableStateOf("A") }
  var methodTypeInput by remember { mutableStateOf("Transformation Thinking") }
  var originalRefInput by remember { mutableStateOf("") }

  var feedbackText by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Text(
        "🛠️ 出題實驗室 (Educator Lab)",
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary
      )
      Text(
        "教師或合作學生可在這裏模擬將改寫題目寫入本地庫（立刻於科目練習中生效，無需重新構建!）",
        fontSize = 12.sp,
        color = Color.Gray
      )

      if (feedbackText != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFFD4EDDA), RoundedCornerShape(8.dp))
            .padding(10.dp)
        ) {
          Text(feedbackText ?: "", color = Color(0xFF155724), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    item {
      OutlinedTextField(
        value = subjectInput,
        onValueChange = { subjectInput = it },
        label = { Text("科目代碼 (math, physics, chemistry, english)") },
        modifier = Modifier.fillMaxWidth()
      )
    }

    item {
      OutlinedTextField(
        value = topicInput,
        onValueChange = { topicInput = it },
        label = { Text("課題英文") },
        modifier = Modifier.fillMaxWidth()
      )
    }

    item {
      OutlinedTextField(
        value = topicChineseInput,
        onValueChange = { topicChineseInput = it },
        label = { Text("課題中文加方法框架") },
        modifier = Modifier.fillMaxWidth()
      )
    }

    item {
      OutlinedTextField(
        value = questionTextByInput,
        onValueChange = { questionTextByInput = it },
        label = { Text("試題改寫內容文字") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4
      )
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = optA,
          onValueChange = { optA = it },
          label = { Text("選項 A") },
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = optB,
          onValueChange = { optB = it },
          label = { Text("選項 B") },
          modifier = Modifier.weight(1f)
        )
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = optC,
          onValueChange = { optC = it },
          label = { Text("選項 C") },
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = optD,
          onValueChange = { optD = it },
          label = { Text("選項 D") },
          modifier = Modifier.weight(1f)
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("正解選項代碼：", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("A", "B", "C", "D").forEach { oCap ->
            val checked = correctOpt == oCap
            Button(
              onClick = { correctOpt = oCap },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
              )
            ) {
              Text(oCap, color = if (checked) Color.White else Color.Black)
            }
          }
        }
      }
    }

    item {
      OutlinedTextField(
        value = methodTypeInput,
        onValueChange = { methodTypeInput = it },
        label = { Text("底層核心邏輯 (e.g. Transformation Thinking)") },
        modifier = Modifier.fillMaxWidth()
      )
    }

    item {
      OutlinedTextField(
        value = originalRefInput,
        onValueChange = { originalRefInput = it },
        label = { Text("考評局對照原型年份") },
        modifier = Modifier.fillMaxWidth()
      )
    }

    item {
      Button(
        onClick = {
          if (topicInput.isEmpty() || questionTextByInput.isEmpty()) {
            feedbackText = "⚠️ 請先填寫完整的課題與題目文字！"
            return@Button
          }
          val customRandomId = "custom_${System.currentTimeMillis()}"
          val entity = QuestionEntity(
            id = customRandomId,
            subject = subjectInput.lowercase(),
            topic = topicInput,
            topicChinese = topicChineseInput,
            difficulty = "Medium",
            questionText = questionTextByInput,
            optionA = optA,
            optionB = optB,
            optionC = optC,
            optionD = optD,
            correctAnswer = correctOpt,
            explanationHint = "名師出題挑戰，加油！",
            explanationDetailed = "【導師口訣：實踐底層思維抗爭】由老師或夥伴自定義寫入庫中的改寫大作！",
            methodologyType = methodTypeInput,
            stepNotes = "步驟1: 依照題目解析.",
            marks = 3,
            youtubeUrl = "",
            originalRef = originalRefInput
          )

          scope.launch {
            viewModel.recordAnswer(customRandomId, true) // Clear/Reset helper
            // We insert real custom question!
            val db = com.example.database.DseDatabase.getDatabase(viewModel.getApplication())
            db.dseDao().insertQuestion(entity)
            feedbackText = "🎉 出題成功！改寫題目已實時錄入本地庫，您現在可以點擊「科目挑戰」進行挑戰！"

            // Clear inputs
            topicInput = ""
            topicChineseInput = ""
            questionTextByInput = ""
            optA = ""
            optB = ""
            optC = ""
            optD = ""
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp)
      ) {
        Text("生成改寫題並上架 🚀")
      }
    }
  }
}

// Confetti Overlay widget
@Composable
fun ConfettiAnimationOverlay(onFinished: () -> Unit) {
  val animValue = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    animValue.animateTo(
      targetValue = 1f,
      animationSpec = tween(1200, easing = LinearEasing)
    )
    onFinished()
  }

  Canvas(modifier = Modifier.fillMaxSize()) {
    val random = Random(1234)
    val progression = animValue.value

    for (i in 0..60) {
      val xOffset = random.nextFloat() * size.width
      val startY = -50f
      val currentY = startY + (size.height + 100f) * progression
      val radius = 8f + random.nextFloat() * 12f
      val color = Color(
        red = random.nextFloat(),
        green = random.nextFloat(),
        blue = random.nextFloat(),
        alpha = 1f - (progression * 0.3f)
      )

      drawCircle(
        color = color,
        radius = radius,
        center = Offset(
          x = (xOffset + Math.sin(progression.toDouble() * 10.0 + i) * 60.0).toFloat(),
          y = currentY
        )
      )
    }
  }
}
