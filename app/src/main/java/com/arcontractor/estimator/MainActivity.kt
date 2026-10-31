package com.arcontractor.estimator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arcontractor.estimator.ui.ar.ArMeasureScreen
import com.arcontractor.estimator.ui.theme.ArContractorTheme

/**
 * Application entry activity hosting the Phase 2 AR measurement workspace.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArContractorTheme {
                ArMeasureScreen()
            }
        }
    }
}
