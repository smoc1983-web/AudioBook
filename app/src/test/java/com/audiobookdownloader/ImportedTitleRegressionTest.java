package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class ImportedTitleRegressionTest {
 @Test public void importedLegacySeoTitleWithoutSeparateAuthor() {
  assertTrue(OpenedBookIdentity.matches("Аудиокнига Сыскарь с того света заблудись бесплатно - автор Валерий Пылаев","Сыскарь с того света","","","",null));
 }
 @Test public void importedLegacyRomanVolumeWithoutSeparateAuthor() {
  assertTrue(OpenedBookIdentity.matches("Аудиокнига Новый каменный век. Том VI заблудись бесплатно - автор Лев Белин","Новый каменный век. Том VI","","","",null));
  assertFalse(OpenedBookIdentity.matches("Аудиокнига Новый каменный век. Том VI заблудись бесплатно - автор Лев Белин","Новый каменный век. Том V","","","",null));
 }
 @Test public void conflictingAuthorStillRejected() {
  assertFalse(OpenedBookIdentity.matches("Аудиокнига Сыскарь с того света заблудись бесплатно - автор Валерий Пылаев","Сыскарь с того света","","Другой Автор","",null));
 }
}
