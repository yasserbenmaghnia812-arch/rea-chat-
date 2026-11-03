package com.yasser.reachat

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        REAChatScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun REAChatScreen() {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "مرحباً بك في REA Chat. يمكنك اختيار ملف APK وتجهيز طلب التحليل. خدمة REA السحابية لم تُربط بعد.",
                false
            )
        )
    }
    var draft by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<String?>(null) }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف APK"
            messages.add(ChatMessage("تم اختيار الملف: $selectedName", true))
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("REA Chat", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "مساعد تحليل التطبيقات — نسخة أولية",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    "تنبيه: الواجهة جاهزة للتجربة، لكن الاتصال بخادم REA وتحليل الملفات غير مفعّل بعد.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message -> MessageBubble(message) }
            }

            if (selectedName != null) {
                Text("الملف المحدد: $selectedName", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    apkPicker.launch(
                        arrayOf("application/vnd.android.package-archive", "application/octet-stream")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("اختيار ملف APK")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).padding(12.dp),
                    decorationBox = { innerTextField ->
                        if (draft.isEmpty()) {
                            Text("اكتب رسالتك...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                )
                Button(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            messages.add(ChatMessage(text, true))
                            messages.add(
                                ChatMessage(
                                    "لم يتم إرسال الطلب: خادم REA غير مربوط بهذه النسخة بعد.",
                                    false
                                )
                            )
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank()
                ) {
                    Text("إرسال")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
        }
    }
}
