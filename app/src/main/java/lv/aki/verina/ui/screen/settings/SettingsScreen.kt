package lv.aki.verina.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import lv.aki.verina.R
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNotificationFilter: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var phonePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var callLogPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var notificationListenerEnabled by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                notificationListenerEnabled = NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        smsPermissionGranted = result[Manifest.permission.RECEIVE_SMS] == true
        phonePermissionGranted = result[Manifest.permission.READ_PHONE_STATE] == true
        callLogPermissionGranted = result[Manifest.permission.READ_CALL_LOG] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = result[Manifest.permission.POST_NOTIFICATIONS] == true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service toggle
            SectionTitle("监听服务")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("事件监听服务", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.isServiceRunning) "运行中" else "已停止",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isServiceRunning)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isServiceRunning,
                        onCheckedChange = { viewModel.toggleService(context) }
                    )
                }
            }

            // Battery optimization
            SectionTitle("电池优化")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (state.isBatteryOptimizationIgnored) "已忽略电池优化" else "未忽略电池优化",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "忽略电池优化可防止系统在后台杀掉 Verina 服务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.isBatteryOptimizationIgnored) {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                viewModel.requestBatteryOptimizationExemption(context)
                            }
                        ) {
                            Text("请求忽略电池优化")
                        }
                    }
                }
            }

            // Permissions
            SectionTitle("权限管理")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermissionItem("短信接收权限", smsPermissionGranted)
                    PermissionItem("电话状态权限", phonePermissionGranted)
                    PermissionItem("通话记录权限", callLogPermissionGranted)
                    PermissionItem("通知权限", notificationPermissionGranted)

                    if (!smsPermissionGranted || !phonePermissionGranted || !callLogPermissionGranted || !notificationPermissionGranted) {
                        Spacer(Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = {
                                val permissions = buildList {
                                    add(Manifest.permission.RECEIVE_SMS)
                                    add(Manifest.permission.READ_PHONE_STATE)
                                    add(Manifest.permission.READ_CALL_LOG)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                permissionLauncher.launch(permissions.toTypedArray())
                            }
                        ) {
                            Text("请求所有权限")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermissionItem("通知读取权限", notificationListenerEnabled)
                    Text(
                        "用于转发其他应用的非持续通知；短信和电话通知也会正常转发。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!notificationListenerEnabled) {
                        FilledTonalButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        ) {
                            Text("授予通知读取权限")
                        }
                    }
                }
            }

            // Notification filter
            if (notificationListenerEnabled) {
                SectionTitle("通知过滤")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToNotificationFilter
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "应用通知过滤",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "选择哪些应用的通知会触发事件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // About
            SectionTitle("关于")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.verina_avatar),
                        contentDescription = "Verina",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    )
                    Text("Verina", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "版本 1.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "事件驱动的自动化工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PermissionItem(name: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (granted) "已授予" else "未授予",
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error
        )
    }
}
