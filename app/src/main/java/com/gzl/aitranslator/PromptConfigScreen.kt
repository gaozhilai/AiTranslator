package com.gzl.aitranslator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PromptConfigScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var prompt by remember { mutableStateOf(AppConfig.translationPrompt) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "翻译 Prompt 配置",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "使用 {text} 作为待翻译文本的占位符",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { newValue ->
                prompt = newValue
                AppConfig.translationPrompt = newValue
            },
            label = { Text("Prompt 模板") },
            modifier = Modifier.fillMaxSize().weight(1f),
            maxLines = 20,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                AppConfig.resetPrompt()
                prompt = AppConfig.translationPrompt
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("恢复默认 Prompt")
        }
    }
}
