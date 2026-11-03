package com.yasser.reachat

import android.net.Uri
import android.os.Bundle
import android.content.ContentResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF081522)
private val Panel = Color(0xFF102438)
private val PanelAlt = Color(0xFF152D43)
private val Cyan = Color(0xFF38D6D0)
private val Muted = Color(0xFF9CB1C3)
private val White = Color(0xFFF3F8FC)

private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    onPrimary = Ink,
                    secondary = Color(0xFF79A9FF),
                    background = Ink,
                    surface = Panel,
                    surfaceVariant = PanelAlt,
                    onSurface = White,
                    onSurfaceVariant = Muted
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        REAChatScreen(contentResolver = this@MainActivity.contentResolver)
                    }
                }
            }
        }
    }
}

@Composable
private fun REAChatScreen(contentResolver: ContentResolver) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "أهلاً بك. اختر ملف APK تملك حق تحليله، ثم اكتب ما تريد فحصه. ستظهر نتائج التحليل هنا بعد ربط خادم REA.",
                false
            )
        )
    }
    var draft by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف APK"
            messages.add(ChatMessage("تم اختيار الملف: $selectedName", true))
        }
    }

    Scaffold(containerColor = Ink) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header()

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ابدأ تحليل تطبيقك", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "ارفع ملف APK وحدد نوع الفحص المطلوب. لا يتم رفع الملف حالياً لأن الخادم لم يُربط بعد.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FeatureTag("APK")
                        FeatureTag("JADX")
                        FeatureTag("REA")
                    }
                    OutlinedButton(
                        onClick = {
                            apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("＋   اختيار ملف APK", fontWeight = FontWeight.SemiBold)
                    }
                    if (selectedName != null) {
                        Text("الملف المحدد: $selectedName", color = Cyan, fontSize = 12.sp)
                        Button(onClick = { val uri = selectedUri; if (uri != null && !busy) { busy = true; CoroutineScope(Dispatchers.Main).launch { val result = withContext(Dispatchers.IO) { ReaApi.analyze(contentResolver, uri) }; messages.add(ChatMessage(result, false)); busy = false } } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "جارٍ التحليل..." else "بدء التحليل الفعلي") }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("المحادثة", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Text("سجل الطلبات", color = Muted, fontSize = 12.sp)
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message -> MessageBubble(message) }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).padding(10.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) Text("اكتب طلب التحليل...", color = Muted, fontSize = 14.sp)
                            inner()
                        }
                    )
                    Button(
                        onClick = {
                            val value = draft.trim()
                            if (value.isNotEmpty()) {
                                messages.add(ChatMessage(value, true))
                                messages.add(ChatMessage("تم حفظ طلبك داخل الواجهة. الإرسال والتحليل الفعلي سيتاحان بعد إعداد الخادم الآمن.", false))
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)
                    ) {
                        Text("إرسال")
                    }
                }
            }
            Text(
                "حلّل التطبيقات التي تملكها أو لديك إذن بفحصها فقط.",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).background(PanelAlt, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("R", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text("REA Chat", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("مساعد الهندسة العكسية", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.background(Color(0xFF382B1C), CircleShape).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(Color(0xFFFFBE62), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("غير متصل", color = Color(0xFFFFD49A), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FeatureTag(label: String) {
    Box(Modifier.background(PanelAlt, RoundedCornerShape(9.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
        Text(label, color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromUser) Color(0xFF16414B) else PanelAlt
            )
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = White,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}
