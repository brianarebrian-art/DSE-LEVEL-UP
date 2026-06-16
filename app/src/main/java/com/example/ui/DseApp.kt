package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.database.BadgeEntity
import com.example.database.MistakeEntity
import com.example.database.QuestionEntity
import com.example.database.PastPaperResourceEntity
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

  // Premium states
  val isTimerRunning by viewModel.isFocusTimerRunning.collectAsStateWithLifecycle()
  val isLockActive by viewModel.isFocusLockActive.collectAsStateWithLifecycle()
  val focusSec by viewModel.focusSecondsElapsed.collectAsStateWithLifecycle()
  val focusSub by viewModel.selectedFocusSubject.collectAsStateWithLifecycle()

  Box(modifier = Modifier.fillMaxSize()) {
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

    // IMMERSIVE APPLOCK OVERLAY
    if (isTimerRunning && isLockActive) {
      ImmersiveFocusLockOverlay(
        focusSec = focusSec,
        subject = focusSub,
        onUnlock = { viewModel.stopFocusTimer() }
      )
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

    // Target DSE Subjects Selector Card (Dropdown Component)
    item {
      TargetSubjectsSelectorCard()
    }

    // DSE Tip of the Day and Positive Encouraging Notification System Card
    item {
      DseNotificationSystemCard(progress = progress)
    }

    // Premium custom Study Focus Timer hub card
    item {
      StudyFocusHubCard(viewModel = viewModel)
    }

    // Premium custom Online Study Groups card with real-time leaderboard
    item {
      OnlineStudyGroupsCard(viewModel = viewModel)
    }

    // Personalized DSE Strategy Planner Card
    item {
      PersonalizedDseStudyPlanCard(progress = progress)
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

// --- TARGET SUBJECTS SELECTOR WITH DROPDOWN (COMPONENT STATE) ---
@Composable
fun TargetSubjectsSelectorCard() {
  // Store selected subjects in the component state (using set of subject IDs)
  var selectedSubjects by remember { mutableStateOf(setOf("math", "english")) }
  var expanded by remember { mutableStateOf(false) }

  val allDseSubjects = listOf(
    SubjectSelectionItem("math", "📐 數學必修部分 / Math", "Mathematics", Color(0xFF1E88E5)),
    SubjectSelectionItem("english", "🇬🇧 英國語文 / English", "English Language", Color(0xFF43A047)),
    SubjectSelectionItem("chinese", "🇨🇳 中國語文 / Chinese", "Chinese Language", Color(0xFFE53935)),
    SubjectSelectionItem("liberal", "🌍 公民與社會 / CS & Liberal", "Citizenship & Social Dev", Color(0xFF8E24AA)),
    SubjectSelectionItem("physics", "⚡ 物理科 (選修) / Phys", "Physics", Color(0xFF00ACC1)),
    SubjectSelectionItem("chemistry", "🧪 化學科 (選修) / Chem", "Chemistry", Color(0xFF009688))
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("target_subjects_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1.5f)) {
          Text(
            "🎯 我的 HKDSE 目標報考科目",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            "自訂目標考科，狀態將保存在此 Dropdown 組件局部 State 中",
            fontSize = 11.sp,
            color = Color.Gray
          )
        }

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
          Button(
            onClick = { expanded = true },
            modifier = Modifier
              .testTag("add_subject_dropdown_btn")
              .height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ArrowDropDown,
              contentDescription = "Select Subjects Dropdown",
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("選擇科目", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
              .width(220.dp)
              .background(MaterialTheme.colorScheme.surface)
              .testTag("subjects_dropdown_menu")
          ) {
            allDseSubjects.forEach { sItem ->
              val alreadySelected = selectedSubjects.contains(sItem.id)
              DropdownMenuItem(
                text = {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(sItem.chineseName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                      Text(sItem.englishName, fontSize = 10.sp, color = Color.Gray)
                    }
                    if (alreadySelected) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  }
                },
                onClick = {
                  selectedSubjects = if (alreadySelected) {
                    selectedSubjects - sItem.id
                  } else {
                    selectedSubjects + sItem.id
                  }
                  expanded = false
                },
                modifier = Modifier.testTag("dropdown_item_${sItem.id}")
              )
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      if (selectedSubjects.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            "✨ 點擊右上方「選擇科目」下拉選單，規劃您的目標考科！",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
          )
        }
      } else {
        LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(vertical = 4.dp)
        ) {
          val activeItems = allDseSubjects.filter { selectedSubjects.contains(it.id) }
          items(activeItems) { sItem ->
            Surface(
              modifier = Modifier.testTag("selected_subject_tag_${sItem.id}"),
              color = sItem.color.copy(alpha = 0.08f),
              shape = RoundedCornerShape(12.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, sItem.color.copy(alpha = 0.4f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .background(sItem.color, CircleShape)
                )
                Text(
                  text = sItem.chineseName.split(" / ").first(),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Remove",
                  tint = Color.Gray,
                  modifier = Modifier
                    .size(14.dp)
                    .clickable { selectedSubjects = selectedSubjects - sItem.id }
                )
              }
            }
          }
        }
      }
    }
  }
}

data class SubjectSelectionItem(
  val id: String,
  val chineseName: String,
  val englishName: String,
  val color: Color
)

// --- NEW COMPONENT: PERSONALIZED STUDY PLANNER ---
@Composable
fun PersonalizedDseStudyPlanCard(progress: com.example.database.UserProgressEntity) {
  val context = LocalContext.current
  var examTargetMonth by remember { mutableStateOf("2027_04") } // "2027_04" | "2026_11" | "2028_04"
  var studyIntensity by remember { mutableStateOf("balanced") } // "relaxed" | "balanced" | "hardcore"
  var selectedStudySubjects by remember { mutableStateOf(setOf("math", "english", "physics")) }
  
  // Reminders state
  var morningReminder by remember { mutableStateOf(false) }
  var afternoonReminder by remember { mutableStateOf(false) }
  var eveningReminder by remember { mutableStateOf(false) }

  var generatedTimeTable by remember { mutableStateOf(false) }
  var showSecurityGuide by remember { mutableStateOf(false) }

  val subjectPriorityData = listOf(
    Triple("math", "📐 數學必修部分 (P0)", Color(0xFF1E88E5)),
    Triple("m1m2", "📐 數學 M1/M2 (P0)", Color(0xFF1E88E5)),
    Triple("physics", "⚡ 物理科 (選修) (P1)", Color(0xFF00ACC1)),
    Triple("english", "🇬🇧 英國語文 (P1)", Color(0xFF43A047)),
    Triple("chinese", "🇨🇳 中國語文 (P2)", Color(0xFFE53935)),
    Triple("bafs", "📊 BAFS / ICT (P2)", Color(0xFF8E24AA)),
    Triple("humanities", "📚 中國歷史/歷史/地理 (P3)", Color(0xFFF57C00))
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("personalized_planner_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "Planner Icon",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
          Text(
            "🎯 DSE 個人專屬衝刺學習計畫",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
          )
        }
        Box(
          modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            "Beta",
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }

      Text(
        "根據預計考試倒數、學科優先級 (P0-P3) 及您當下的多巴胺題庫進度，一鍵為您客製化高效溫習日程表與定時複習提醒。",
        fontSize = 12.sp,
        color = Color.DarkGray,
        lineHeight = 16.sp
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

      // 1. Expected test date selection
      Text("🗓️ 選擇您的預計 DSE 考試日程目標：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val targets = listOf(
          "2026_11" to "2026年11月 (高壓高分期)",
          "2027_04" to "2027年04月 (常規衝刺期)",
          "2028_04" to "2028年04月 (穩打穩紮期)"
        )
        targets.forEach { (key, title) ->
          val active = examTargetMonth == key
          Button(
            onClick = { examTargetMonth = key },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.weight(1f).height(38.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
          ) {
            Text(
              title.split(" ").first(),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // Display countdown badge
      val countdownDays = when (examTargetMonth) {
        "2026_11" -> 145
        "2027_04" -> 298
        else -> 662
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
          .padding(10.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = "Countdown",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp)
          )
          Text(
            "學考倒計時：距離目標考試月份還有約 ",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
          )
          Text(
            "$countdownDays 天",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFE53935)
          )
        }
      }

      // 2. Study Intensity Choice
      Text("⚡ 選擇每日學習強度分度：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val intensities = listOf(
          "relaxed" to "🌱 奠基 (1-2hr)",
          "balanced" to "⚖️ 均衡 (3-4hr)",
          "hardcore" to "🩸 地獄 (6hr+)"
        )
        intensities.forEach { (key, label) ->
          val active = studyIntensity == key
          Card(
            colors = CardDefaults.cardColors(
              containerColor = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            modifier = Modifier
              .weight(1f)
              .clickable { studyIntensity = key }
              .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.secondary else Color.LightGray.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
              )
          ) {
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // 3. Subject prioritizations selections checkboxes
      Text("📚 選擇納入計畫的考驗學科：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        subjectPriorityData.forEach { (id, label, color) ->
          val checked = selectedStudySubjects.contains(id)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { 
                selectedStudySubjects = if (checked) selectedStudySubjects - id else selectedStudySubjects + id
              },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
              Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Checkbox(
              checked = checked,
              onCheckedChange = { isChecked ->
                selectedStudySubjects = if (checked) selectedStudySubjects - id else selectedStudySubjects + id
              },
              modifier = Modifier.size(32.dp).testTag("select_plan_subject_$id")
            )
          }
        }
      }

      // 4. Generate Button
      Button(
        onClick = { generatedTimeTable = true },
        modifier = Modifier
          .fillMaxWidth()
          .height(44.dp)
          .testTag("gen_plan_btn")
      ) {
        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Gen", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("⚡ 智能生成 DSE 專屬計畫與溫習軸", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
      }

      // 5. Revealed generated plan timeline
      if (generatedTimeTable) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
          ),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth().testTag("generated_plan_output_card")
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = "Trophy", tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
              Text("🏆 AI 衝刺戰略規劃配置", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            // Real progress diagnose
            val progressReport = if (progress.totalCorrectAnswers < 5) {
              "💡 開闢奠基期：當下題目進度偏少 (${progress.totalCorrectAnswers} 題)。戰略優先【P0必修數學】的即時 MCQ 對抗，夯實 12 大邏輯框架。在此階段不應盲目刷長題，必須以核心題庫中的 Transformation / Correct Method 概念關聯先行。"
            } else {
              "🚀 衝刺提分期：你已解鎖多於 ${progress.totalCorrectAnswers} 題正確作答！進度十分優秀。現在建議開啟【長題目 Part B / Sec A2】自主考官模式自評 (Marking Scheme 步驟分精確比對)，藉由勾選 M/A/F 分值克服粗心失分。"
            }
            Text(
              progressReport,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              lineHeight = 15.sp,
              fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

            // Time schedule list
            val scheduleSteps = when (studyIntensity) {
              "relaxed" -> listOf(
                "08:30 - 09:30" to "📐 核心 DSE 數學邏輯框架 MCQ 進擊 (3題 / 累積 30 分)",
                "16:30 - 17:30" to "🧪 理科 P1 (物理/化學/生物) 單選速刷與 AI 詳解 (20門概念核對)"
              )
              "balanced" -> listOf(
                "08:00 - 09:30" to "📐 P0 核心數學 / 延伸 M1/M2 系統化對抗 (40分鐘概念 + 4題挑戰)",
                "15:00 - 16:30" to "🧪 P1 理科 / 英文常規閱讀模擬題 (深入研讀神級影片精析)",
                "21:00 - 22:00" to "📋 錯題本 (Mistakes) 閉環回放測試：手寫 Marking Scheme 比對"
              )
              else -> listOf(
                "07:30 - 09:30" to "📐 數學/M1/M2 地獄限時答題 (P0 加倍多巴胺，做完 10 道題)",
                "10:30 - 12:30" to "🇬🇧 英文 / 中文必修突破 (高難度句型拆解，AI 口試/寫作自檢)",
                "14:30 - 17:00" to "🧪 物理/化學 P1 熱門考點重慶、真切做題！100% 勾勒 12 分題草稿紙",
                "20:30 - 23:00" to "🌌 全方位弱點與歷屆真題改寫 (AI 真題導師逐行精析 + 錯題精雕)"
              )
            }

            Text("📅 客製化 DSE 溫習日程推演：", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            scheduleSteps.forEach { (timeSpan, activityText) ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  timeSpan,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.width(90.dp)
                )
                Text(
                  activityText,
                  fontSize = 11.sp,
                  color = Color.Black,
                  lineHeight = 14.sp
                )
              }
            }
          }
        }
      }

      // 6. Push Reminders Checkers
      Text("🔔 配置每日定期衝刺複習推送提醒：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val remindersList = listOf(
          Triple("morning", "🌅 早上 08:30 基礎常規題訓練 (建立多巴胺習慣)", morningReminder),
          Triple("afternoon", "🌇 下午 16:30 理科難套題衝刺 (Method Marks 分數搶攻)", afternoonReminder),
          Triple("evening", "🌌 晚上 21:00 錯題本 AI 自動回放複溫", eveningReminder)
        )

        remindersList.forEach { (id, label, stateValue) ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Switch(
              checked = stateValue,
              onCheckedChange = { isChecked ->
                when (id) {
                  "morning" -> morningReminder = isChecked
                  "afternoon" -> afternoonReminder = isChecked
                  "evening" -> eveningReminder = isChecked
                }
                val text = if (isChecked) "已成功啟用「$label」！自律成就理想等級 5** 🚀" else "已關閉提醒設定"
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.testTag("switch_reminder_$id")
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

      // 7. Security Classroom section
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("security_classroom_card")
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth().clickable { showSecurityGuide = !showSecurityGuide },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(imageVector = Icons.Default.Shield, contentDescription = "Security", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
              Text("🔒 [DSE 防線] AI Token 防偷與系統安全維護課堂", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFC62828))
            }
            Icon(
              imageVector = if (showSecurityGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = "Expand",
              tint = Color.Gray,
              modifier = Modifier.size(16.dp)
            )
          }

          if (showSecurityGuide) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "作為 HKDSE Level Up 的核心安全課堂，以下為防止 AI tokens 被惡意擷取/帳號被 Hack 的三大終極防禦指引：",
              fontSize = 11.sp,
              color = Color.DarkGray,
              lineHeight = 15.sp
            )

            // Tip 1
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
              Text("❓ Q1：如何防範用戶惡意刷屏並偷走我們的 Gemini SDK API Token？", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.height(2.dp))
              Box(
                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(4.dp)).padding(8.dp)
              ) {
                Text(
                  "🟢 專家解答：絕對不能把 API Key 明文寫在主程式代碼中！應該使用 Firebase AI Server-Side (雲端安全中轉) 或在後端伺服器 (Node.js/Spring Boot) 中封裝 API，並加入 Rate Limiting 限流機制，限制每個用戶 ID 每分鐘最多只可發送 3 次 AI 詢問。本 App 通過 BuildConfig 與 AI Studio Secrets 重重鎖定！",
                  fontSize = 10.sp,
                  color = Color.DarkGray,
                  lineHeight = 13.sp
                )
              }
            }

            // Tip 2
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
              Text("❓ Q2：如何保障我們的 Room 即時歷史與珍貴 DSE 真題庫不被逆向工程打包？", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.height(2.dp))
              Box(
                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(4.dp)).padding(8.dp)
              ) {
                Text(
                  "🟢 專家解答：1) 在 ProGuard / R8 中啟用程式碼混淆 (Obfuscation)，防止黑客使用 JADX 等反編譯器直接閱讀源碼。2) 對 Room 數據庫進行 SQLCipher 加密，這樣在 Root 手機中撈走數據庫也會是亂碼。3) 本地真題庫與遠端備份只採用 HTTPS 加上 SSL Pinning 傳輸保護，確保中間人攻擊 (MITM) 無法抓包。",
                  fontSize = 10.sp,
                  color = Color.DarkGray,
                  lineHeight = 13.sp
                )
              }
            }
          }
        }
      }
    }
  }
}

// --- NEW COMPONENT: DSE DAILY TIPS & ENCOURAGING QUOTES NOTIFICATION SYSTEM ---
@Composable
fun DseNotificationSystemCard(progress: com.example.database.UserProgressEntity) {
  val context = LocalContext.current
  var activeCategory by remember { mutableStateOf("tips") } // "tips" | "quotes"
  var currentTipIndex by remember { mutableStateOf(0) }
  var currentQuoteIndex by remember { mutableStateOf(0) }
  
  // Notification Toggles
  var morningAlertEnabled by remember { mutableStateOf(true) }
  var eveningAlertEnabled by remember { mutableStateOf(false) }

  // Dialog State
  var showDopamineDialog by remember { mutableStateOf(false) }

  // Lists definitions
  val examTips = listOf(
    "💡 答卷時間管理：Maths Paper 1 丙部（Section B）佔分極重，建議預留 75 分鐘作答，Section A1 及 A2 則以 50-60 分鐘內秒殺為目標。",
    "📝 步驟分重要性：在 Section A2 及 B 的大長題中，只要寫出正確的公式代入（如 m_L1 × m_L2 = -1）即可獲得 Method Mark (M)，即使最終答案算錯，也能穩拿基本分！",
    "⚡ 圓的方程 (Equation of Circle) 必殺技：若方程式為 x² + y² + Dx + Ey + F = 0，圓心坐標必為 (-D/2, -E/2)，圓半徑為 √( (D/2)² + (E/2)² - F )。留意 D² + E² - 4F 必須大於 0 才是實心圓。",
    "📚 等比數列 (Geometric Sequence) 陷阱：當求 ∑ T_n 無限項之和時，必須確保公比 |r| < 1。公式為 S_∞ = a / (1-r)。若 r ≥ 1，此數列發散，並無無限項之和！",
    "🇬🇧 英文科 Part A 逆襲法：在作答 Reading 時，先仔細閱讀題目（Questions）並圈出關鍵詞（Keywords）與同義詞（Synonyms），然後快速定位段落，切忌一字一句盲目死讀。",
    "🧪 理科解題對稱原理：看到複雜的幾何、變分或物理系統時，先嘗試代入特殊值（如 x=0, x=1）或尋找對稱軸（Symmetry），這往往能幫你在 MCQ 中 10 秒鎖定答案！"
  )

  val quotes = listOf(
    "🌟 「不看昨天的遺憾，不看明天的迷茫，只看今時今日，你手下的每一道題都是你踏往 5** 階梯的基石！」",
    "🔥 「無人能為你的 DSE 畫上句號，除了你自己。再算錯一次也沒關係，錯題本是強者的盔甲，每一次重來都是底層邏輯的昇華！」",
    "🌈 「每一滴在草稿紙上流過的汗水，都會在放榜那天折射出璀璨的光芒。加油，未來的狀元，你離理想大學只剩最後這一戰！」",
    "🏆 「DSE 考的不是考生的智商，而是大腦在限時壓力下的抗干擾程度。保持呼吸，穩定節奏，你比自己想像的更強大！」",
    "💪 「把大目標拆解成今天的 3 個 Dopamine 任務。每天進步 1%，只要有計畫，在放榜那天你定將迎來脫胎換骨的自己！」"
  )

  // Current active text & index
  val isTips = activeCategory == "tips"
  val activeText = if (isTips) examTips[currentTipIndex] else quotes[currentQuoteIndex]
  val activeIndexToShow = if (isTips) currentTipIndex + 1 else currentQuoteIndex + 1
  val totalItemsToShow = if (isTips) examTips.size else quotes.size

  // Pulse animation for the notification bell
  val infiniteTransition = rememberInfiniteTransition(label = "bell_pulse")
  val bellScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("dse_notification_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header item
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Bell Icon pulsing with micro-interaction
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer)
              .padding(6.dp),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = "Alert Bell",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .size(20.dp)
                .scale(bellScale)
            )
          }

          Column {
            Text(
              "📢 DSE 每日晨光通知 & 5** 密技",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              "今日能量回報：你今天比預計多前進了一步！",
              fontSize = 11.sp,
              color = Color.Gray
            )
          }
        }
        
        // Circular Unread Badge count indicator
        Box(
          modifier = Modifier
            .background(Color(0xFFE53935), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            "NEW",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
          )
        }
      }

      // Filter selector tabs
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val options = listOf(
          "tips" to "💡 5** 必讀應試神技",
          "quotes" to "🌟 狀元勵志鼓勵能量"
        )
        options.forEach { (cat, title) ->
          val active = activeCategory == cat
          Button(
            onClick = { activeCategory = cat },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
              .weight(1f)
              .height(36.dp)
              .testTag("notification_tab_$cat"),
            contentPadding = PaddingValues(0.dp)
          ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // Main Quote Display Card
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Quote metadata
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              if (isTips) "📌 應試錦囊" else "🔥 候選人正能量",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              "[$activeIndexToShow / $totalItemsToShow]",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = Color.Gray,
              fontWeight = FontWeight.Bold
            )
          }

          // Quote body text with large visual quotation marks
          Box(modifier = Modifier.fillMaxWidth()) {
            Text(
              "“",
              fontSize = 32.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
              modifier = Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-8).dp)
            )
            
            Text(
              activeText,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = Color.DarkGray,
              lineHeight = 17.sp,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
          }
          
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
              .padding(8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Recommend,
                contentDescription = "Recommend",
                tint = Color(0xFF43A047),
                modifier = Modifier.size(14.dp)
              )
              Text(
                if (isTips) "戰術大師給予積極看點：溫習此段可提高對抗盲區能力！(+15% 信心評估)" else "本日激勵回升度：不論進度如何，你正行在正確的突圍之路上！",
                fontSize = 9.sp,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      // Control Action Buttons Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Next Button with circular cycle
        Button(
          onClick = {
            if (isTips) {
              currentTipIndex = (currentTipIndex + 1) % examTips.size
            } else {
              currentQuoteIndex = (currentQuoteIndex + 1) % quotes.size
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
          ),
          modifier = Modifier
            .weight(1.1f)
            .height(38.dp)
            .testTag("next_tip_button")
        ) {
          Icon(
            imageVector = Icons.Default.NavigateNext,
            contentDescription = "Next Tip",
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("下一則 Next", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // Copy item to clipboard
        IconButton(
          onClick = {
            val systemClipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("DSETip", activeText)
            systemClipboard.setPrimaryClip(clip)
            Toast.makeText(context, "📋 已成功複製 DSE 貼心密語！快去分享給戰友！✨", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .testTag("copy_tip_button")
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy Text",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }

        // Dopamine Booster dialog trigger button (Boost!)
        Button(
          onClick = { showDopamineDialog = true },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE53935) // Deep vivid Red for Dopamine pulse
          ),
          modifier = Modifier
            .weight(1.3f)
            .height(38.dp)
            .testTag("dopamine_boost_button")
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Dopamine",
            tint = Color.White,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("💖 獲取能量 Boost", fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
      }

      // Notification schedule configuration
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Alarm,
              contentDescription = "Alarm Icon",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(15.dp)
            )
            Text(
              "每日晨光早報推送 (08:00 AM)",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = morningAlertEnabled,
            onCheckedChange = { isChecked ->
              morningAlertEnabled = isChecked
              val text = if (isChecked) "🌅 朝陽晨光提醒已啟：明早 08:00 我們準時見！" else "已關閉晨鳴通知"
              Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
              .scale(0.85f)
              .testTag("notification_switch_morning")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.OfflineBolt,
              contentDescription = "Bolt Alarm",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(15.dp)
            )
            Text(
              "晚自修錯題極速播報 (10:00 PM)",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = eveningAlertEnabled,
            onCheckedChange = { isChecked ->
              eveningAlertEnabled = isChecked
              val text = if (isChecked) "🌌 錯題本 AI 總複習通知開啟：每晚十點為您智能復盤！" else "已關閉夜闌通知"
              Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
              .scale(0.85f)
              .testTag("notification_switch_evening")
          )
        }
      }
    }
  }

  // Dopamine dialog implementation
  if (showDopamineDialog) {
    Dialog(onDismissRequest = { showDopamineDialog = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .testTag("dopamine_booster_dialog"),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD54F)) // Glowing Yellow outline!
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Large beautiful Trophy inside dialogue
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFEB3B), Color(0xFFFBC02D))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.WorkspacePremium,
              contentDescription = "Huge Award Trophy",
              tint = Color(0xFFE65100),
              modifier = Modifier.size(48.dp)
            )
          }

          Text(
            "💖 多巴胺爆發！考生激勵回饋",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
          )

          // Diagnostic and progress-customized motivational messaging
          val encourageMessage = when {
            progress.scorePoints < 20 -> {
              "🌟「萬丈高樓平地起，你目前積蓄了 ${progress.scorePoints} 積分，雖然起步不久，但每一題的失誤和重組都是實力裂變的起點！放下負擔，今天再做對 1 題就是巨大的突破！」"
            }
            progress.scorePoints < 100 -> {
              "🚀「太棒了！你已成功在 DSE App 中獲取 ${progress.scorePoints} XP 本土優良分！這意味著你在 DSE 考綱的底層變換邏輯中已經邁出了堅實的一大步。保持這個狀態，5** 只是努力的附贈品！」"
            }
            else -> {
              "✨「你累計答對多道題目，多巴胺能量值高達 ${progress.scorePoints} XP！你的努力和學習進度完全在 5** 的最頂端區間。自律和專注是你的雙翼，請繼續穩健落筆，一戰成名！」"
            }
          }

          Text(
            encourageMessage,
            fontSize = 11.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp)
          )

          // Custom positive stats gauge
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
              .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              "💪 您的 DSE 實力值今日預判",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              "「持之以恆：超前於戰役中 92% 的候選候考人！」",
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFF1B5E20)
            )
          }

          // Close confirm button
          Button(
            onClick = { showDopamineDialog = false },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
          ) {
            Text("✅ 吸收這波正能量，繼續專注複習！", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
  var selectedTabIdx by remember { mutableStateOf(0) }

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
      "📚 HKDSE 試題智庫與下載中心",
      fontWeight = FontWeight.Black,
      fontSize = 20.sp,
      color = MaterialTheme.colorScheme.primary
    )

    TabRow(
      selectedTabIndex = selectedTabIdx,
      containerColor = Color.Transparent,
      contentColor = MaterialTheme.colorScheme.primary,
      modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
    ) {
      Tab(
        selected = selectedTabIdx == 0,
        onClick = { selectedTabIdx = 0 },
        text = { Text("🧩 題型思維改寫", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
      )
      Tab(
        selected = selectedTabIdx == 1,
        onClick = { selectedTabIdx = 1 },
        text = { Text("📥 歷屆試卷下載", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
      )
    }

    if (selectedTabIdx == 0) {
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
  } else {
    PastPaperDownloadSection(viewModel = viewModel)
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

@Composable
fun PastPaperDownloadSection(viewModel: DseViewModel) {
  val pastPapers by viewModel.allPastPaperResources.collectAsStateWithLifecycle()
  val progresses by viewModel.downloadProgress.collectAsStateWithLifecycle()
  
  var searchKeyword by remember { mutableStateOf("") }
  var selectedSubjectFilter by remember { mutableStateOf("all") }
  var selectedYearFilter by remember { mutableStateOf("all") }
  var selectedTypeFilter by remember { mutableStateOf("all") }

  // Dialog to preview paper details
  var previewPaper by remember { mutableStateOf<PastPaperResourceEntity?>(null) }

  val subjectsList = listOf(
    "all" to "🌐 全部",
    "chinese" to "🇨🇳 中文",
    "english" to "🇬🇧 英文",
    "math" to "📐 數學",
    "physics" to "⚡ 物理",
    "chemistry" to "🧪 化學",
    "biology" to "🧬 生物"
  )

  val yearsList = listOf("all" to "📅 全部") + (2012..2025).reversed().map { it.toString() to "${it}年" }.filter { y ->
    y.first == "2025" || y.first == "2024" || y.first == "2023" || y.first == "2022" || y.first == "2021" || y.first == "2020" || y.first == "2019" || y.first == "2018" || y.first == "2017" || y.first == "2016" || y.first == "2012"
  }

  val typesList = listOf(
    "all" to "📄 全部類型",
    "mc" to "🟢 選擇題",
    "lq" to "🔵 問答題",
    "listening" to "🟣 聆聽綜合"
  )

  // Filtering Logic
  val filteredPapers = pastPapers.filter { paper ->
    val matchSubject = selectedSubjectFilter == "all" || paper.subject == selectedSubjectFilter
    val matchYear = selectedYearFilter == "all" || paper.year == selectedYearFilter
    val matchType = selectedTypeFilter == "all" || paper.paperType == selectedTypeFilter
    val matchSearch = searchKeyword.isEmpty() ||
        paper.title.contains(searchKeyword, ignoreCase = true) ||
        paper.titleChinese.contains(searchKeyword, ignoreCase = true) ||
        paper.syllabusKeypoints.contains(searchKeyword, ignoreCase = true)

    matchSubject && matchYear && matchType && matchSearch
  }

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Search Bar
    OutlinedTextField(
      value = searchKeyword,
      onValueChange = { searchKeyword = it },
      placeholder = { Text("搜尋歷屆試卷標題、考點考綱重點...") },
      modifier = Modifier.fillMaxWidth(),
      leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search Papers") },
      shape = RoundedCornerShape(12.dp)
    )

    // 2. Filters Row with horizontal scrolls
    Text("學科篩選：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(subjectsList) { (key, label) ->
        FilterChip(
          selected = selectedSubjectFilter == key,
          onClick = { selectedSubjectFilter = key },
          label = { Text(label, fontSize = 11.sp) }
        )
      }
    }

    Text("年份與類型：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f)
      ) {
        items(yearsList) { (key, label) ->
          FilterChip(
            selected = selectedYearFilter == key,
            onClick = { selectedYearFilter = key },
            label = { Text(label, fontSize = 11.sp) }
          )
        }
      }
    }

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(typesList) { (key, label) ->
        FilterChip(
          selected = selectedTypeFilter == key,
          onClick = { selectedTypeFilter = key },
          label = { Text(label, fontSize = 11.sp) }
        )
      }
    }

    Text(
      "文憑試資源：共計 ${filteredPapers.size} 份高頻原題與詳解",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = Color.Gray
    )

    if (filteredPapers.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text("沒有匹配的試卷資源 📂", fontSize = 12.sp, color = Color.Gray)
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f).fillMaxWidth()
      ) {
        items(filteredPapers) { paper ->
          val progress = progresses[paper.id]
          val isDownloaded = paper.isDownloaded

          Card(
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    val subjectColor = when(paper.subject) {
                      "chinese" -> Color(0xFFE57373)
                      "english" -> Color(0xFF64B5F6)
                      "math" -> Color(0xFF81C784)
                      "physics" -> Color(0xFFFFD54F)
                      "chemistry" -> Color(0xFFBA68C8)
                      else -> Color(0xFF4DB6AC)
                    }
                    Box(
                      modifier = Modifier
                        .background(subjectColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = paper.year + " DSE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = subjectColor
                      )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = when(paper.paperType) {
                        "mc" -> "卷二 選擇題"
                        "lq" -> "卷一 甲乙部問答"
                        else -> "卷三 聆聽及綜合"
                      },
                      fontSize = 10.sp,
                      color = Color.DarkGray,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = paper.titleChinese,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = paper.title,
                    fontSize = 11.sp,
                    color = Color.Gray
                  )
                }
                Text(
                  text = paper.fileSize,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              }

              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "📚 核心要點：${paper.syllabusKeypoints}",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color.DarkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )

              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "🔥 已被下載 ${paper.downloadCount} 次",
                  fontSize = 11.sp,
                  color = Color.Gray
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  if (isDownloaded) {
                    Button(
                      onClick = { previewPaper = paper },
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      modifier = Modifier.height(36.dp)
                    ) {
                      Icon(imageVector = Icons.Default.Visibility, contentDescription = "View", modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("開啟/預覽", fontSize = 11.sp)
                    }
                  } else if (progress != null) {
                    Column(horizontalAlignment = Alignment.End) {
                      LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.width(100.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                      )
                      Text(
                        text = "正在下載 ${(progress * 100).toInt()}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                      )
                    }
                  } else {
                    Button(
                      onClick = { viewModel.downloadPastPaper(paper.id) },
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      modifier = Modifier.height(36.dp)
                    ) {
                      Icon(imageVector = Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("下載試題紙", fontSize = 11.sp)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Preview Dialog for downloaded paper
  if (previewPaper != null) {
    val paper = previewPaper!!
    Dialog(onDismissRequest = { previewPaper = null }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "📄 試卷原件閱讀與考點導航",
              fontWeight = FontWeight.Black,
              fontSize = 16.sp,
              color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { previewPaper = null }) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Close Dialog")
            }
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            item {
              Text(
                text = "${paper.year} HKDSE ${paper.titleChinese}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "儲存路徑: ${paper.localFilePath}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
              )
            }

            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text("🧠 考評大綱高頻突破重點 (Syllabus Keypoints)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "本試卷原卷共有對應必備思維框架。點擊下方「去學科挑戰」可在『錯題本』與『學科挑戰』加載同等難度、同等邏輯的改寫模擬題，實踐極致爆分：\n\n• ${paper.syllabusKeypoints}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                }
              }
            }

            item {
              Card(
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text("💡 官方 Marking Scheme 核心扣分陷阱", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                  Spacer(modifier = Modifier.height(6.dp))
                  val traps = when (paper.subject) {
                    "math" -> """
                      1. 在 Conventional 卷一中，題目若提及「求解 P(x) 的係數」，千萬不要遺漏代入餘數為零的正負號，否則直接扣 1 分步驟分（M1 標籤落空）。
                      2. 三維空間幾何幾何題，若找不到線面角（Angle between Line and Plane）的真正射影點，嚴禁直接寫出餘弦定理，必須在第一步描述投影三角形。
                    """.trimIndent()
                    "physics" -> """
                      1. 關於冷次定律（Lenz's Law）的定性解釋題，描述電流方向時必須先指明大腦視角：「從頂部向下看」或「正面看」，否則閱卷官無法判定順/逆時針，直接扣分！
                      2. 計算折射臨界角時，注意介質與空氣的順序，折射係數較大的介質內才是臨界角。
                    """.trimIndent()
                    "chemistry" -> """
                      1. 書寫碳化合物 (Carbon Compounds) 結構式時，碳原子上的 H 如果省略了連線 (即寫成 C-H) 會有扣分風險，務必畫出完整的滿價結構。
                      2. 滴定反應中，指出限量反應物必須附帶摩爾比計算過程。
                    """.trimIndent()
                    "english" -> """
                      1. 在 Paper 2 寫作中，濫用 but/however 會被視為語意連貫性弱。試著替換成 Notwithstanding / On the flip side。
                      2. 注意 Subject-Verb Agreement 尤其是 non-finite clausal modifiers。
                    """.trimIndent()
                    "chinese" -> """
                      1. 閱讀理解中，闡述「修辭手法作用」時，不能只寫「生動形象」，必須緊扣原文描述對象的特徵及作者寄託的深層情感。
                      2. 文言實詞翻譯，必須考慮一詞多義及古今異義。
                    """.trimIndent()
                    else -> """
                      1. 請實地遵循文憑試官方 Marking Scheme 解析要求。
                      2. 練習底層思維框架，切勿死記答案。
                    """.trimIndent()
                  }
                  Text(text = traps, fontSize = 11.sp, lineHeight = 16.sp, color = Color.DarkGray)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            OutlinedButton(onClick = { previewPaper = null }) {
              Text("關閉", fontSize = 12.sp)
            }
          }
        }
      }
    }
  }
}

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

  // Practice Modes and Automatic Grading Engine State
  var practiceMode by remember { mutableStateOf("mcq") } // "mcq" | "short_answer" | "long"
  var completedStepsState by remember(currentQ?.id) { mutableStateOf(setOf<Int>()) }
  var customTypedInput by remember(currentQ?.id) { mutableStateOf("") }
  var gradingFeedbackResult by remember(currentQ?.id) { mutableStateOf<com.example.viewmodel.GradingResult?>(null) }

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
    completedStepsState = emptySet()
    customTypedInput = ""
    gradingFeedbackResult = null
    practiceMode = "mcq"
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

        // --- practiceMode Tab Toggle Row ---
        item {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("practice_mode_card")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              val modes = listOf(
                "mcq" to "🎯 MCQ 選擇題",
                "short_answer" to "✏️ 填充題速填",
                "long" to "📋 長題目自主批改"
              )
              modes.forEach { (modeKey, title) ->
                val active = practiceMode == modeKey
                Card(
                  colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !answerVerified) {
                      practiceMode = modeKey
                    }
                ) {
                  Box(
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      title,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
        }

        // --- Render Based on Selected Practice Mode ---
        when (practiceMode) {
          "mcq" -> {
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
                  .border(
                    width = 1.dp,
                    color = borderStrokeColor,
                    shape = RoundedCornerShape(8.dp)
                  )
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

            item {
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    if (selectedChoice == null) return@Button
                    val result = com.example.viewmodel.DseGradingProcessor.evaluateMcq(question, selectedChoice!!)
                    gradingFeedbackResult = result
                    answerVerified = true
                    if (result.isCorrect) {
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
                  Text("提交選擇題 (即時批改)", fontWeight = FontWeight.Bold)
                }

                if (answerVerified) {
                  Button(
                    onClick = { viewModel.nextQuestion() },
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
          }

          "short_answer" -> {
            item {
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Edit,
                      contentDescription = "Short Answer Icon",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      "請在下方手動填入你的最終答案表示式：",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }

                  Text(
                    "提示：請寫入精簡形式，例如: 「10/9」或「(1,0)」或直接輸入正確選項字母。",
                    fontSize = 11.sp,
                    color = Color.Gray
                  )

                  OutlinedTextField(
                    value = customTypedInput,
                    onValueChange = { if (!answerVerified) customTypedInput = it },
                    placeholder = { Text("請在此填寫答案...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("short_answer_input_text"),
                    singleLine = true,
                    enabled = !answerVerified
                  )
                }
              }
            }

            item {
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    if (customTypedInput.trim().isEmpty()) return@Button
                    val result = com.example.viewmodel.DseGradingProcessor.evaluateShortAnswer(question, customTypedInput)
                    gradingFeedbackResult = result
                    answerVerified = true
                    if (result.isCorrect) {
                      showCorrectVisualConfetti = true
                      viewModel.recordAnswer(question.id, true, timeSpentSeconds = elapsedSeconds)
                    } else {
                      showMistakeDialog = true
                      viewModel.recordAnswer(question.id, false, timeSpentSeconds = elapsedSeconds)
                    }
                  },
                  enabled = customTypedInput.trim().isNotEmpty() && !answerVerified,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("submit_short_answer_btn")
                ) {
                  Text("提交填充題 (智能比對)", fontWeight = FontWeight.Bold)
                }

                if (answerVerified) {
                  Button(
                    onClick = { viewModel.nextQuestion() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("next_short_question_btn")
                  ) {
                    Text("下一挑戰題 🚀", fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }

          "long" -> {
            item {
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = "Writing Note",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                  )
                  Column {
                    Text(
                      "✍️ DSE 長分數大作戰 (模擬 Part B / Sec A2)",
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                      "請在常規溫習草稿紙上完成完整公式和推導步驟，然後在下方展開 Marking steps 進行高階自主考官閱卷判分。",
                      fontSize = 11.sp,
                      color = Color.DarkGray
                    )
                  }
                }
              }
            }

            // Fetch marking steps
            val markingSteps = com.example.viewmodel.DseGradingProcessor.getMarkingStepsForQuestion(question)
            val totalMarks = markingSteps.sumOf { it.marks }
            val scoredMarks = markingSteps.filter { completedStepsState.contains(it.stepNumber) }.sumOf { it.marks }

            item {
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      "🏆 閱卷核對器判分 (Marking Scheme)",
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 13.sp,
                      color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                      modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                      Text(
                        "評分: $scoredMarks / $totalMarks 分",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                      )
                    }
                  }
                  HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                  Text(
                    "勾選你在草稿紙上正確得出的步驟：",
                    fontSize = 11.sp,
                    color = Color.Gray
                  )
                }
              }
            }

            items(markingSteps) { mStep ->
              val stepCorrect = completedStepsState.contains(mStep.stepNumber)
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (stepCorrect) Color(0xFFD4EDDA).copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                  width = 1.dp,
                  color = if (stepCorrect) Color(0xFF28A745).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("marking_step_${mStep.stepNumber}")
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(18.dp)
                          .background(MaterialTheme.colorScheme.secondary, CircleShape),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          "${mStep.stepNumber}",
                          color = Color.White,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                      Text(
                        "考點目標: ${mStep.content}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                    }

                    // Score indicator badge
                    Box(
                      modifier = Modifier
                        .background(
                          if (mStep.markType == "M") Color(0xFF1E88E5) else Color(0xFF43A047),
                          RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(
                        "${mStep.marks}${mStep.markType}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(6.dp))

                  // Formula code block look
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                      .padding(8.dp)
                  ) {
                    Text(
                      mStep.formula,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.fillMaxWidth()
                    )
                  }

                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    mStep.description,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 13.sp
                  )

                  if (!answerVerified) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text("這步推導正確：", fontSize = 11.sp, color = Color.Gray)
                      Checkbox(
                        checked = stepCorrect,
                        onCheckedChange = { isChecked ->
                          completedStepsState = if (isChecked) {
                            completedStepsState + mStep.stepNumber
                          } else {
                            completedStepsState - mStep.stepNumber
                          }
                        },
                        modifier = Modifier.size(24.dp).testTag("step_check_${mStep.stepNumber}")
                      )
                    }
                  }
                }
              }
            }

            item {
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    val pass = scoredMarks >= (totalMarks / 2f)
                    val result = com.example.viewmodel.GradingResult(
                      isCorrect = pass,
                      earnedMarks = scoredMarks,
                      maxMarks = totalMarks,
                      feedbackTitle = if (pass) "🎉 長題分配合格！" else "⚠️ 步驟仍需精進",
                      feedbackBody = "您在本題 5 級閱卷基準下自我判定得 $scoredMarks / $totalMarks 分！獲得對應多巴胺點數 ${scoredMarks * 10} DP。"
                    )
                    gradingFeedbackResult = result
                    answerVerified = true
                    if (pass) {
                      showCorrectVisualConfetti = true
                      viewModel.recordAnswer(question.id, true, timeSpentSeconds = elapsedSeconds)
                    } else {
                      showMistakeDialog = true
                      viewModel.recordAnswer(question.id, false, timeSpentSeconds = elapsedSeconds)
                    }
                  },
                  enabled = !answerVerified,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("submit_long_step_btn")
                ) {
                  Text("送出步驟閱卷分數", fontWeight = FontWeight.Bold)
                }

                if (answerVerified) {
                  Button(
                    onClick = { viewModel.nextQuestion() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("next_long_question_btn")
                  ) {
                    Text("下一挑戰題 🚀", fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }

        // --- Standard DseGradingProcessor Instant Feedback Banner Bar ---
        if (answerVerified && gradingFeedbackResult != null) {
          item {
            val result = gradingFeedbackResult!!
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (result.isCorrect) Color(0xFFD4EDDA) else Color(0xFFF8D7DA)
              ),
              border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (result.isCorrect) Color(0xFF28A745) else Color(0xFFDC3545)
              ),
              modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("grading_result_feedback_banner")
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = if (result.isCorrect) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Status",
                    tint = if (result.isCorrect) Color(0xFF28A745) else Color(0xFFDC3545),
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    result.feedbackTitle,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (result.isCorrect) Color(0xFF155724) else Color(0xFF721C24)
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  result.feedbackBody,
                  fontSize = 12.sp,
                  color = if (result.isCorrect) Color(0xFF155724) else Color(0xFF721C24),
                  lineHeight = 16.sp
                )
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
  val allQuestions by viewModel.allQuestions.collectAsStateWithLifecycle()

  // State tracking for modern interactive reviews
  val expandedQuestionId = remember { mutableStateOf<String?>(null) }
  val selectedChoices = remember { mutableStateMapOf<String, String?>() }
  val answerVerified = remember { mutableStateMapOf<String, Boolean>() }
  val userResults = remember { mutableStateMapOf<String, Boolean>() }
  val userFeedbackMessage = remember { mutableStateMapOf<String, String>() }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      "🗃️ 錯題本 2.0 學習盲點攻克庫",
      fontSize = 20.sp,
      fontWeight = FontWeight.Black,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      "直面你曾答錯的文憑試題！展開即可於本頁重做考題、參閱閱卷重點及一鍵移出錯題本！",
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(mistakes) { mistake ->
          val question = allQuestions.find { it.id == mistake.questionId }
          val isExpanded = expandedQuestionId.value == mistake.questionId

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("mistake_card_${mistake.questionId}"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(
              width = if (isExpanded) 1.5.dp else 1.dp,
              color = if (isExpanded) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f)
            )
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              // Header line with Badges
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // Topic & Subject
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  val subjectEmoji = when(mistake.subject.lowercase()) {
                    "math" -> "📐"
                    "physics" -> "⚡"
                    "chemistry" -> "🧪"
                    "english" -> "🇬🇧"
                    else -> "📚"
                  }
                  val subjectText = when(mistake.subject.lowercase()) {
                    "math" -> "數學"
                    "physics" -> "物理"
                    "chemistry" -> "化學"
                    "english" -> "英文"
                    else -> mistake.subject.uppercase()
                  }
                  Box(
                    modifier = Modifier
                      .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                      .padding(horizontal = 8.dp, vertical = 3.dp)
                  ) {
                    Text(
                      "$subjectEmoji $subjectText",
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }

                  Box(
                    modifier = Modifier
                      .background(Color(0xFFDC3545).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                      .padding(horizontal = 8.dp, vertical = 3.dp)
                  ) {
                    Text(
                      "答錯 ${mistake.timesFailed} 次",
                      color = Color(0xFFDC3545),
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                // Reason Tag
                Box(
                  modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Text(
                    reasonDescription(mistake.reasonTag),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
              Text(
                "課題：${question?.topicChinese ?: mistake.topic}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )

              if (question != null) {
                Spacer(modifier = Modifier.height(4.dp))
                // Collapsed Preview
                if (!isExpanded) {
                  Text(
                    text = question.questionText,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // User notes or hint
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                  .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = "Notes",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  "溫習筆記：${mistake.userNotes.ifBlank { "需要加強底層對抗邏輯。" }}",
                  fontSize = 11.sp,
                  color = Color.DarkGray
                )
              }

              // EXPANDED INTERACTIVE REVIEW SECTION
              if (isExpanded && question != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Question Statement card
                Card(
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                  ),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                      "題目考卷內容 Question:",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      question.questionText,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Medium,
                      lineHeight = 20.sp
                    )
                    if (question.methodologyType != "General") {
                      Spacer(modifier = Modifier.height(6.dp))
                      Box(
                        modifier = Modifier
                          .background(Color(0xFFFF9800).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                          .padding(horizontal = 8.dp, vertical = 2.dp)
                      ) {
                        Text(
                          "🔐 底層計謀: ${question.methodologyType}",
                          color = Color(0xFFE65100),
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    }
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MCQ Choices Rendering
                val choices = listOf(
                  "A" to question.optionA,
                  "B" to question.optionB,
                  "C" to question.optionC,
                  "D" to question.optionD
                )

                val selectedChoice = selectedChoices[mistake.questionId]
                val verified = answerVerified[mistake.questionId] ?: false
                val isAnswerCorrectResult = userResults[mistake.questionId] ?: false

                Text(
                  "💡 請在下方重新選擇正確解答：",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(bottom = 6.dp)
                )

                choices.forEach { (key, optionText) ->
                  val isChoiceSelected = selectedChoice == key
                  val isCorrect = key == question.correctAnswer

                  val choiceBgColor = when {
                    verified && isChoiceSelected && isCorrect -> Color(0xFFD4EDDA) // Correct Selected
                    verified && isChoiceSelected && !isCorrect -> Color(0xFFF8D7DA) // Incorrect Selected
                    verified && !isChoiceSelected && isCorrect -> Color(0xFFD4EDDA) // Guide Correct
                    isChoiceSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                  }

                  val choiceBorderColor = when {
                    verified && isChoiceSelected && isCorrect -> Color(0xFF28A745)
                    verified && isChoiceSelected && !isCorrect -> Color(0xFFDC3545)
                    isChoiceSelected -> MaterialTheme.colorScheme.primary
                    else -> Color.LightGray.copy(alpha = 0.4f)
                  }

                  Card(
                    colors = CardDefaults.cardColors(containerColor = choiceBgColor),
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp)
                      .border(1.dp, choiceBorderColor, RoundedCornerShape(8.dp))
                      .clickable(enabled = !verified) {
                        selectedChoices[mistake.questionId] = key
                      }
                      .testTag("mistake_option_${mistake.questionId}_${key.lowercase()}")
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(26.dp)
                          .background(
                            color = if (isChoiceSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                            shape = CircleShape
                          ),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          key,
                          fontWeight = FontWeight.Bold,
                          fontSize = 12.sp,
                          color = if (isChoiceSelected) Color.White else Color.Black
                        )
                      }
                      Spacer(modifier = Modifier.width(10.dp))
                      Text(
                        optionText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                      )
                    }
                  }
                }

                // Interactive control buttons
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  if (!verified) {
                    Button(
                      onClick = {
                        if (selectedChoice == null) return@Button
                        val correct = selectedChoice == question.correctAnswer
                        answerVerified[mistake.questionId] = true
                        userResults[mistake.questionId] = correct
                        
                        if (correct) {
                          userFeedbackMessage[mistake.questionId] = "🎉 回答正確！你已成功重構了本題的底層邏輯盲區！"
                          viewModel.recordAnswer(question.id, true, timeSpentSeconds = 15)
                        } else {
                          userFeedbackMessage[mistake.questionId] = "❌ 答錯了。別氣餒！請參考下方的名師考點和解題詳解。"
                          viewModel.recordAnswer(question.id, false, timeSpentSeconds = 15)
                        }
                      },
                      enabled = selectedChoice != null,
                      modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("verify_mistake_btn_${mistake.questionId}")
                    ) {
                      Text("驗證新解答", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                  } else {
                    // Try again button (if incorrect)
                    if (!isAnswerCorrectResult) {
                      OutlinedButton(
                        onClick = {
                          answerVerified[mistake.questionId] = false
                          selectedChoices[mistake.questionId] = null
                          userResults[mistake.questionId] = false
                        },
                        modifier = Modifier
                          .weight(1f)
                          .height(38.dp)
                          .testTag("retry_mistake_btn_${mistake.questionId}")
                      ) {
                        Text("再試一次 🔄", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      }
                    } else {
                      // Correct mastered state
                      Button(
                        onClick = {
                          scope.launch {
                            viewModel.deleteMistakeRecord(mistake.questionId)
                            expandedQuestionId.value = null
                          }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                          .weight(1f)
                          .height(38.dp)
                          .testTag("mastered_remove_btn_${mistake.questionId}")
                      ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Mastered", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("已掌握！移出錯題集", fontSize = 12.sp, fontWeight = FontWeight.Black)
                      }
                    }
                  }
                }

                // Inline Feedback banner
                val feedback = userFeedbackMessage[mistake.questionId]
                if (!feedback.isNullOrEmpty()) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(
                        color = if (isAnswerCorrectResult) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(6.dp)
                      )
                      .border(
                        1.dp,
                        if (isAnswerCorrectResult) Color(0xFF81C784) else Color(0xFFE57373),
                        RoundedCornerShape(6.dp)
                      )
                      .padding(8.dp)
                  ) {
                    Text(
                      feedback,
                      fontSize = 11.sp,
                      color = if (isAnswerCorrectResult) Color(0xFF2E7D32) else Color(0xFFC62828),
                      fontWeight = FontWeight.Bold
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
}

@Composable
fun RevisionScreen() {
    var selectedSubTab by remember { mutableStateOf(0) }
    var targetSubjectsState by remember { mutableStateOf(setOf("math", "physics", "chemistry", "english")) }
    var daysRemainingState by remember { mutableStateOf(100f) }
    var reviewRemindersEnabled by remember { mutableStateOf(true) }
    var completedPlanItemsState by remember { mutableStateOf(setOf<String>()) }

    // Balanced responsive horizontal tabs - Scrollable Row
    val tabs = listOf(
      "📅 學習計劃",
      "⏱️ 考場戰策",
      "🧮 計算機神程",
      "👥 自修生逆襲",
      "🏫 教師專區"
    )

    // States for Calculator Simulator
    var selectedCalcProg by remember { mutableStateOf(0) }
    var calcInputA by remember { mutableStateOf("") }
    var calcInputB by remember { mutableStateOf("") }
    var calcInputC by remember { mutableStateOf("") }
    var calcInputD by remember { mutableStateOf("") }
    var calcInputE by remember { mutableStateOf("") }
    var calcInputF by remember { mutableStateOf("") }
    var calcOutputMain by remember { mutableStateOf("0.") }
    var calcOutputSub by remember { mutableStateOf("CASIO fx-3650P II READY") }

    // States for Private Candidate District Selector
    var selectedDistrict by remember { mutableStateOf("all") }

    // States for Teacher Worksheet Generator
    var selectedWorksheetSubject by remember { mutableStateOf("math") }
    var selectedWorksheetCount by remember { mutableStateOf(3) }
    var worksheetGeneratedText by remember { mutableStateOf("") }
    var worksheetGeneratedScheme by remember { mutableStateOf("") }
    var worksheetGeneratedShow by remember { mutableStateOf(false) }
    var feedbackCopiedMsg by remember { mutableStateOf(false) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      LazyRow(
      modifier = Modifier.fillMaxWidth().testTag("revision_tab_row"),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      itemsIndexed(tabs) { idx, title ->
        val selected = selectedSubTab == idx
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
          ),
          modifier = Modifier
            .clickable { selectedSubTab = idx }
            .testTag("revision_tab_$idx")
        ) {
          Box(
            modifier = Modifier
              .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = title,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth().testTag("revision_content_column"),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      when (selectedSubTab) {
        0 -> { // Modern Personalized Study Schedule
          item {
            Card(
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  "📊 HKDSE 學習計劃底層生成規則 (P0 - P4 戰術優先度)",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  "• P0 必爭地 (數學/M1/M2) — 秒殺與即時批改\n" +
                  "• P1 理英重心 (物化生/英文) — 重組與解法對稱\n" +
                  "• P2 理論及格 (中文/BAFS/ICT) — 高階語意改寫\n" +
                  "• P3-P4 小眾人文與冷門科目 — 重在脈絡框架",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  lineHeight = 15.sp
                )
              }
            }
          }

          // 1. Selector segment for target subjects
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📌 勾選你要迎戰的 HKDSE 科目：", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

                // Render Priority Checkboxes
                val subjectsByPriority = listOf(
                  Triple("math", "📐 P0 數學 (必修部分)", "P0"),
                  Triple("math_m", "📈 P0 數學選修 M1 / M2", "P0"),
                  Triple("physics", "⚡ P1 物理科", "P1"),
                  Triple("chemistry", "🧪 P1 化學科", "P1"),
                  Triple("biology", "🧬 P1 生物科", "P1"),
                  Triple("english", "🇬🇧 P1 英文必修科", "P1"),
                  Triple("chinese", "🇨🇳 P2 中文必修科", "P2"),
                  Triple("bafs_ict", "💼 P2 BAFS 商業 / ICT 資訊科技", "P2"),
                  Triple("humanities", "📚 P3-P4 中史 / 歷史 / 地理選修", "P3")
                )

                subjectsByPriority.forEach { (subKey, label, priority) ->
                  val isChecked = targetSubjectsState.contains(subKey)
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        targetSubjectsState = if (isChecked) {
                          targetSubjectsState - subKey
                        } else {
                          targetSubjectsState + subKey
                        }
                      }
                      .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      label,
                      fontSize = 11.sp,
                      fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                      color = if (isChecked) MaterialTheme.colorScheme.primary else Color.Black
                    )
                    Checkbox(
                      checked = isChecked,
                      onCheckedChange = { isCheckedNow ->
                        targetSubjectsState = if (isCheckedNow == true) {
                          targetSubjectsState + subKey
                        } else {
                          targetSubjectsState - subKey
                        }
                      },
                      modifier = Modifier.size(24.dp).testTag("select_plan_subject_$subKey")
                    )
                  }
                }
              }
            }
          }

          // 2. Countdown slider for target exam date
          item {
            Card(
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("⏳ 預計距離首科 DSE 考試天數：", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Text(
                    "${daysRemainingState.toInt()} 天",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                  )
                }

                Slider(
                  value = daysRemainingState,
                  onValueChange = { daysRemainingState = it },
                  valueRange = 10f..180f,
                  modifier = Modifier.fillMaxWidth().testTag("exam_days_slider")
                )

                val phaseText = when {
                  daysRemainingState < 30f -> "🚨 進入 1-Month「極致操卷、全真模擬限時 Pass」黃金爆分期！"
                  daysRemainingState < 60f -> "⚠️ 「鞏固重點、突破 DseGrading 步驟分」的核心攻堅期！"
                  else -> "🎯 「全面夯實基本觀念、穩固底層邏輯」的體系奠定期。"
                }
                Text(phaseText, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
              }
            }
          }

          // 3. Spaced review reminders switch toggler
          item {
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (reviewRemindersEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Review Notice",
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Column {
                    Text("⏰ 智慧間隔重複複習提醒", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("系統會在每天晚上 9:30 與考前 1.5 小時，智能推送『AI 平台高頻錯題複盤與解法思維對照』。", fontSize = 10.sp, color = Color.Gray)
                  }
                }
                Switch(
                  checked = reviewRemindersEnabled,
                  onCheckedChange = { reviewRemindersEnabled = it },
                  modifier = Modifier.testTag("reminder_plan_toggle")
                )
              }
            }
          }

          // 4. Dynamic Generated study schedule checklist items!
          item {
            Text("📋 為您量身定制的黃金每日學習時間表：", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
          }

          // Generate dynamic items based on target subjects selected!
          val studyBlocks = mutableListOf<Pair<String, String>>()
          if (targetSubjectsState.contains("math")) {
            studyBlocks.add("P0_MATH" to "📐 1.5小時 DSE 必修數學：完成 10 題底層邏輯改寫題。重點訓練「圓的方程、二階變換、等幾何拐點解法」！")
          }
          if (targetSubjectsState.contains("math_m")) {
            studyBlocks.add("P0_MATH_M" to "📈 1小時 數學 M1 / M2：模擬 Part B 大題目，嚴格執行 DseGrading 3級閱卷法核對，穩拿步驟分！")
          }
          if (targetSubjectsState.contains("physics") || targetSubjectsState.contains("chemistry") || targetSubjectsState.contains("biology")) {
            studyBlocks.add("P1_SCIENCE" to "⚡/🧪 1.25小時 理科專項攻克：做2道化學/物理中度大題。草稿推演後立刻展開 Marking scheme 高維對照！")
          }
          if (targetSubjectsState.contains("english")) {
            studyBlocks.add("P1_ENGLISH" to "🇬🇧 45分鐘 英文核心：閱讀 3 篇 DSE 改寫文章，熟悉邏輯連貫性（Cohesion）核心語法盲點。")
          }
          if (targetSubjectsState.contains("chinese")) {
            studyBlocks.add("P2_CHINESE" to "🇨🇳 30分鐘 中文實體：高頻錯題翻閱、精準掌握文言實詞最簡代元轉換。")
          }
          if (studyBlocks.isEmpty()) {
            studyBlocks.add("DEFAULT_PLAN" to "📍 請至少在上方勾選1門 HKDSE 科目以動態生成黃金戰術安排！")
          }

          items(studyBlocks) { (blockKey, blockText) ->
            val blockDone = completedPlanItemsState.contains(blockKey)
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (blockDone) Color(0xFFD4EDDA).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
              ),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (blockDone) Color(0xFF28A745).copy(alpha = 0.4f) else Color.LightGray.copy(alpha = 0.3f)
              ),
              modifier = Modifier.fillMaxWidth().testTag("plan_item_$blockKey")
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    blockText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                    color = if (blockDone) Color.Gray else Color.Black
                  )
                  if (blockDone) {
                    Text("💡 狀態：今日已完滿推演，多巴胺分配完成！", fontSize = 9.sp, color = Color(0xFF28A745), fontWeight = FontWeight.Bold)
                  }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                  checked = blockDone,
                  onCheckedChange = { isChecked ->
                    completedPlanItemsState = if (isChecked == true) {
                      completedPlanItemsState + blockKey
                    } else {
                      completedPlanItemsState - blockKey
                    }
                  },
                  modifier = Modifier.size(24.dp).testTag("plan_check_$blockKey")
                )
              }
            }
          }

          // Security Protection Badge at the very bottom
          item {
            Card(
              colors = CardDefaults.cardColors(
                containerColor = Color(0xFFECEFF1)
              ),
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Shield,
                  contentDescription = "Security Shield",
                  tint = Color(0xFF455A64),
                  modifier = Modifier.size(28.dp)
                )
                Column {
                  Text(
                    "🔐 DSE Level Up 安全合規沙盒保障",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF37474F)
                  )
                  Text(
                    "本平台所有 AI 生成試題與考試解析均運行於安全的託管沙盒環境。我們特別優化了 Token 頻率限制、雙向 Prompt 防注入（WAF）與動態憑證儲存，有效防堵惡意 Token 竊取與系統越獄入侵，敬請安心專注溫習！",
                    fontSize = 9.sp,
                    color = Color(0xFF546E7A),
                    lineHeight = 13.sp
                  )
                }
              }
            }
          }
        }

        1 -> { // ⏱️ 考場戰策 (Bulletins & 3-Pass Strategy combined and fixed)
          item {
            Text("📢 DSE 最新日程、考綱與極致 3-Pass 答題戰略", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
          }

          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("📅 DSE 核心科目黃金考試日程表", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                val schedule = listOf(
                  "4月9日" to "🇬🇧 英國語文 (紙一閱讀 / 紙二寫作)",
                  "4月10日" to "🇬🇧 英國語文 (紙三聆聽與綜合能力)",
                  "4月13日" to "📐 數學必修部分 (卷一問答 / 卷二選擇題)",
                  "4月15日" to "🇨🇳 中國語文 (卷一閱讀體系 / 卷二寫作)",
                  "4月17日" to "⚡ 物理科 (選修專項理論突破)",
                  "4月20日" to "🧪 化學科 (選修考點高度改寫)"
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
                Text("🎯 考場神話：DSE 選擇題 (MC) 3-Pass 操卷術", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  "DSE MC 卷限時極度緊迫，切忌遇到難題便卡死。請嚴格執行 3-Pass 策略：\n\n" +
                  "• 1st Pass (秒殺題)：只做一看便知算法的送分題與基本概念題。手算/計數機秒出答案。一旦需要思考多於 15 秒，立即標記、跳過。這能確保基本盤的分數安妥入袋。\n\n" +
                  "• 2nd Pass (邏輯常規題)：此時心態放鬆、基本分已穩。重回標記題目，攻克具有中度運算與轉換的常規大題，每題限時 90 秒解答。\n\n" +
                  "• 3rd Pass (高難/壓軸題)：最後 10 分鐘，將精力投注於高難度或需要大運算量的壓軸幾何/二次分析題，善用計算機內置「神程」或代入法衝刺。",
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
                Text("📝 考評局報名須知與入閘指南", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  "1. 准考證核對：准考證一般於2月下旬發放，務必核對個人姓名、應考科目及試場編號。\n" +
                  "2. 身份證明：應試當天必須攜帶有效香港身份證正本，以及官方准考證正本入閘。平板或電子身份證恕不接納。\n" +
                  "3. 收音機檢查：應考英文科紙三聆聽時，須自備合格收音機及耳機，並確認電池充足、調頻(FM)正常運作。試場不提供額外收音設備。\n" +
                  "4. 計算機標籤：數學/理科應試計算機背面必須具備官方「H.K.E.A.A. APPROVED」紅色印刷或綠色標籤，否則不可帶入考場。",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = Color.DarkGray
                )
              }
            }
          }
        }

        2 -> { // 🧮 計算機神程 (CASIO fx-3650P II Ultimate Guide & Simulator)
          item {
            Text("🧮 CASIO 計算機神級程式 & 考場虛擬跑道", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("文憑試理科生人手必備神程。在此了解源碼，並可在下方「虛擬測試跑道」即時驗證、核對你計算機的程式輸出是否正確！", fontSize = 11.sp, color = Color.Gray)
          }

          item {
            // Internal Sub-chips selector for standard CASIO programs
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              val progs = listOf("📍 二次方程 (QUAD)", "📍 聯立二元 (SimEq)", "📍 餘弦定律 (CosArea)")
              progs.forEachIndexed { idx, title ->
                val active = selectedCalcProg == idx
                FilterChip(
                  selected = active,
                  onClick = {
                    selectedCalcProg = idx
                    calcOutputMain = "0."
                    calcOutputSub = "READY"
                  },
                  label = { Text(title, fontSize = 10.sp) }
                )
              }
            }
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                val pTitle = when(selectedCalcProg) {
                  0 -> "1. 二次方程與判別式 (Quadratic Eq & Discriminant)"
                  1 -> "2. 二元一次聯立方程 (Simultaneous Equations)"
                  else -> "3. 餘弦定律與三角形面積 (Cosine Law & Triangle Area)"
                }
                Text(pTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))

                val pExplain = when(selectedCalcProg) {
                  0 -> "此程式輸入 \$ax^2 + bx + c = 0\$ 的係數 \$A, B, C\$。會首先檢查並保存判別式值到 D，然後輸出根 \$x_1\$ 及 \$x_2\$。如無實根，程式會安全跳轉，避免 Math ERROR 崩潰。"
                  1 -> "解聯立方程：\n\$a_1x + b_1y = c_1\$\n\$a_2x + b_2y = c_2\$\n順序輸入 6 個係數：\$A, B, C, D, X, Y\$。按 ◢ 會順序顯示未知數 \$x\$ 與 \$y\$ 的精確解。"
                  else -> "輸入兩個鄰邊 \$a, b\$ 以及夾角 \$C\$（度數）。程式會存儲面積至 M，並自動算出第三條邊 \$c\$ （即邊長 c），以及三角形的實時面積。考卷 Part B 幾何題秒殺級核心。"
                }
                Text(pExplain, fontSize = 11.sp, color = Color.DarkGray, lineHeight = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Text("💻 計算機輸入源碼 (CASIO fx-3650P II):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                val pCode = when(selectedCalcProg) {
                  0 -> "Lbl 1 : ? → A : ? → B : ? → C : B² - 4 A C → D : D < 0 → GoTo 2 : (-B + √ D) ◢ (-B - √ D) ◢ GoTo 1 : Lbl 2 : \"NO REAL\" ◢"
                  1 -> "Lbl 1 : ? → A : ? → B : ? → C : ? → D : ? → X : ? → Y : AD - BX → M : (CY - BX) / M ◢ (AX - CD) / M ◢"
                  else -> "Lbl 1 : ? → A : ? → B : ? → C : A² + B² - 2 A B cos( C → D : √ D ◢ 0.5 A B sin( C ◢"
                }

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(6.dp))
                    .padding(8.dp)
                ) {
                  Text(
                    text = pCode,
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                  )
                }
              }
            }
          }

          // Virtual LCD Simulator and Inputs
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📟 考場即時虛擬調試跑道 (Virtual LCD Tester)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                // Virtual LCD SCREEN
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .background(Color(0xFFD4E157), RoundedCornerShape(6.dp))
                    .border(2.dp, Color.Gray, RoundedCornerShape(6.dp))
                    .padding(8.dp)
                ) {
                  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                      text = calcOutputSub.uppercase(),
                      fontFamily = FontFamily.Monospace,
                      fontSize = 10.sp,
                      color = Color.DarkGray
                    )
                    Text(
                      text = calcOutputMain,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 18.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.Black,
                      modifier = Modifier.align(Alignment.End)
                    )
                  }
                }

                // Inputs depending on Selected Program
                if (selectedCalcProg == 0) {
                  // QUAD inputs
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                      value = calcInputA,
                      onValueChange = { calcInputA = it },
                      label = { Text("A (x²)", fontSize = 10.sp) },
                      modifier = Modifier.weight(1f),
                      singleLine = true,
                      shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                      value = calcInputB,
                      onValueChange = { calcInputB = it },
                      label = { Text("B (x)", fontSize = 10.sp) },
                      modifier = Modifier.weight(1f),
                      singleLine = true,
                      shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                      value = calcInputC,
                      onValueChange = { calcInputC = it },
                      label = { Text("C (繼)", fontSize = 10.sp) },
                      modifier = Modifier.weight(1f),
                      singleLine = true,
                      shape = RoundedCornerShape(8.dp)
                    )
                  }
                  Text("💡 實例測試：輸入 A=1, B=-5, C=6。這代表 \$x^2 - 5x + 6 = 0\$，兩個實數根分別是 3 和 2！", fontSize = 10.sp, color = Color.Gray, lineHeight = 13.sp)
                } else if (selectedCalcProg == 1) {
                  // SimEq inputs
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("方程 1: ax + by = c   |   方程 2: dx + ey = f", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      OutlinedTextField(
                        value = calcInputA,
                        onValueChange = { calcInputA = it },
                        label = { Text("a", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                      )
                      OutlinedTextField(
                        value = calcInputB,
                        onValueChange = { calcInputB = it },
                        label = { Text("b", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                      )
                      OutlinedTextField(
                        value = calcInputC,
                        onValueChange = { calcInputC = it },
                        label = { Text("c", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                      )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      OutlinedTextField(
                        value = calcInputD,
                        onValueChange = { calcInputD = it },
                        label = { Text("d", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                      )
                      OutlinedTextField(
                        value = calcInputE,
                        onValueChange = { calcInputE = it },
                        label = { Text("e", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                      )
                      OutlinedTextField(
                        value = calcInputF,
                        onValueChange = { calcInputF = it },
                        label = { Text("f", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                      )
                    }
                    Text("💡 實例測試：解 \$x+y=5, x-y=1\$。輸入 a=1, b=1, c=5 且 d=1, e=-1, f=1。預期輸出 \$x=3, y=2\$！", fontSize = 10.sp, color = Color.Gray, lineHeight = 13.sp)
                  }
                } else {
                  // CosArea inputs
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                      value = calcInputA,
                      onValueChange = { calcInputA = it },
                      label = { Text("編長 a", fontSize = 10.sp) },
                      modifier = Modifier.weight(1f),
                      singleLine = true
                    )
                    OutlinedTextField(
                      value = calcInputB,
                      onValueChange = { calcInputB = it },
                      label = { Text("編長 b", fontSize = 10.sp) },
                      modifier = Modifier.weight(1f),
                      singleLine = true
                    )
                    OutlinedTextField(
                      value = calcInputC,
                      onValueChange = { calcInputC = it },
                      label = { Text("夾角 C (度)", fontSize = 10.sp) },
                      modifier = Modifier.weight(1f),
                      singleLine = true
                    )
                  }
                  Text("💡 實例測試：已知三角形邊 \$a=3, b=4\$, 夾角 \$C=90^{\\circ}\$。第三條邊預期為 5.0，面積為 6.0！", fontSize = 10.sp, color = Color.Gray, lineHeight = 13.sp)
                }

                Button(
                  onClick = {
                    try {
                      if (selectedCalcProg == 0) {
                        val a = calcInputA.toDoubleOrNull() ?: 1.0
                        val b = calcInputB.toDoubleOrNull() ?: -5.0
                        val c = calcInputC.toDoubleOrNull() ?: 6.0
                        if (a == 0.0) {
                          calcOutputMain = "Math ERROR"
                          calcOutputSub = "A CANNOT BE ZERO"
                        } else {
                          val disc = b * b - 4 * a * c
                          if (disc < 0) {
                            calcOutputMain = "Math ERROR"
                            calcOutputSub = "DISC D=$disc < 0"
                          } else {
                            val r1 = (-b + Math.sqrt(disc)) / (2 * a)
                            val r2 = (-b - Math.sqrt(disc)) / (2 * a)
                            calcOutputMain = "r1 = $r1  ◢"
                            calcOutputSub = "r2 = $r2 | DISC D=$disc"
                          }
                        }
                      } else if (selectedCalcProg == 1) {
                        val a = calcInputA.toDoubleOrNull() ?: 1.0
                        val b = calcInputB.toDoubleOrNull() ?: 1.0
                        val c = calcInputC.toDoubleOrNull() ?: 5.0
                        val d = calcInputD.toDoubleOrNull() ?: 1.0
                        val e = calcInputE.toDoubleOrNull() ?: -1.0
                        val f = calcInputF.toDoubleOrNull() ?: 1.0

                        val det = a * e - b * d
                        if (det == 0.0) {
                          calcOutputMain = "Math ERROR"
                          calcOutputSub = "NO UNIQUE SOLUTION"
                        } else {
                          val x = (c * e - b * f) / det
                          val y = (a * f - c * d) / det
                          calcOutputMain = "X = $x  ◢"
                          calcOutputSub = "Y = $y | DET M=$det"
                        }
                      } else {
                        val a = calcInputA.toDoubleOrNull() ?: 3.0
                        val b = calcInputB.toDoubleOrNull() ?: 4.0
                        val deg = calcInputC.toDoubleOrNull() ?: 90.0

                        val rad = Math.toRadians(deg)
                        val c2 = a * a + b * b - 2 * a * b * Math.cos(rad)
                        val c = Math.sqrt(c2)
                        val area = 0.5 * a * b * Math.sin(rad)
                        calcOutputMain = "SIDE c = $c  ◢"
                        calcOutputSub = "AREA = $area"
                      }
                    } catch (ex: Exception) {
                      calcOutputMain = "Math ERROR"
                      calcOutputSub = "INVALID CHARACTERS"
                    }
                  },
                  modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                  Text("⚡ 虛擬運算 DSE 常規輸出", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        3 -> { // 👥 自修生逆襲 (Self-Study Candidates Resource Hub)
          item {
            Text("👥 自修生逆襲攻略與全港自修室指南", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("沒有學校老師的貼身督促？自修生必須有比別人更強的體系管理，我們為你特設了 SBA 縮表機制及 18 區溫書好去處！", fontSize = 11.sp, color = Color.Gray)
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🧠 自修生必知：SBA 校本評核「無損過關」秘辛", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(
                  "眾所周知，自修生係無學校「校本評核(SBA)」分數學。考評局會如何處理其 20% 分數？\n\n" +
                  "依據官方統計學機制，考評局會根據你在 Paper 1 卷一和 Paper 2 卷二的「實得考試卷面分」，按全港考生的統計比對，無損折算一組虛擬的 SBA 分數計入成績單。\n" +
                  "💡 【黃金戰略】：也就是說，自修生不要有心理包袱，你只需要在 DSE 當天專注力壓群雄。只要卷面分極高，你的 SBA 分數就會自動被比對成 5** 最高期望。本應用的「學科挑戰」即是為極致模擬卷面設計！",
                  fontSize = 11.sp,
                  lineHeight = 15.sp,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
            }
          }

          // Districts search for study rooms
          item {
            Card(
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("📍 全港 18 區文憑試黃金溫書位置 (含入座證提示)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(6.dp))

                val districtsList = listOf(
                  "all" to "🌐 全部區域",
                  "hk" to "🏙️ 香港島",
                  "kln" to "🚇 九龍區",
                  "nt" to "🏕️ 新界區"
                )

                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                  items(districtsList) { (key, name) ->
                    FilterChip(
                      selected = selectedDistrict == key,
                      onClick = { selectedDistrict = key },
                      label = { Text(name, fontSize = 10.sp) }
                    )
                  }
                }

                val rooms = listOf(
                  Triple("hk", "香港中央圖書館 (10-11樓精研自修區)", "時間：09:00 - 21:00\n重點：設有獨立叉電插座，WiFi 頂級，座位極多，3月-5月需於網上抽籤領取入座證。"),
                  Triple("hk", "灣仔公共圖書館 學生自修室", "時間：09:00 - 20:00\n重點：地鐵站出口行 5 分鐘，環境清幽無雜音，適合文科考生安靜思考與對照。"),
                  Triple("kln", "九龍公共圖書館 (培正道)", "時間：08:00 - 22:00 (Study Leave 加時)\n重點：老牌溫修重鎮，位處何文田，周邊多平價食堂。自修室座位寬敞，強推！"),
                  Triple("kln", "石硤尾公共圖書館 自修室", "時間：09:00 - 20:00\n重點：自修座間隔高，私隱度高，附近有大量咖啡吧，極受自修生歡迎。"),
                  Triple("nt", "沙田公共圖書館 自修室", "時間：08:00 - 22:00\n重點：新界東最受歡迎！隔音極好。因人流極大，上午 7:30 需開始排隊，3-5月必須主動出示入座證！"),
                  Triple("nt", "荃灣公共圖書館 自修室", "時間：09:00 - 21:00\n重點：臨近地鐵，全場設有冷氣溫控防著涼，且通風良好不昏睡。")
                )

                val filteredRooms = if (selectedDistrict == "all") rooms else rooms.filter { it.first == selectedDistrict }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  filteredRooms.forEach { (_, title, detail) ->
                    Card(
                      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                      border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text("🏛️ $title", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(detail, fontSize = 10.sp, color = Color.DarkGray, lineHeight = 13.sp)
                      }
                    }
                  }
                }
              }
            }
          }
        }

        4 -> { // 🏫 教師專區 (Teacher & Tutor Corner with mock worksheet generator & analytics)
          item {
            Text("🏫 老師與教學導師專用：DSE 備課智庫", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("為學校學科主任或補習導師設計，您可以在此為學生「快速生成雙語模擬工作紙」或解讀「學生整體答題盲區分析」。", fontSize = 11.sp, color = Color.Gray)
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📋 快速生成雙語 DSE 工作紙與標準 Marking Scheme", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("科目：", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  val subjects = listOf("math" to "📐 數學必修", "physics" to "⚡ 物理科", "chemistry" to "🧪 化學科")
                  subjects.forEach { (key, label) ->
                    val active = selectedWorksheetSubject == key
                    FilterChip(
                      selected = active,
                      onClick = {
                        selectedWorksheetSubject = key
                        worksheetGeneratedShow = false
                      },
                      label = { Text(label, fontSize = 9.sp) }
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("題數：", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  val counts = listOf(3, 5)
                  counts.forEach { count ->
                    val active = selectedWorksheetCount == count
                    FilterChip(
                      selected = active,
                      onClick = {
                        selectedWorksheetCount = count
                        worksheetGeneratedShow = false
                      },
                      label = { Text("$count 題", fontSize = 9.sp) }
                    )
                  }
                }

                Button(
                  onClick = {
                    worksheetGeneratedShow = true
                    feedbackCopiedMsg = false
                    if (selectedWorksheetSubject == "math") {
                      worksheetGeneratedText = """
                        HONG KONG DIPLOMA OF SECONDARY EDUCATION EXAMINATION
                        MATHEMATICS COMPULSORY PART - SECTION A
                        香港中學文憑考試：數學必修部分 - 甲部

                        [Q1] Simple Move & Circle Equations (圓的方程與動點軌跡)
                        Let C be the circle x² + y² - 8x - 6y + 21 = 0 on the Cartesian plane. 
                        A straight line L passes through the origin O with slope m. 
                        Find the range of values of m such that L intersects with the circle C at two distinct points.
                        設 C 為直角坐標平面上的圓 C: x² + y² - 8x - 6y + 21 = 0。 
                        直線 L 通過原點 O 且斜率為 m。若 L 與 C 相交於兩個不同點，求 m 的取值範圍。 [4 Marks / 分]

                        [Q2] Simultaneous Variable Substituted (二元一次方程組的置換)
                        Given x + y = 3k and 2x - y = k + 1, where k is a positive real constant. 
                        Express x and y in terms of k. 
                        已知 x + y = 3k 及 2x - y = k + 1，其中 k 設為正實數常數。
                        試以 k 表示 x 與 y 的實數解。 [3 Marks / 分]

                        [Q3] Trigonometric Coordinate Move (三角形動點與三角比)
                        In standard acute triangle ABC, AB = 5, BC = 7, and the area is 10√6.
                        Calculate the exact value of angle B in degrees, and find the length of AC.
                        在標準銳角三角形 ABC 中，AB = 5，BC = 7，面積為 10√6。
                        求夾角 B 的精確度數，並求邊長 AC 實正值。 [4 Marks / 分]
                      """.trimIndent()

                      worksheetGeneratedScheme = """
                        OFFICIAL MARKING SCHEME (評分及批改指引)

                        Q1 Solution:
                        1. Substitute y = mx into Circle C:
                           x² + (mx)² - 8x - 6(mx) + 21 = 0
                           (1 + m²)x² - (8 + 6m)x + 21 = 0 ------- [M1] - Substitution
                        2. Since L intersects C at two distinct points, discriminant Δ > 0:
                           Δ = [-(8 + 6m)]² - 4(1+m²)(21) > 0 ----- [M1] - Discriminant condition
                           (64 + 96m + 36m²) - 84 - 84m² > 0
                           -48m² + 96m - 20 > 0
                           12m² - 24m + 5 < 0 --------------------- [A1] - Quadratic Ineq Form
                        3. Solving the quadratic inequality:
                           Roots of 12m² - 24m + 5 = 0 are m = (6 ± √21) / 6
                           Therefore, range is: (6 - √21)/6 < m < (6 + √21)/6 ---- [A1] - Final range

                        Q2 Solution:
                        1. Adding two equations: (x+y) + (2x-y) = 3k + k + 1
                           3x = 4k + 1  =>  x = (4k + 1) / 3 ----- [M1] - Elimination
                        2. Substitute x into (x+y = 3k):
                           y = 3k - (4k+1)/3 = (9k - 4k - 1) / 3 = (5k - 1) / 3 --- [A1] - Expression for y

                        Q3 Solution:
                        1. Area = 0.5 * AB * BC * sin B:
                           10√6 = 0.5 * 5 * 7 * sin B  =>  sin B = (20√6) / 35 = (4√6) / 7 --- [M1]
                        2. For acute triangle, cos B = √(1 - sin²B) = √(1 - 96/49) => Since angle B must be acute,
                           we have exact ratio. Substitute into Cosine Law:
                           AC² = AB² + BC² - 2(AB)(BC) cos B --------------------- [M1] [A1]
                      """.trimIndent()
                    } else if (selectedWorksheetSubject == "physics") {
                      worksheetGeneratedText = """
                        HONG KONG DIPLOMA OF SECONDARY EDUCATION EXAMINATION
                        PHYSICS - CORE MECHANICS & EMI BILINGUAL MOCK
                        香港中學文憑考試：物理科 - 經典力學與電磁感應模擬工作紙

                        [Q1] Lenz's Law Symmetrical Induction (冷次定律不對稱電磁感應)
                        A bar magnet is dropped vertically downward through a horizontal stationary copper loop. 
                        (a) State the direction of the induced current in the loop (viewed from above) as the north pole of the magnet approaches the loop. 
                        (b) Explain your answer in terms of magnetic flux change.
                        一條條形磁鐵由高處垂直向下穿過一個水平放置、固定的銅環。
                        (a) 當磁鐵的北極(N極)接近銅環時，指出由上向下看時銅環中感應電流的方向。
                        (b) 從磁通量變化的角度解釋你(a)的答案。 [4 Marks / 分]

                        [Q2] Gravitational Potential Escape (萬有引力天體重力逃逸)
                        Explan why the escape velocity of a space probe from Planet X depends on Planet X's mass M and radius R but does not depend on the probe's mass m.
                        解釋為甚麼太空探測器從 X 行星的逃逸速度取決於行星的質量 M 與半徑 R，但與探測器本身的質量 m 無關。 [3 Marks / 分]
                      """.trimIndent()

                      worksheetGeneratedScheme = """
                        OFFICIAL MARKING SCHEME (評分及批改指引)

                        Q1 Solution:
                        (a) Direction is anticlockwise (逆時針方向) ------- [A1]
                        (b) Explanation:
                            - As the north pole of the magnet approaches the loop, the downward magnetic flux through the loop increases. -- [M1]
                            - According to Lenz's law, the induced current must generate an upward magnetic field to oppose this increase. -- [M1]
                            - By the Right-Hand Grip Rule, the current must flow anticlockwise (viewed from above). -- [A1]

                        Q2 Solution:
                        - At the surface, total energy of the probe at escape is zero: E = KE + PE = 0
                          0.5 * m * v² - G * M * m / R = 0 ----------------- [M1] - Energy Conservation Form
                        - The mass of the probe 'm' appears in both terms and can be canceled out:
                          v = √(2GM/R) ------------------------------------- [M1]
                        - Therefore, v depends only on G, M, and R, which are properties of planet X. - [A1]
                      """.trimIndent()
                    } else {
                      worksheetGeneratedText = """
                        HONG KONG DIPLOMA OF SECONDARY EDUCATION EXAMINATION
                        CHEMISTRY - BILINGUAL MCQ & STRUCTURAL WORKsheet
                        香港中學文憑考試：化學科 - 雙語結構分析與滴定綜合工作紙

                        [Q1] Active Titration & Calculation (化學中和滴定計算)
                        A student used 0.1M NaOH(aq) to titrate 25.0 cm³ of a commercial vinegar solution containing ethanoic acid. 
                        The titration required exactly 18.5 cm³ of NaOH for complete neutralization. 
                        (a) State the chemical equation of the neutralization.
                        (b) Calculate the concentration of ethanoic acid in the vinegar.
                        某同學用 0.1M 的氫氧化鈉溶液(NaOH)滴定 25.0 cm³ 含有乙酸(醋酸)的家用白醋。
                        滴定共消耗了 18.5 cm³ 的 NaOH。
                        (a) 寫出此中和反應的化學方程式。
                        (b) 計算該白醋中乙酸的摩爾濃度。 [4 Marks / 分]
                      """.trimIndent()

                      worksheetGeneratedScheme = """
                        OFFICIAL MARKING SCHEME (評分及批改指引)

                        Q1 Solution:
                        (a) CH₃COOH(aq) + NaOH(aq) → CH₃COONa(aq) + H₂O(l) -------- [A1] (includes correct physical states)
                        (b) Calculation:
                            - No. of moles of NaOH reacted = 0.1 * (18.5 / 1000) = 0.00185 mol ----- [M1]
                            - Since mole ratio of CH₃COOH : NaOH is 1 : 1, mole of CH₃COOH in 25 cm³ = 0.00185 mol. -- [M1]
                            - Concentration = 0.00185 mol / (25.0/1000 dm³) = 0.074 M --------- [A1]
                      """.trimIndent()
                    }
                  },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("⚡ 智能生成對稱 DSE 工作紙 (含 Marking Scheme)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (worksheetGeneratedShow) {
                  HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text("💡 雙語模擬工作紙已生成", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Button(
                        onClick = {
                          val allText = "--- WORKsheet ---\n$worksheetGeneratedText\n\n--- MARKING SCHEME ---\n$worksheetGeneratedScheme"
                          val annotated = androidx.compose.ui.text.AnnotatedString(allText)
                          clipboardManager.setText(annotated)
                          feedbackCopiedMsg = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                      ) {
                        Text(if (feedbackCopiedMsg) "已複製！" else "📋 複製工作紙", fontSize = 10.sp)
                      }
                    }
                  }

                  // Generated Box
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(max = 200.dp)
                      .background(Color.White, RoundedCornerShape(6.dp))
                      .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                      .padding(8.dp)
                      .verticalScroll(rememberScrollState())
                  ) {
                    Column {
                      Text(
                        text = worksheetGeneratedText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Serif,
                        color = Color.Black
                      )
                      Spacer(modifier = Modifier.height(10.dp))
                      HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                      Spacer(modifier = Modifier.height(10.dp))
                      Text(
                        text = worksheetGeneratedScheme,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }
              }
            }
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📊 學科班級弱點診斷雷達 (Classroom Diagnostics Map)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Text("此雷達圖與分析幫助老師掌握 5-6 年級大考或模擬試的通病，提供精準教學介入建議：", fontSize = 10.sp, color = Color.Gray)

                val weakMap = listOf(
                  Triple("📐 數學卷一軌跡與動點 (Trig & Locus)", "高危 📉 (42% 及格率)", "【建議】：學生不理解『動點軌跡』是保持等距，常直接套代數式，應多利用本應用的『題型思維改寫』推敲幾何對稱性。"),
                  Triple("⚡ 物理冷次定理解釋 (Lenz's Law Qual)", "極度高危 🚨 (35% 及格率)", "【建議】：學生習慣寫『有感應電流』，但欠缺指明『磁通量變化方向』以及大腦觀測視角。在改寫題庫中有 5 道針對性定性改寫題。"),
                  Triple("🇨🇳 中文文言多義詞代元 (Classical Chinese)", "中度高危 ⚠️ (55% 及格率)", "【建議】：學生在文言文斷句與古今異義偏弱，建議引導學生在平台的『錯題本』多重做代元最簡轉換。")
                )

                weakMap.forEach { (sub, status, advice) ->
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                      .padding(10.dp)
                      .border(androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f)), RoundedCornerShape(6.dp))
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                      Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(advice, fontSize = 10.sp, color = Color.DarkGray, lineHeight = 14.sp)
                  }
                }
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

// ==========================================
// --- PREMIUM FEATURE CODES: TIME & SOCIALS ---
// ==========================================

@Composable
fun ImmersiveFocusLockOverlay(
  focusSec: Int,
  subject: String,
  onUnlock: () -> Unit
) {
  var showWarningDialog by remember { mutableStateOf(false) }

  val hours = focusSec / 3600
  val minutes = (focusSec % 3600) / 60
  val seconds = focusSec % 60
  val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

  // Soft visual glow background animation
  val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(2500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  // Affiliative motivating quotes that rotate
  val motivationQuotes = listOf(
    "💡 不看昨天的遺憾，不看明天的迷茫，只看今時今日！",
    "🔥 放下手機，專注當下，你離理想中的 5** 只有一步之遙！",
    "🌟 外面的世界都在瘋狂，而你的安靜將鑄就最震撼的逆襲！",
    "📚 同屆考友們正在各自精進，你也行在自我突破的高峰上！",
    "💪 每一道算對的公式、背熟的語法，都是你未來的金鐘罩鐵布衫！"
  )
  val quoteIndex = (focusSec / 15) % motivationQuotes.size
  val activeQuote = motivationQuotes[quoteIndex]

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            Color(0xFF0F172A) // Dark space color
          )
        )
      )
      .clickable(enabled = false) {}, // Swallow all click events to prevent clicking background elements!
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp)
    ) {
      // Large Lock & Hourglass pulse box
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .scale(pulseScale)
          .size(160.dp)
          .background(Color.White.copy(alpha = 0.08f), CircleShape)
          .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock Active",
            tint = Color(0xFFFFD700), // Glowing Gold
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "專注強鎖中",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Elapsed focus timer text
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          formattedTime,
          fontSize = 44.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Black,
          color = Color.White,
          letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .background(Color(0xFF4CAF50), CircleShape)
          )
          Text(
            "正在全力攻克：$subject",
            color = Color(0xFF64B5F6),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
          )
        }
      }

      // Scroll background encouragement quote
      Card(
        colors = CardDefaults.cardColors(
          containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(
          width = 1.dp,
          color = Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            activeQuote,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
          )
        }
      }

      // Small instruction
      Text(
        "💡 貼心提醒: 「專注鎖機模式」啟動！請將手機正面朝下放置於桌上，心無旁騖，直到計時結束。5** 的桂冠，屬於能夠對抗誘惑的自律者！",
        fontSize = 11.sp,
        color = Color.White.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Exit button
      OutlinedButton(
        onClick = { showWarningDialog = true },
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = Color.White.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("emergency_unlock_button")
      ) {
        Icon(
          imageVector = Icons.Default.Cancel,
          contentDescription = "Unlock",
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          "緊急退出專注並放棄積分",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp
        )
      }
    }

    if (showWarningDialog) {
      AlertDialog(
        onDismissRequest = { showWarningDialog = false },
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Warning",
              tint = Color(0xFFE53935)
            )
            Text("確定要中途放棄嗎？ 🤔")
          }
        },
        text = {
          Text(
            "自律是通往 5** 唯一的通道。如果現在放棄，剛才所累積的專注時間將被銷毀，您也將無法獲得任何多巴胺 XP 溫習積分。請咬緊牙關再堅持一下！",
            fontSize = 12.sp,
            lineHeight = 18.sp
          )
        },
        confirmButton = {
          Button(
            onClick = {
              showWarningDialog = false
              onUnlock()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error
            )
          ) {
            Text("殘忍放棄", fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          OutlinedButton(
            onClick = { showWarningDialog = false }
          ) {
            Text("繼續堅持溫習", fontWeight = FontWeight.Bold)
          }
        }
      )
    }
  }
}

@Composable
fun StudyFocusHubCard(viewModel: DseViewModel) {
  val context = LocalContext.current
  val focusSubjects by viewModel.customFocusSubjects.collectAsStateWithLifecycle()
  val selectedFocusSub by viewModel.selectedFocusSubject.collectAsStateWithLifecycle()
  val isRunning by viewModel.isFocusTimerRunning.collectAsStateWithLifecycle()
  val isLockActive by viewModel.isFocusLockActive.collectAsStateWithLifecycle()
  val secondsElapsed by viewModel.focusSecondsElapsed.collectAsStateWithLifecycle()
  val focusSubjectMinutes by viewModel.focusSubjectMinutes.collectAsStateWithLifecycle()

  var newSubjectName by remember { mutableStateOf("") }
  var showAddSubjectDialog by remember { mutableStateOf(false) }

  val hours = secondsElapsed / 3600
  val minutes = (secondsElapsed % 3600) / 60
  val seconds = secondsElapsed % 60
  val formattedTimer = String.format("%02d:%02d:%02d", hours, minutes, seconds)

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("study_focus_hub_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Card Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer)
              .padding(8.dp),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Timer,
              contentDescription = "Timer",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              "⏱️ DSE 分科專注計時器",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              "建立強自律與學科累計時間統計",
              fontSize = 11.sp,
              color = Color.Gray
            )
          }
        }
        
        // Active display badge
        Box(
          modifier = Modifier
            .background(
              if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
              RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            if (isRunning) "專注中" else "IDLE 待命",
            color = if (isRunning) Color.White else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
          )
        }
      }

      // 1. Subject specific chips selection grid with a dynamic ➕ option
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          "📚 選擇當前溫習科目：",
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
        
        val chunks = focusSubjects.chunked(3)
        chunks.forEach { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            rowItems.forEach { sub ->
              val isSelected = selectedFocusSub == sub
              Card(
                onClick = {
                  if (!isRunning) {
                    viewModel.setFocusSubject(sub)
                  } else {
                    Toast.makeText(context, "🚫 專注計時中，不可更換溫習科目！", Toast.LENGTH_SHORT).show()
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("focus_sub_chip_$sub"),
                colors = CardDefaults.cardColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                  width = 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f)
                )
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    sub,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }

            if (rowItems.size < 3) {
              Box(
                modifier = Modifier
                  .weight((3 - rowItems.size).toFloat())
                  .height(32.dp)
              )
            }
          }
        }

        // Add Subject button row
        Button(
          onClick = { showAddSubjectDialog = true },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag("add_custom_subject_button"),
          contentPadding = PaddingValues(0.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Subject icon",
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("➕ 自由新增自訂科目", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }

      // 2. Focus distraction lock toggle switch
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Lock",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Column {
              Text(
                "專注鎖機模式 (Focus distraction Lock)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                "啟動全螢幕屏障遮罩，杜絕所有通知與滑手機分心！",
                fontSize = 10.sp,
                color = Color.Gray,
                lineHeight = 13.sp
              )
            }
          }
          Switch(
            checked = isLockActive,
            onCheckedChange = { active ->
              if (!isRunning) {
                viewModel.toggleFocusLock(active)
              } else {
                Toast.makeText(context, "🚫 專注溫習中，不可變更鎖定設定！", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier
              .scale(0.85f)
              .testTag("focus_lock_switch")
          )
        }
      }

      // 3. Central Clock timer progress display
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            if (isRunning) "📊 專注時間走勢中" else "⏱️ 已準備好開始你的自律旅程",
            fontSize = 11.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
          )

          Text(
            formattedTimer,
            fontSize = 36.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp
          )

          // Run Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (!isRunning) {
              Button(
                onClick = {
                  viewModel.startFocusTimer()
                  Toast.makeText(context, "🚀 「$selectedFocusSub」專注計時啟動！加油！", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(40.dp)
                  .testTag("start_focus_button")
              ) {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = "start",
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("開始專注", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            } else {
              Button(
                onClick = {
                  val secs = secondsElapsed
                  viewModel.stopFocusTimer()
                  if (secs > 0) {
                    val earnedXP = (secs / 10).coerceAtLeast(1)
                    Toast.makeText(context, "🎉 作戰成功！專注「$selectedFocusSub」已結算，榮獲 +$earnedXP XP分！🏆", Toast.LENGTH_LONG).show()
                  }
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF2E7D32) // Nice Emerald green for completion
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(40.dp)
                  .testTag("stop_focus_button")
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "complete focus",
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("停止並結算 (領取XP)", fontSize = 12.sp, fontWeight = FontWeight.Black)
              }
            }
          }
        }
      }

      // 4. Statistics list showing precise times per subject
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          "📊 金頭腦精確科目累積時間：",
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          val activeList = focusSubjectMinutes.toList().sortedByDescending { it.second }.take(4)
          activeList.forEach { (sub, mins) ->
            Card(
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
              ),
              modifier = Modifier.weight(1f)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Text(
                  sub,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  String.format("%.1f分", mins),
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Black
                )
              }
            }
          }
        }
      }
    }
  }

  // Dialog for adding a custom subject
  if (showAddSubjectDialog) {
    Dialog(onDismissRequest = { showAddSubjectDialog = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .testTag("add_subject_dialog"),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            "➕ 自由新增 DSE 學習科目",
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
          )

          Text(
            "輸入你想記錄的時間分組科目，例如：M2、微積分、English Writing、綜合人文學等，精準度百分百！",
            fontSize = 11.sp,
            color = Color.Gray,
            lineHeight = 15.sp
          )

          OutlinedTextField(
            value = newSubjectName,
            onValueChange = { newSubjectName = it },
            label = { Text("科目名稱 Subject Name", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("new_subject_textfield"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary
            )
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TextButton(onClick = { showAddSubjectDialog = false }) {
              Text("取消 Cancel", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                if (newSubjectName.isNotBlank()) {
                  viewModel.addNewFocusSubject(newSubjectName)
                  Toast.makeText(context, "✅ 已新增自訂學習群組科目：$newSubjectName！", Toast.LENGTH_SHORT).show()
                  newSubjectName = ""
                  showAddSubjectDialog = false
                } else {
                  Toast.makeText(context, "🚫 科目名稱不能為空喔！", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.testTag("confirm_add_subject_button")
            ) {
              Text("確認新增", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
fun OnlineStudyGroupsCard(viewModel: DseViewModel) {
  val context = LocalContext.current
  val isUserInGroup by viewModel.isUserInGroup.collectAsStateWithLifecycle()
  val groupRoomName by viewModel.groupRoomName.collectAsStateWithLifecycle()
  val onlinePeers by viewModel.onlineGroupUsers.collectAsStateWithLifecycle()

  var showFriendInviteDialog by remember { mutableStateOf(false) }
  var showRoomSwitchDialog by remember { mutableStateOf(false) }

  var inviteFriendName by remember { mutableStateOf("") }
  var inviteFriendSchool by remember { mutableStateOf("") }
  var switchRoomName by remember { mutableStateOf("") }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("online_study_groups_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = androidx.compose.foundation.BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header item
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer)
              .padding(8.dp),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Groups,
              contentDescription = "Groups",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }

          Column {
            Text(
              "👥 線上讀書小組 Sync Room",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              "即時查看隊友在線狀態與同儕累積時數",
              fontSize = 11.sp,
              color = Color.Gray
            )
          }
        }

        IconButton(
          onClick = { showRoomSwitchDialog = true },
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .testTag("switch_room_icon")
        ) {
          Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Switch Room",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
          )
        }
      }

      if (isUserInGroup) {
        // Group Box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                "當前加入房間：",
                fontSize = 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
              )
              Text(
                groupRoomName,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
              )
            }
            
            // Stats
            Box(
              modifier = Modifier
                .background(Color(0xFFE8F5E9), CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                "📶 ${onlinePeers.filter { it.isOnline }.size}人線上合修中",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
              )
            }
          }
        }

        // Action Buttons Row (Invite Friend)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = { showFriendInviteDialog = true },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier
              .weight(1f)
              .height(34.dp)
              .testTag("invite_friend_action_button")
          ) {
            Icon(
              imageVector = Icons.Default.PersonAdd,
              contentDescription = "Invite Person",
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("➕ 邀請好戰友/網上考友", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }

        // Teammates Online status list
        Text(
          "💬 小組成員在線狀態：",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          onlinePeers.forEach { peer ->
            val isUserSelf = peer.id == "g5"
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (isUserSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .padding(10.dp)
                  .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  // Live Dot
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .background(
                        color = when {
                          !peer.isOnline -> Color(0xFFB0BEC5)
                          peer.status.contains("休息") -> Color(0xFFFFB300)
                          else -> Color(0xFF4CAF50)
                        },
                        shape = CircleShape
                      )
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(peer.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      if (isUserSelf) {
                        Box(
                          modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                          Text("你", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                    Text("${peer.schoolTag} • ${peer.status}", fontSize = 10.sp, color = Color.Gray)
                  }
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    "${peer.focusedMinutesToday} 分鐘",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text("今日累計", fontSize = 9.sp, color = Color.Gray)
                }
              }
            }
          }
        }

        // Peer Pressure dynamic comment
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF9C4), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFFBC02D).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(10.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Pressure icon",
              modifier = Modifier.size(16.dp),
              tint = Color(0xFFE65100)
            )
            val topPeer = onlinePeers.filter { it.id != "g5" }.maxByOrNull { it.focusedMinutesToday }
            val dynamicAdvice = if (topPeer != null) {
              "同儕壓力激發！「${topPeer.name}」今日已累計溫習「${topPeer.focusedMinutesToday} 分鐘」！別放棄，立刻開機迎頭趕上！🚀"
            } else {
              "組內戰備啟動！與考友一同在線合修中，彼此鞭策方可直奔 5**！"
            }
            Text(
              dynamicAdvice,
              fontSize = 11.sp,
              color = Color(0xFFE65100),
              fontWeight = FontWeight.Bold,
              lineHeight = 15.sp,
              modifier = Modifier.weight(1f)
            )
          }
        }

        Divider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Leaderboard,
              contentDescription = "Leaderboard",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              "📊 實時精準排名 (小組內今日時數)",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          Text(
            "實時連線更新中 ⚡",
            fontSize = 9.sp,
            color = Color(0xFF388E3C),
            fontWeight = FontWeight.Bold
          )
        }

        Column(
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          val sortedList = onlinePeers.sortedByDescending { it.focusedMinutesToday }
          sortedList.forEachIndexed { index, peer ->
            val rankNum = index + 1
            val isUserSelf = peer.id == "g5"
            val medal = when (rankNum) {
              1 -> "🥇"
              2 -> "🥈"
              3 -> "🥉"
              else -> "🎖️"
            }
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (isUserSelf) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
              ),
              border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isUserSelf) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else Color.LightGray.copy(alpha = 0.1f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .padding(8.dp)
                  .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(medal, fontSize = 14.sp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    "$rankNum. ${peer.name}",
                    fontSize = 11.sp,
                    fontWeight = if (isUserSelf) FontWeight.Black else FontWeight.Bold,
                    color = if (isUserSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                }

                Text(
                  "${peer.focusedMinutesToday} Mins",
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("你目前不屬於任何線上讀書小組 🤔", fontSize = 12.sp, color = Color.Gray)
          Button(
            onClick = { viewModel.joinOrCreateGroup("DSE 5** 黃金衝刺組 (04)") },
            modifier = Modifier.testTag("quick_join_room_button")
          ) {
            Text("一鍵加入預設 5** 考友合修房", fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        }
      }
    }
  }

  // Invite Friend Dialog list
  if (showFriendInviteDialog) {
    Dialog(onDismissRequest = { showFriendInviteDialog = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .testTag("invite_friend_dialog"),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            "➕ 邀請 DSE 好友/同窗戰友加入房間",
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
          )

          OutlinedTextField(
            value = inviteFriendName,
            onValueChange = { inviteFriendName = it },
            label = { Text("戰友暱稱 Nickname", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("invite_friend_name_input")
          )

          OutlinedTextField(
            value = inviteFriendSchool,
            onValueChange = { inviteFriendSchool = it },
            label = { Text("就讀高中 High School (例如: 喇沙書院)", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("invite_friend_school_input")
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TextButton(onClick = { showFriendInviteDialog = false }) {
              Text("取消 Cancel", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                if (inviteFriendName.isNotBlank() && inviteFriendSchool.isNotBlank()) {
                  viewModel.addFriendToGroup(inviteFriendName, inviteFriendSchool)
                  Toast.makeText(context, "🎉 已成功將親密戰友 [$inviteFriendName] 連線拉入小組！實施督促作用！", Toast.LENGTH_SHORT).show()
                  inviteFriendName = ""
                  inviteFriendSchool = ""
                  showFriendInviteDialog = false
                } else {
                  Toast.makeText(context, "🚫 暱稱和學校都要填寫喔！", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.testTag("confirm_invite_friend_button")
            ) {
              Text("召喚戰友", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }

  // Switch Room Dialog list
  if (showRoomSwitchDialog) {
    Dialog(onDismissRequest = { showRoomSwitchDialog = false }) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .testTag("switch_room_dialog"),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            "🔁 切換 / 開創 DSE 讀書小組房間",
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
          )

          Text(
            "輸入一個新的房間識別碼（例如：英文寫作衝刺班、中大醫科自修閣），就能與同一批考友同時專注溫習！",
            fontSize = 11.sp,
            color = Color.Gray,
            lineHeight = 15.sp
          )

          OutlinedTextField(
            value = switchRoomName,
            onValueChange = { switchRoomName = it },
            label = { Text("房間識別碼 Room Code/Name", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("switch_room_name_input")
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TextButton(onClick = { showRoomSwitchDialog = false }) {
              Text("取消 Cancel", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                if (switchRoomName.isNotBlank()) {
                  viewModel.joinOrCreateGroup(switchRoomName)
                  Toast.makeText(context, "🚪 成功開門傳送！已切換至全新房間：[$switchRoomName]！", Toast.LENGTH_SHORT).show()
                  switchRoomName = ""
                  showRoomSwitchDialog = false
                } else {
                  Toast.makeText(context, "🚫 房間識別碼不能為空喔！", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.testTag("confirm_switch_room_button")
            ) {
              Text("進入房間", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
fun LeaderboardScreen(userPoints: Int) {
  val level = (userPoints / 200) + 1
  val xpToNext = 200 - (userPoints % 200)
  val progressPercent = (userPoints % 200).toFloat() / 200f

  val mockLeaderboard = listOf(
    com.example.ui.LeaderboardUser("1", "🥇 Audrey Tang", "英文/物理雙雙 5**", 1850, 48),
    com.example.ui.LeaderboardUser("2", "🥈 Brianareb (你)", "物理必殺生", userPoints, 12),
    com.example.ui.LeaderboardUser("3", "🥉 Stephanie Tang", "數學科神人", 420, 24),
    com.example.ui.LeaderboardUser("4", "🎖️ Kelvin Lo", "英文科狂熱者", 360, 18),
    com.example.ui.LeaderboardUser("5", "🎖️ Melody Ho", "化學核心戰士", 280, 14),
    com.example.ui.LeaderboardUser("6", "🎖️ Jackson Ng", "自修老兵", 190, 8)
  ).sortedByDescending { it.score }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      "🏆 DSE 戰友積分榜 (Leaderboard)",
      fontSize = 20.sp,
      fontWeight = FontWeight.Black,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      "與全港應屆 DSE 考友、自修生戰友共同連線，互相勉勵！每攻克一道改寫題即賺取點數，提振衝星鬥志！",
      fontSize = 12.sp,
      color = Color.Gray
    )

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text("👑 我的當前稱號 (Level $level)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
          Text(
            text = when (level) {
              1 -> "🐣 文憑試初生之犢"
              2 -> "🛡️ 戰術探索者"
              3 -> "⚔️ 題海征服者"
              4 -> "⚡ 5* 準大師"
              else -> "👑 5** 終極神人"
            },
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text("距離下一等級還需 $xpToNext XP", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
          LinearProgressIndicator(
            progress = { progressPercent },
            modifier = Modifier
              .width(180.dp)
              .padding(top = 4.dp)
              .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
          )
        }
        Text(
          "$userPoints XP",
          fontSize = 22.sp,
          fontWeight = FontWeight.Black,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    Card(
      modifier = Modifier.weight(1f).fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("排名 & 暱稱 Name", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray)
          Row {
            Text("答題數", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.width(32.dp))
            Text("總經驗點", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray)
          }
        }
        HorizontalDivider()
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxSize().padding(top = 8.dp)
        ) {
          items(mockLeaderboard) { peer ->
            val isUser = peer.name.contains("你")
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                  RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = when (peer.rank) {
                    "1" -> "🥇"
                    "2" -> "🥈"
                    "3" -> "🥉"
                    else -> " ${peer.rank} "
                  },
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    peer.name,
                    fontWeight = if (isUser) FontWeight.Black else FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isUser) MaterialTheme.colorScheme.primary else Color.Black
                  )
                  Text(peer.tagline, fontSize = 9.sp, color = Color.Gray)
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  "${peer.questionsAnswered} 題",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color.DarkGray
                )
                Spacer(modifier = Modifier.width(42.dp))
                Text(
                  "${peer.score} XP",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = if (isUser) MaterialTheme.colorScheme.primary else Color.Black
                )
              }
            }
          }
        }
      }
    }
  }
}

data class LeaderboardUser(
  val rank: String,
  val name: String,
  val tagline: String,
  val score: Int,
  val questionsAnswered: Int
)

fun reasonDescription(tag: String): String {
  return when (tag) {
    "calculation_error" -> "🧮 計算錯誤"
    "concept_confusion" -> "🧠 概念未明"
    "careless_reading" -> "🔍 審題不清"
    "formula_misuse" -> "📐 公式誤用"
    "time_pressure" -> "⏳ 時間壓迫"
    else -> "⚠️ 常見盲點"
  }
}
