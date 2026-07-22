package lv.aki.verina.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import lv.aki.verina.ui.screen.editor.RuleEditorScreen
import lv.aki.verina.ui.screen.home.HomeScreen
import lv.aki.verina.ui.screen.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RULE_EDITOR = "rule_editor/{ruleId}"
    const val SETTINGS = "settings"

    fun ruleEditor(ruleId: Long = -1L) = "rule_editor/$ruleId"
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
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
