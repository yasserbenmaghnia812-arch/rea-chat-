package com.yasser.reachat

import android.content.ContentResolver
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ReaApi {
    suspend fun analyze(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            val boundary = "REA-${UUID.randomUUID()}"
            val connection = (URL("http://127.0.0.1:8765/analyze").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 180000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"sample.apk\"\r\n".toByteArray())
                output.write("Content-Type: application/vnd.android.package-archive\r\n\r\n".toByteArray())
                contentResolver.openInputStream(uri)?.use { input -> input.copyTo(output) }
                    ?: throw IllegalStateException("تعذر قراءة ملف APK")
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            connection.disconnect()
            if (code in 200..299) body else "فشل التحليل (HTTP $code): $body"
        } catch (e: Exception) {
            "تعذر الاتصال بمحرك REA المحلي. تأكد من تشغيل خادم Termux على المنفذ 8765. التفاصيل: ${e.message}"
        }
    }
}
