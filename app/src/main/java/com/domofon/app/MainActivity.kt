package com.domofon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.domofon.app.ui.DomofonRoot
import com.domofon.app.ui.theme.DomofonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DomofonTheme {
                DomofonRoot()
            }
        }
    }
}
