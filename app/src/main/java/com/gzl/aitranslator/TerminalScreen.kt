package com.gzl.aitranslator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TerminalMessage(val role: String, val content: String)

@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val messages = remember { mutableStateListOf<TerminalMessage>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val bgColor = Color(0xFF1E1E1E)
    val promptColor = Color(0xFF00FF00)
    val responseColor = Color(0xFFFFFFFF)
    val inputBg = Color(0xFF2D2D2D)

    Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF333333)).padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Text(
                "命令行翻译",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp).align(androidx.compose.ui.Alignment.CenterVertically)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            state = listState
        ) {
            items(messages) { msg ->
                if (msg.role == "user") {
                    Text(
                        ">>> ${msg.content}",
                        color = promptColor,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Text(
                        msg.content,
                        color = responseColor,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(inputBg).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !loading,
                textStyle = TextStyle(
                    color = promptColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                modifier = Modifier.weight(1f).padding(8.dp),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                ">>> 输入要翻译的文字...",
                                color = Color.Gray,
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isBlank() || loading) return@Button
                    input = ""
                    messages.add(TerminalMessage("user", text))
                    messages.add(TerminalMessage("model", ""))
                    val modelIdx = messages.lastIndex
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val result = StringBuilder()
                            val sendResult = TranslationManager.sendMessageStream(text) { token ->
                                result.append(token)
                                val current = result.toString()
                                withContext(Dispatchers.Main) {
                                    messages[modelIdx] = TerminalMessage("model", current)
                                }
                            }
                            when (sendResult) {
                                SendResult.NotInitialized -> withContext(Dispatchers.Main) {
                                    messages[modelIdx] = TerminalMessage("model", "错误: 模型未就绪")
                                }
                                SendResult.Busy -> withContext(Dispatchers.Main) {
                                    messages[modelIdx] = TerminalMessage("model", "模型资源等待中...")
                                }
                                SendResult.Success -> { /* tokens already streamed */ }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                messages[modelIdx] = TerminalMessage("model", "错误: ${e.message}")
                            }
                        } finally {
                            withContext(Dispatchers.Main) { loading = false }
                        }
                    }
                },
                enabled = !loading
            ) {
                Text(if (loading) "..." else "发送")
            }
        }
    }
}
