package com.yasser.reaconsole

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.http.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

@Serializable data class DispatchBody(val ref:String="main", val inputs:Map<String,String>)
@Serializable data class WorkflowRuns(val workflow_runs:List<RunItem> = emptyList())
@Serializable data class RunItem(val id:Long,val status:String,val conclusion:String?=null,val html_url:String="")
@Serializable data class ContentsBody(val message:String,val content:String)
@Serializable data class ArtifactList(val artifacts:List<Artifact> = emptyList())
@Serializable data class Artifact(val id:Long,val name:String,val expired:Boolean=false)

interface GithubApi {
 @PUT("repos/{owner}/{repo}/contents/{path}") suspend fun upload(@Path("owner") owner:String,@Path("repo") repo:String,@Path(value="path",encoded=true) path:String,@Header("Authorization") auth:String,@Body body:ContentsBody):retrofit2.Response<okhttp3.ResponseBody>
 @POST("repos/{owner}/{repo}/actions/workflows/rea-apk-analysis.yml/dispatches") suspend fun dispatch(@Path("owner") owner:String,@Path("repo") repo:String,@Header("Authorization") auth:String,@Body body:DispatchBody):retrofit2.Response<Unit>
 @GET("repos/{owner}/{repo}/actions/workflows/rea-apk-analysis.yml/runs") suspend fun runs(@Path("owner") owner:String,@Path("repo") repo:String,@Header("Authorization") auth:String,@Query("per_page") count:Int=20):WorkflowRuns
 @GET("repos/{owner}/{repo}/actions/runs/{runId}/artifacts") suspend fun artifacts(@Path("owner") owner:String,@Path("repo") repo:String,@Path("runId") runId:Long,@Header("Authorization") auth:String):ArtifactList
}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{ConsoleApp()}}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConsoleApp(){
 val context=LocalContext.current
 val prefs=remember{
  val key=MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
  EncryptedSharedPreferences.create(context,"rea_console_secure",key,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 }
 var token by remember{mutableStateOf(prefs.getString("token","")?:"")}
 var repository by remember{mutableStateOf(prefs.getString("repository","")?:"")}
 var page by remember{mutableIntStateOf(0)}
 var state by remember{mutableStateOf("لم يبدأ أي تحليل")}
 var reportText by remember{mutableStateOf("")}
 var rawReport by remember{mutableStateOf("")}
 var history by remember{mutableStateOf(listOf<String>())}
 var busy by remember{mutableStateOf(false)}
 var workflowUrl by remember{mutableStateOf("")}
 val json=remember{Json{ignoreUnknownKeys=true;isLenient=true}}
 val api=remember{Retrofit.Builder().baseUrl("https://api.github.com/").addConverterFactory(json.asConverterFactory("application/vnd.github+json".toMediaType())).client(OkHttpClient.Builder().build()).build().create(GithubApi::class.java)}
 fun auth()="Bearer ${token.trim()}"
 fun repoParts():Pair<String,String>?{val p=repository.trim().split("/");return if(token.isNotBlank()&&p.size==2&&p.all{it.isNotBlank()})p[0] to p[1] else null}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->
  if(uri!=null){
   val parts=repoParts()
   if(parts==null){state="أدخل Token واسم المستودع الصحيح أولاً"}else{
    val displayName=uri.lastPathSegment?.substringAfterLast('/')?.substringAfter(':')?.ifBlank{"target.apk"}?:"target.apk"
    if(!displayName.endsWith(".apk",true)){state="الملف المحدد ليس APK"}else{
     busy=true;state="جارٍ رفع ملف APK..."
     CoroutineScope(Dispatchers.IO).launch{
      try{
       val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("تعذرت قراءة الملف")
       require(bytes.size<=90*1024*1024){"حجم الملف أكبر من 90 ميغابايت"}
       val path="targets/${UUID.randomUUID()}-${displayName.replace(Regex("[^A-Za-z0-9._-]"),"_")}"
       val put=api.upload(parts.first,parts.second,path,auth(),ContentsBody("Upload authorized APK for REA analysis",Base64.encodeToString(bytes,Base64.NO_WRAP)))
       check(put.isSuccessful){"GitHub رفض رفع الملف (HTTP ${put.code()})"}
       val started=api.dispatch(parts.first,parts.second,auth(),DispatchBody(inputs=mapOf("apk_path" to path)))
       check(started.isSuccessful){"تعذر تشغيل workflow (HTTP ${started.code()}). تحقق من صلاحية Actions"}
       withContext(Dispatchers.Main){state="تم الرفع. ننتظر بدء التحليل..."}
       var run:RunItem?=null
       for(attempt in 0 until 30){
        delay(4000)
        val latest=api.runs(parts.first,parts.second,auth()).workflow_runs.firstOrNull()
        if(latest!=null){
         run=latest
         withContext(Dispatchers.Main){state=translate(latest.status,latest.conclusion);workflowUrl=latest.html_url}
         if(latest.status=="completed") break
        }
       }
       val finished=run
       if(finished!=null&&finished.status=="completed"&&finished.conclusion=="success"){
        val artifact=api.artifacts(parts.first,parts.second,finished.id,auth()).artifacts.firstOrNull{it.name=="rea-results"&&!it.expired}
        if(artifact!=null){
         val req=Request.Builder().url("https://api.github.com/repos/${parts.first}/${parts.second}/actions/artifacts/${artifact.id}/zip").header("Authorization",auth()).header("Accept","application/vnd.github+json").build()
         val response=OkHttpClient.Builder().followRedirects(true).build().newCall(req).execute()
         check(response.isSuccessful){"تعذر تنزيل نتائج التحليل (HTTP ${response.code})"}
         val zip=response.body?.bytes()?:error("ملف النتائج فارغ")
         var packageJson:String?=null
         ZipInputStream(ByteArrayInputStream(zip)).use{z->while(true){val entry=z.nextEntry?:break;if(entry.name.endsWith("package.json")){packageJson=z.readBytes().toString(Charsets.UTF_8);break}}}
         val raw=packageJson?:error("لم يتضمن artifact ملف package.json. افتح help.txt وملفات out لمعرفة السبب.")
         val root=json.parseToJsonElement(raw)
         val pkg=findReportValue(root,setOf("package_name","packagename","package"))?.toString()?.trim('"')?:"راجع تفاصيل التقرير"
         val permissions=findReportValue(root,setOf("permissions"))?.toString()?:"غير متاحة في نتيجة REA"
         val components=findReportValue(root,setOf("components","activities"))?.toString()?:"غير متاحة في نتيجة REA"
         val signatures=findReportValue(root,setOf("signers","signatures","signing_certificates","signingcertificates"))?.toString()?:"راجع قسم التوقيع"
         val prettyJson=Json{prettyPrint=true;ignoreUnknownKeys=true}.encodeToString(JsonElement.serializer(),root)
         val pretty=buildString{
          appendLine("الحزمة: $pkg")
          appendLine()
          appendLine("الصلاحيات:")
          appendLine(permissions)
          appendLine()
          appendLine("الأنشطة والمكوّنات:")
          appendLine(components)
          appendLine()
          appendLine("التوقيعات:")
          appendLine(signatures)
          appendLine()
          appendLine("التقرير الكامل:")
          append(prettyJson)
         }
         withContext(Dispatchers.Main){rawReport=raw;reportText=pretty;state="نجح التحليل وتم استلام التقرير"}
        }else withContext(Dispatchers.Main){state="نجح التحليل، لكن artifact rea-results غير موجود"}
       }else if(finished==null)withContext(Dispatchers.Main){state="لم يظهر تشغيل للتحليل. راجع Actions"}
       withContext(Dispatchers.Main){history=listOf("$displayName — $state") + history}
      }catch(e:Exception){withContext(Dispatchers.Main){state=errorArabic(e);history=listOf("$displayName — فشل: ${errorArabic(e)}")+history}}
      finally{withContext(Dispatchers.Main){busy=false}}
     }
    }
   }
  }
 }
 CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
  Scaffold(topBar={TopAppBar(title={Text("REA Console")})},bottomBar={NavigationBar{
   NavigationBarItem(selected=page==0,onClick={page=0},icon={},label={Text("تحليل")})
   NavigationBarItem(selected=page==1,onClick={page=1},icon={},label={Text("السجل")})
   NavigationBarItem(selected=page==2,onClick={page=2},icon={},label={Text("الإعدادات")})
  }}){padding->Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   when(page){
    0->{
     Text("تحليل تطبيق Android",style=MaterialTheme.typography.headlineSmall)
     Text("اختر APK لرفعه إلى targets/ وتشغيل REA على GitHub Actions، وليس على الهاتف.")
     Button(onClick={picker.launch(arrayOf("application/vnd.android.package-archive","application/octet-stream"))},enabled=!busy&&repoParts()!=null,modifier=Modifier.fillMaxWidth()){Text("اختيار APK وبدء التحليل")}
     if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
     Text("الحالة: $state")
     if(workflowUrl.isNotBlank())TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(workflowUrl)))}){Text("فتح تشغيل GitHub")}
     if(reportText.isNotBlank()){
      HorizontalDivider()
      Text("تقرير package.json",style=MaterialTheme.typography.titleLarge)
      Text(reportText)
      Button(onClick={val i=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"REA package.json").putExtra(Intent.EXTRA_TEXT,rawReport);context.startActivity(Intent.createChooser(i,"مشاركة التقرير"))}){Text("مشاركة ملف JSON الخام")}
     }
    }
    1->{Text("سجل التحليلات",style=MaterialTheme.typography.headlineSmall);LazyColumn{items(history){Text(it,Modifier.fillMaxWidth().padding(10.dp));HorizontalDivider()}}}
    else->{
     Text("الإعدادات",style=MaterialTheme.typography.headlineSmall)
     OutlinedTextField(value=token,onValueChange={token=it},label={Text("GitHub Personal Access Token")},singleLine=true,modifier=Modifier.fillMaxWidth())
     OutlinedTextField(value=repository,onValueChange={repository=it},label={Text("owner/repo")},singleLine=true,modifier=Modifier.fillMaxWidth())
     Button(onClick={prefs.edit().putString("token",token.trim()).putString("repository",repository.trim()).apply();state="تم حفظ الإعدادات محلياً بشكل مشفّر"},modifier=Modifier.fillMaxWidth()){Text("حفظ الإعدادات")}
     Text("Fine-grained token: Contents وActions بصلاحية Read and write. أو Classic token بالنطاقين repo وworkflow.")
     Text("تنبيه: استخدم الأداة لتحليل التطبيقات وملفات APK التي تملكها أو لديك تصريح صريح بفحصها فقط.",color=MaterialTheme.colorScheme.error)
    }
   }
  }}
 }
}
fun translate(status:String,conclusion:String?):String=when{status=="queued"||status=="waiting"->"قيد الانتظار";status=="in_progress"->"جارٍ التنفيذ";status=="completed"&&conclusion=="success"->"نجح";status=="completed"->"فشل";else->"الحالة: $status"}
fun errorArabic(e:Exception):String=when{
 e.message?.contains("401")==true->"رمز GitHub غير صالح أو منتهي الصلاحية."
 e.message?.contains("403")==true->"صلاحيات الرمز غير كافية. فعّل repo وworkflow."
 e.message?.contains("404")==true->"المستودع أو workflow غير موجود."
 e.message?.contains("429")==true->"تجاوزت حد طلبات GitHub؛ أعد المحاولة لاحقاً."
 else->e.message?:"حدث خطأ غير معروف."
}

 
private fun findReportValue(element: JsonElement, keys: Set<String>): JsonElement? {
    return when (element) {
        is JsonObject -> {
            val direct = element.entries.firstOrNull { it.key.lowercase() in keys }?.value
            direct ?: element.values.firstNotNullOfOrNull { findReportValue(it, keys) }
        }
        is JsonArray -> element.firstNotNullOfOrNull { findReportValue(it, keys) }
        else -> null
    }
}
