package com.audiobookdownloader
import org.junit.Test
import org.junit.Assert.*
class DiagnosticSectionsTest {
 @Test fun oldSearchAndLibraryPayloadsOnlyExposeTechnicalMetrics() {
  val raw="""date
  {"query":"private search","title":"private book","root":"/storage/private",
  "audioPermission":true,"books":3,"sites":[{"site":"Aume","count":2,"error":"cookie secret","examples":[{"title":"private book"}],"url":"https://source/book?token=private"}]}"""
  val s=AbDiagnosticSections.summary(raw,setOf("Aume"))
  assertFalse(s.contains("private"));assertFalse(s.contains("secret"));assertFalse(s.contains("examples"));assertTrue(s.contains("Aume"));assertTrue(s.contains("hasError"));assertTrue(s.contains("3"))
 }
 @Test fun malformedAndUnexpectedStringsDoNotBecomeReports() {
  for(raw in listOf("private unstructured trace","{bad private","{\"books\":\"private title\",\"site\":\"private name\"}")) {
   val s=AbDiagnosticSections.summary(raw,setOf("Aume"));assertFalse(s.contains("private"))
  }
 }
 @Test fun nestedPayloadBudgetAndDeletionTraceDoNotLeakTitles() {
  val s=AbDiagnosticSections.summary("{\"playlistLoad\":{\"httpStatus\":503,\"title\":\"private\"}}",emptySet())
  assertTrue(s.contains("503"));assertFalse(s.contains("private"))
  assertFalse(AbDiagnosticSections.playback("Ошибка перемотки: IOException; private message without labels").contains("private"))
  assertFalse(AbDiagnosticSections.playback("Удаление главы private.mp3: IOException\nplay | книга=private | глава=2").contains("private"))
 }
}
