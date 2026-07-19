package com.example.fakenews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.fakenews.ui.navigation.AppNavHost
import com.example.fakenews.ui.theme.NewsAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewsAppTheme {
                AppNavHost()
            }
        }
    }
}
//현재 삼성전자 주가 40만원 돌파
//삼성전자 월드컵