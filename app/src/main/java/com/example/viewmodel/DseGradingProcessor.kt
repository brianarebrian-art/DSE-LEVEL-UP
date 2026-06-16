package com.example.viewmodel

import com.example.database.QuestionEntity

/**
 * Structured model describing an exam step inside a multi-step Long Question (Part B / Section A2).
 */
data class DseMarkingStep(
  val stepNumber: Int,
  val content: String,
  val formula: String,
  val marks: Int,
  val markType: String, // "M" for Method, "A" for Accuracy, "F" for explanation / full
  val description: String
)

/**
 * Result returned by the automatic grading processor.
 */
data class GradingResult(
  val isCorrect: Boolean,
  val earnedMarks: Int,
  val maxMarks: Int,
  val feedbackTitle: String,
  val feedbackBody: String,
  val errorType: String? = null // e.g., "Sign Error", "Formula Error", "Calculation Error"
)

/**
 * Automatic grading engine and distractor analyzer for HKDSE Level Up tasks.
 */
object DseGradingProcessor {

  /**
   * Evaluates a multiple-choice question selection (MCQ).
   * Incorporates specialized distractor analysis for potential pitfalls of typical students.
   */
  fun evaluateMcq(question: QuestionEntity, selectedChoice: String): GradingResult {
    val isCorrect = selectedChoice.uppercase() == question.correctAnswer.uppercase()
    val maxMarks = question.marks
    val earned = if (isCorrect) maxMarks else 0

    return if (isCorrect) {
      GradingResult(
        isCorrect = true,
        earnedMarks = maxMarks,
        maxMarks = maxMarks,
        feedbackTitle = "🎉 完美解答！",
        feedbackBody = "你成功攻破了「${question.topicChinese}」這道改寫題的底層陷阱。${question.explanationHint}"
      )
    } else {
      // Analyze incorrect option choices based on known algebraic common pitfalls (Distractor Analysis)
      val (errorType, hint) = when (selectedChoice.uppercase()) {
        "A" -> "符號遺漏或平方根極性錯誤" to "常見陷阱：在運算過程中，是否忘記了負號的疊加，或者在二次根式移項時遺失了正負符號？"
        "C" -> "係數比例倒置或分母帶錯" to "常見陷阱：檢查公式中的分子分母關係。例如根之和為 -b/a 是否倒算成 -a/b 或者是係數代入出錯？"
        "D" -> "公式混淆或帶入未簡化值" to "常見陷阱：可能誤用了公式或帶入了中間未經化簡的數值。請特別注意題目的定性限制（例如分點與非零根）。"
        else -> "邏輯推導混淆" to "常見陷阱：可能未看清題目的實體轉換條件，或者在常數計算時出現溢出。"
      }

      GradingResult(
        isCorrect = false,
        earnedMarks = 0,
        maxMarks = maxMarks,
        feedbackTitle = "⚠️ 答錯了！($errorType)",
        feedbackBody = "$hint \n\nDSE 狀元心法：$hint \n建議點擊下方的「AI 考試導師即時解析」以獲取最底層的解題對抗思維。",
        errorType = errorType
      )
    }
  }

  /**
   * Evaluates text based short answer questions with string sanitization, trimming, LaTeX compatibility.
   */
  fun evaluateShortAnswer(question: QuestionEntity, userTypedInput: String): GradingResult {
    val sanitizedInput = userTypedInput.trim().replace("\\s+".toRegex(), "").lowercase()
    val rawAnswer = question.correctAnswer.trim().replace("\\s+".toRegex(), "").lowercase()

    // Also support checking alternate equivalents like fractions vs decimals if matching math pattern
    val isMatch = sanitizedInput == rawAnswer || 
                 (rawAnswer == "b" && sanitizedInput == "10/9") || 
                 (rawAnswer == "c" && (sanitizedInput == "1,0" || sanitizedInput == "(1,0)"))

    val maxMarks = question.marks
    val earned = if (isMatch) maxMarks else 0

    return if (isMatch) {
      GradingResult(
        isCorrect = true,
        earnedMarks = maxMarks,
        maxMarks = maxMarks,
        feedbackTitle = "🎉 填充題解對！",
        feedbackBody = "恭喜你！手動填寫的數學/理科表示式「$userTypedInput」與標準解答完全符合，邏輯完全一致。"
      )
    } else {
      GradingResult(
        isCorrect = false,
        earnedMarks = 0,
        maxMarks = maxMarks,
        feedbackTitle = "❌ 數值或表示式不符",
        feedbackBody = "您輸入的是: 「$userTypedInput」，但標答底層解析為: 「${question.correctAnswer}」或是「${if (question.correctAnswer == "B") "10/9" else "1" }」。請檢查正負號、空格或數值運算是否精緻。"
      )
    }
  }

  /**
   * Generates localized mock marking steps for any question when simulated inside the Long Question UI.
   * Standardizes math explanation steps into method (M) and accuracy (A) mark points based on HKDSE guidelines.
   */
  fun getMarkingStepsForQuestion(question: QuestionEntity): List<DseMarkingStep> {
    return when (question.id) {
      "math_eq_q1" -> listOf(
        DseMarkingStep(
          stepNumber = 1,
          content = "寫出二次方程 3x^2+5x-2=0 的係數並求兩根之和 (α+β)",
          formula = "\\alpha + \\beta = -b/a = -5/3",
          marks = 1,
          markType = "M",
          description = "方法分 (Method Mark)：正確對比係數並套用兩根之和公式。"
        ),
        DseMarkingStep(
          stepNumber = 2,
          content = "求出兩根之積 (αβ) 的值",
          formula = "\\alpha\\beta = c/a = -2/3",
          marks = 1,
          markType = "A",
          description = "答案分 (Accuracy Mark)：求解 c/a 的精準分數。"
        ),
        DseMarkingStep(
          stepNumber = 3,
          content = "計算 (α+β) 與 (αβ) 兩部分的乘積",
          formula = "(-5/3) \\times (-2/3) = 10/9",
          marks = 1,
          markType = "A",
          description = "最終答案分：無懈可擊地求解並求出 10/9。"
        )
      )
      "math_eq_q2" -> listOf(
        DseMarkingStep(
          stepNumber = 1,
          content = "求取原三次函數的二階導數 f''(x)",
          formula = "f''(x) = 6x - 6",
          marks = 2,
          markType = "M",
          description = "微分步：正確推導一階導數 3x^2-6x 與二階導數 6x-6。"
        ),
        DseMarkingStep(
          stepNumber = 2,
          content = "令二階導數為0並解出拐點的 X 座標及 Y 座標",
          formula = "6x - 6 = 0 \\implies x=1 \\implies f(1) = 2",
          marks = 1,
          markType = "M",
          description = "求解步：解得拐點為 (1, 2)。"
        ),
        DseMarkingStep(
          stepNumber = 3,
          content = "根據向下平移 2 單位的定義變更 Y 座標",
          formula = "(1, 2 - 2) = (1, 0)",
          marks = 1,
          markType = "A",
          description = "坐標轉換：得到平移後的最終正確答案 (1, 0)。"
        )
      )
      "math_eq_q3" -> listOf(
        DseMarkingStep(
          stepNumber = 1,
          content = "由兩線垂直斜率性質，設 L1 方程斜率形式",
          formula = "m_1 \\times m_2 = -1 \\implies m_{L1} = 4/3 \\implies L_1: 4x - 3y + C = 0",
          marks = 2,
          markType = "M",
          description = "方法分：正確寫出 L_1 與 3x+4y-12=0 垂直的係數比。"
        ),
        DseMarkingStep(
          stepNumber = 2,
          content = "求得 L1 在坐標軸上的截距並列出三角形面積等式",
          formula = "\\text{Area} = 0.5 \\times |x_{int}| \\times |y_{int}| = 0.5 \\times |-C/4| \\times |C/3| = 6",
          marks = 2,
          markType = "M",
          description = "幾何解析：推導截距公式求得 C^2 = 144。"
        ),
        DseMarkingStep(
          stepNumber = 3,
          content = "求取常數並列出其中一個可能的合規直線方程",
          formula = "C = \\pm 12 \\implies 4x - 3y + 12 = 0",
          marks = 1,
          markType = "A",
          description = "最終解答：運算出合適選項 A 方程。"
        )
      )
      "math_eq_q4" -> listOf(
        DseMarkingStep(
          stepNumber = 1,
          content = "利用第 3 天及第 6 天的荷葉面積建立等比數列方程組",
          formula = "a \\cdot r^2 = 18 \\quad \\text{and} \\quad a \\cdot r^5 = 144",
          marks = 2,
          markType = "M",
          description = "數列建模：正確推廣等比通式 T_n = a \\cdot r^{n-1}。"
        ),
        DseMarkingStep(
          stepNumber = 2,
          content = "聯立方程相除求出公比 r 及基準項 a 的值",
          formula = "r^3 = 8 \\implies r = 2, \\quad a = 4.5",
          marks = 2,
          markType = "A",
          description = "方法/精準度：順利解出公比 2 且基項面積為 4.5。"
        ),
        DseMarkingStep(
          stepNumber = 3,
          content = "根據等比第 8 項求取第 8 天的總面積",
          formula = "T_8 = 4.5 \\times (2)^7 = 576",
          marks = 1,
          markType = "A",
          description = "數值精準分：計算獲得第8天荷葉繁殖面積為 576。"
        )
      )
      else -> listOf(
        DseMarkingStep(
          stepNumber = 1,
          content = "步驟一：解剖物理/化學/英文題目之核心定律",
          formula = "\\text{Concept Base}",
          marks = 1,
          markType = "M",
          description = "理解題目所涉及的公式或語法機制。"
        ),
        DseMarkingStep(
          stepNumber = 2,
          content = "步驟二：套用邊界數值進行精準推演",
          formula = "\\text{Result}",
          marks = question.marks - 1,
          markType = "A",
          description = "執行精準運算並獲得與 DSE 官方標準一致的答案。"
        )
      )
    }
  }
}
