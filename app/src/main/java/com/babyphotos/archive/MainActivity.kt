package com.babyphotos.archive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.babyphotos.archive.ui.navigation.AppNavigation
import com.babyphotos.archive.ui.theme.BabyPhotosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BabyPhotosTheme {
                AppNavigation()
            }
        }
    }
}
