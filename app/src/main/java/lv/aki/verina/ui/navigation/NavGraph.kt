package lv.aki.verina.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import lv.aki.verina.ui.screen.editor.RuleEditorScreen
import lv.aki.verina.ui.screen.failure.WebhookFailureDetailScreen
import lv.aki.verina.ui.screen.failure.WebhookFailureListScreen
import lv.aki.verina.ui.screen.home.HomeScreen
import lv.aki.verina.ui.screen.settings.SettingsScreen
import lv.aki.verina.ui.screen.settings.notification.NotificationFilterScreen

object Routes {
    const val HOME = "home"
    const val RULE_EDITOR = "rule_editor/{ruleId}"
    const val SETTINGS = "settings"
    const val NOTIFICATION_FILTER = "notification_filter"
    const val WEBHOOK_FAILURES = "webhook_failures"
    const val WEBHOOK_FAILURE_DETAIL = "webhook_failure/{recordId}"

    fun ruleEditor(ruleId: Long = -1L) = "rule_editor/$ruleId"
    fun webhookFailureDetail(recordId: Long) = "webhook_failure/$recordId"
}

@Composable
fun VerinaNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToEditor = { ruleId ->
                    navController.navigate(Routes.ruleEditor(ruleId))
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        composable(
            route = Routes.RULE_EDITOR,
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getLong("ruleId") ?: -1L
            RuleEditorScreen(
                ruleId = ruleId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNotificationFilter = {
                    navController.navigate(Routes.NOTIFICATION_FILTER)
                },
                onNavigateToWebhookFailures = {
                    navController.navigate(Routes.WEBHOOK_FAILURES)
                }
            )
        }
        composable(Routes.NOTIFICATION_FILTER) {
            NotificationFilterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.WEBHOOK_FAILURES) {
            WebhookFailureListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { recordId ->
                    navController.navigate(Routes.webhookFailureDetail(recordId))
                }
            )
        }
        composable(
            route = Routes.WEBHOOK_FAILURE_DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) {
            WebhookFailureDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
