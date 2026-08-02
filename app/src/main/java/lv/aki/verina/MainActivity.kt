package lv.aki.verina

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import lv.aki.verina.ui.navigation.Routes
import lv.aki.verina.ui.navigation.VerinaNavGraph
import lv.aki.verina.ui.theme.VerinaTheme

class MainActivity : ComponentActivity() {
    private val failureRecordId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            VerinaTheme {
                val navController = rememberNavController()
                VerinaNavGraph(navController = navController)
                val recordId by failureRecordId
                LaunchedEffect(recordId) {
                    recordId?.let { id ->
                        navController.navigate(Routes.webhookFailureDetail(id)) {
                            launchSingleTop = true
                        }
                        failureRecordId.value = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_FAILURE_RECORD_ID, -1L) ?: -1L
        failureRecordId.value = id.takeIf { it >= 0L }
    }

    companion object {
        const val EXTRA_FAILURE_RECORD_ID = "webhook_failure_record_id"
    }
}
