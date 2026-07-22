package lv.aki.verina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import lv.aki.verina.ui.navigation.VerinaNavGraph
import lv.aki.verina.ui.theme.VerinaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VerinaTheme {
                val navController = rememberNavController()
                VerinaNavGraph(navController = navController)
            }
        }
    }
}
