package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class QuestionEntity(
  @PrimaryKey val id: String,
  val subject: String, // math, physics, chemistry, english
  val topic: String,
  val topicChinese: String,
  val difficulty: String, // Easy, Medium, Hard
  val questionText: String,
  val optionA: String,
  val optionB: String,
  val optionC: String,
  val optionD: String,
  val correctAnswer: String, // A, B, C, D
  val explanationHint: String,
  val explanationDetailed: String,
  val methodologyType: String, // Transformation Thinking, Rate of Change, Condition Decomposition, Modeling Ability, General
  val stepNotes: String, // Step by step or general translation details
  val marks: Int,
  val youtubeUrl: String,
  val originalRef: String
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
  @PrimaryKey val id: String = "main_user",
  val dailyStreak: Int = 0,
  val lastActiveTimestamp: Long = 0,
  val scorePoints: Int = 0,
  val totalQuestionsJoined: Int = 0,
  val totalCorrectAnswers: Int = 0
)

@Entity(tableName = "mistakes")
data class MistakeEntity(
  @PrimaryKey val questionId: String,
  val subject: String,
  val topic: String,
  val reasonTag: String, // Carelessness, Concept Gap, Calculation Error, Time Pressure
  val timestamp: Long,
  val timesFailed: Int = 1,
  val userNotes: String = ""
)

@Entity(tableName = "completed_questions")
data class CompletedQuestionEntity(
  @PrimaryKey val id: String, // questionId
  val subject: String,
  val timestamp: Long,
  val isCorrect: Boolean,
  val timeSpentSeconds: Int = 0,
  val scoreEarned: Int = 0
)

@Entity(tableName = "unlocked_badges")
data class BadgeEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String,
  val iconName: String,
  val unlockTimestamp: Long
)

@Entity(tableName = "past_paper_resources")
data class PastPaperResourceEntity(
  @PrimaryKey val id: String, // e.g. "2024_math_p1"
  val year: String, // "2024", "2023", etc.
  val subject: String, // "math", "english", "physics", "chemistry", "chinese", "biology"
  val paperType: String, // "mc", "lq", "listening"
  val title: String,
  val titleChinese: String,
  val fileSize: String,
  val downloadCount: Int,
  val syllabusKeypoints: String,
  val isDownloaded: Boolean = false,
  val localFilePath: String? = null
)
