package com.gzl.aitranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gzl.aitranslator.ui.theme.AiTranslatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val MODEL_NAME = "gemma4_4b.litertlm"

private enum class SettingsPage { List, PromptConfig, ModelPath }

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("主页", Icons.Filled.Home),
    Terminal("命令行翻译", Icons.Filled.Code),
    Settings("设置", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    private var canOverlay by mutableStateOf(false)
    private var modelReady by mutableStateOf(false)
    private var modelError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfig.init(this)
        canOverlay = Settings.canDrawOverlays(this)
        enableEdgeToEdge()

        initModelIfReady()

        setContent {
            AiTranslatorTheme {
                var currentTab by remember { mutableStateOf(Tab.Home) }
                var settingsPage by remember { mutableStateOf(SettingsPage.List) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (currentTab) {
                            Tab.Home -> MainScreen(
                                canOverlay = canOverlay,
                                running = FloatingButtonService.isRunning,
                                modelReady = modelReady,
                                modelError = modelError,
                                onGrant = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
                                onStart = { ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, FloatingButtonService::class.java)) },
                                onStop = { stopService(Intent(this@MainActivity, FloatingButtonService::class.java)) },
                                onRequestStorage = {
                                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                                },
                            )
                            Tab.Terminal -> TerminalScreen(
                                onBack = { currentTab = Tab.Home }
                            )
                            Tab.Settings -> {
                                when (settingsPage) {
                                    SettingsPage.List -> SettingsListScreen(
                                        onNavigateToPrompt = { settingsPage = SettingsPage.PromptConfig },
                                        onNavigateToModelPath = { settingsPage = SettingsPage.ModelPath }
                                    )
                                    SettingsPage.PromptConfig -> PromptConfigScreen(
                                        onBack = { settingsPage = SettingsPage.List }
                                    )
                                    SettingsPage.ModelPath -> ModelPathScreen(
                                        onBack = { settingsPage = SettingsPage.List }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canOverlay = Settings.canDrawOverlays(this)
        if (!modelReady && modelError == null) {
            initModelIfReady()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TranslationManager.close()
        modelReady = false
    }

    private fun initModelIfReady() {
        if (!Environment.isExternalStorageManager()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val err = ensureModelAndInit(this@MainActivity, AppConfig.modelSrcPath) { }
                if (err != null) {
                    modelError = err
                } else {
                    modelReady = true
                    modelError = null
                }
            } catch (e: Exception) {
                modelError = "模型初始化失败: ${e.message}"
            }
        }
    }
}

suspend fun ensureModelAndInit(context: android.content.Context, modelSrc: String, onProgress: (Float) -> Unit): String? {
    if (!Environment.isExternalStorageManager()) return null
    val destFile = File(context.filesDir, MODEL_NAME)
    if (!destFile.exists()) {
        val srcFile = File(modelSrc)
        if (!srcFile.exists()) return "模型文件不存在: $modelSrc\n请将模型推送到手机"
        val totalSize = srcFile.length()
        FileInputStream(srcFile).use { input ->
            FileOutputStream(destFile).use { output ->
                val buf = ByteArray(8192)
                var copied = 0L
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    copied += read
                    onProgress(if (totalSize > 0) copied.toFloat() / totalSize * 100f else 0f)
                }
            }
        }
    }
    onProgress(0f)
    TranslationManager.initialize(destFile.absolutePath)
    return null
}

@Composable
private fun MainScreen(
    canOverlay: Boolean,
    running: Boolean,
    modelReady: Boolean,
    modelError: String?,
    onGrant: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AI 翻译", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // model status
        if (!modelReady && modelError == null) {
            Text("模型加载中...", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        modelError?.let {
            Text("模型错误: $it", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            if (running) "悬浮按钮: 运行中" else "悬浮按钮: 未启动",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(16.dp))

        if (!canOverlay) {
            Text("需要授予悬浮窗权限", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGrant) { Text("开放悬浮窗权限") }
        } else if (running) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("停止悬浮按钮") }
        } else {
            Button(
                onClick = {
                    if (!Environment.isExternalStorageManager()) {
                        onRequestStorage()
                    } else {
                        onStart()
                    }
                },
                enabled = !running
            ) {
                Text("启动悬浮按钮")
            }
        }

        if (!Environment.isExternalStorageManager()) {
            Spacer(Modifier.height(8.dp))
            Text("需要「所有文件访问」权限才能读取模型文件", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequestStorage) { Text("授予文件访问权限") }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "点击悬浮按钮可翻译剪贴板内容",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
