# Перенос последней версии

Используйте **current/** целиком. Уже включены оба последних обновления:
девять тем/библиотека/жанры и отдельные режимы поиска.

1. Сделайте резервную копию вашего Android-проекта.
2. `current/MainActivity.kt` → `app/src/main/java/com/MainActivity.kt`.
3. `current/PlaybackService.kt` → `app/src/main/java/com/PlaybackService.kt`.
4. С объединением папок скопируйте `current/app/src/main/java/com/`:
   `AbGlassWidgets.kt`, `SearchBookIndex.java`, `LibraryRecoveryRules.java`.
5. Скопируйте всё содержимое `current/app/src/main/res/`.
6. Объедините debug/release-манифесты из current/app/src/ со своими:
   сохраните старые компоненты и добавьте объявления виджетов, конфигуратора,
   FileProvider диагностики, READ_MEDIA_AUDIO и READ_EXTERNAL_STORAGE
   с maxSdkVersion=32. Основной манифест проекта не заменяется.
7. Сохраните старый `AudioBookWidget.kt`, его ресурсы и текущие Gradle-файлы.
8. Выполните `:app:assembleDebug` в существующем проекте.
9. Обновите установленный APK с прежним applicationId и подписью.

Удаление приложения и очистка данных для обновления не требуются.
Если Android отозвал доступ к старым аудиофайлам, выдайте разрешение через
«Книги → Синхронизация» или выберите папку Download/AudioBooks.

Ревизия в диагностике после установки: `4.0-search-modes-1`.

Текстовые и ресурсные проверки не подтверждают Android-компиляцию,
слияние манифестов и фактическое воспроизведение на телефоне.
