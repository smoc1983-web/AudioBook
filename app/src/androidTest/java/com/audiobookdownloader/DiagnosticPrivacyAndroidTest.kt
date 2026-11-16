package com.audiobookdownloader
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DiagnosticPrivacyAndroidTest {
 @get:Rule val compose=createComposeRule()
 private val context=ApplicationProvider.getApplicationContext<Context>()
 @Test fun preparedReportIsPreviewedAndExportedWithoutSecrets() {
  val p=context.getSharedPreferences("audiobook_diagnostics",0)
  p.edit().putString("last_search","date\n{\"query\":\"stage4-private-search\",\"sites\":[{\"site\":\"Aume\",\"count\":2,\"error\":\"Cookie: stage4-private-cookie\"}]}").commit()
  try {
   val report=AbCrashReport.read(context,true)!!.second
   assertFalse(report.contains("stage4-private"));assertTrue(report.contains("Aume"))
   val c=Color(0xff222222);val theme=AbTheme(0,"test",false,c,c,c,Color.Cyan,Color.White,Color.LightGray,c,c,Color.Black,c,0)
   compose.setContent{AbCrashReports(theme,1)}
   compose.waitUntil(15000){compose.onAllNodesWithTag("diagnostic_preview").fetchSemanticsNodes().isNotEmpty()}
   val preview=compose.onNodeWithTag("diagnostic_preview").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString {it.text}
   assertFalse(preview.contains("stage4-private"));assertTrue(preview.contains("Версия диагностики"))
   compose.onNodeWithText("Отправить отчёт (.txt)").assertExists()
   val uri=AbDiagnosticReportFiles.create(context,report)
   val exported=context.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()}
   assertEquals(report,exported)
  }finally{p.edit().remove("last_search").commit()}
 }
 @Test fun fileBoundaryAlsoSanitizesExternalDiagnosticText() {
  val uri=AbDiagnosticReportFiles.create(context,"Cookie: a=stage4-secret; b=stage4-other\nhttps://user:stage4-pass@cdn.example/private?token=stage4-token")
  val exported=context.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()}
  assertFalse(exported.contains("stage4-"));assertTrue(exported.contains("cdn.example"))
  val provider=context.packageManager.resolveContentProvider(context.packageName+".diagnostic_reports",0)!!
  assertFalse(provider.exported);assertTrue(provider.grantUriPermissions)
 }
}
