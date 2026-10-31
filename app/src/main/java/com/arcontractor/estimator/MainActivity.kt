package com.arcontractor.estimator

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arcontractor.estimator.ui.ar.ArMeasureScreen
import com.arcontractor.estimator.ui.quote.QuoteAction
import com.arcontractor.estimator.ui.quote.QuoteScreen
import com.arcontractor.estimator.ui.quote.QuoteViewModel
import com.arcontractor.estimator.ui.theme.ArContractorTheme

/**
 * Application entry activity hosting the AR measurement and quote workspaces.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EstimatorApplication).container
        setContent {
            ArContractorTheme {
                EstimatorApp(container)
            }
        }
    }
}

@Composable
private fun EstimatorApp(container: AppContainer) {
    val navController = rememberNavController()
    val quoteViewModel: QuoteViewModel = viewModel(
        factory = QuoteViewModel.Factory(
            projectRepository = container.projectRepository,
            materialCatalogRepository = container.materialCatalogRepository,
            quotePdfExporter = container.quotePdfExporter
        )
    )
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = QUOTE_ROUTE
    ) {
        composable(QUOTE_ROUTE) {
            QuoteScreen(
                viewModel = quoteViewModel,
                onMeasureSurface = {
                    navController.navigate(MEASURE_ROUTE) {
                        launchSingleTop = true
                    }
                },
                onSharePdf = { result ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, result.contentUri)
                        clipData = ClipData.newRawUri("Contractor quote PDF", result.contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share contractor quote")
                    )
                }
            )
        }
        composable(MEASURE_ROUTE) {
            ArMeasureScreen(
                onSaveMeasurement = { capture ->
                    quoteViewModel.onAction(QuoteAction.SaveMeasurement(capture))
                    navController.navigate(QUOTE_ROUTE) {
                        popUpTo(MEASURE_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBackToQuote = {
                    navController.popBackStack(QUOTE_ROUTE, inclusive = false)
                }
            )
        }
    }
}

private const val QUOTE_ROUTE = "quote"
private const val MEASURE_ROUTE = "measure"
