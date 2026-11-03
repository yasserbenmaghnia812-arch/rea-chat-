package com.yasser.reachat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private val TerminalGreen = Color(0xFF71E6A2)
private val TerminalBg = Color(0xFF050B10)

@Composable
fun TerminalPanel(appDirectory: File) {
    val scope = rememberCoroutineScope()
    val lines = remember {
        mutableStateListOf(
            "REA Chat Android Shell",
            "هذه طرفية Android حقيقية تعمل بصلاحيات التطبيق، وليست بيئة Linux كاملة.",
            "اكتب help لعرض الأوامر المقترحة."
        )
    }
    var command by remember { mutableStateOf("") }
    var currentDirectory by remember { mutableStateOf(appDirectory) }
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Terminal • ${currentDirectory.absolutePath}",
            color = TerminalGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TerminalBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(lines) { line ->
                    Text(
                        line,
                        color = if (line.startsWith("$")) TerminalGreen else Color(0xFFD6E2EA),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !running,
                label = { Text("أدخل أمرًا") }
            )
            Button(
                enabled = command.isNotBlank() && !running,
                onClick = {
                    val entered = command.trim()
                    command = ""
                    lines.add("$ ${entered}")
                    if (entered == "clear") {
                        lines.clear()
                    } else if (entered == "help") {
                        lines.add("أوامر مقترحة: pwd, ls, whoami, date, id, getprop, df, cd <path>, clear")
                        lines.add("تنفذ الأوامر داخل Android shell وبصلاحيات التطبيق فقط.")
                    } else {
                        running = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                executeAndroidCommand(entered, currentDirectory, appDirectory)
                            }
                            currentDirectory = result.first
                            lines.addAll(result.second)
                            if (lines.size > 500) {
                                repeat(lines.size - 500) { lines.removeAt(0) }
                            }
                            running = false
                        }
                    }
                }
            ) {
                Text(if (running) "..." else "تنفيذ")
            }
        }
        Text(
            "لا يوجد Root. أوامر النظام الحساسة تظل محكومة بصلاحيات Android.",
            color = Color(0xFF9CB1C3),
            fontSize = 10.sp
        )
    }
}

private fun executeAndroidCommand(
    input: String,
    currentDirectory: File,
    appDirectory: File
): Pair<File, List<String>> {
    if (input == "pwd") return currentDirectory to listOf(currentDirectory.absolutePath)
    if (input.startsWith("cd")) {
        val target = input.removePrefix("cd").trim().ifEmpty { appDirectory.absolutePath }
        val candidate = if (target.startsWith("/")) File(target) else File(currentDirectory, target)
        val canonical = runCatching { candidate.canonicalFile }.getOrElse { currentDirectory }
        return if (canonical.isDirectory) canonical to listOf("المجلد الحالي: ${canonical.absolutePath}")
        else currentDirectory to listOf("cd: المجلد غير موجود")
    }

    return try {
        val outputFile = File.createTempFile("rea-shell-", ".log", appDirectory)
        val process = ProcessBuilder("/system/bin/sh", "-c", input)
            .directory(currentDirectory)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .start()
        val completed = process.waitFor(30, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
        }
        val output = outputFile.readLines().take(300)
        outputFile.delete()
        if (!completed) {
            currentDirectory to (output + "انتهت مهلة الأمر (30 ثانية).")
        } else {
            currentDirectory to (if (output.isEmpty()) listOf("اكتمل الأمر، رمز الخروج: ${process.exitValue()}") else output)
        }
    } catch (error: Exception) {
        currentDirectory to listOf("خطأ: ${error.message ?: "تعذر تنفيذ الأمر"}")
    }
}
