package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.DseMainApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DseViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: DseViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        DseMainApp(viewModel = viewModel)
      }
    }
  }
}
