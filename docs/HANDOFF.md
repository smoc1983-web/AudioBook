# Продолжение разработки Audiobook Preview 4.2

Код и история книг находятся только в smoc1983-web/AudioBook; музыкальные изменения — в smoc1983-web/ForTanya. Актуальная ветка книги: main; рабочая ветка integration/verified-sources объединена через PR #1.

Канонические исходники экрана и сервиса: current/MainActivity.kt и current/PlaybackService.kt. Gradle syncCurrentSources копирует их в build/generated перед компиляцией. Дополнительные классы/ресурсы current/app/src/main подключены напрямую; новые адаптеры и совместимость старого виджета — в app/src/main/java/com/audiobookdownloader.

Активные каталоги: LibriVox через Internet Archive и другие аудиокниги Internet Archive. В UnifiedBookSearch работают фоновые HTTP/JSON-запросы. Старые определения адаптеров остаются частью наследуемого файла для истории, но активные списки, поиск, открытие книг и стартовый каталог используют VerifiedBooks/OpenAudioCatalog. Четыре прежних сайта в активный поиск не включаются. Прямые ссылки принимаются только archive.org/details/….

С версии 4.2 приложение собирается как отдельная Audiobook Preview: applicationId com.audiobookdownloader.preview, при сохранённом namespace com.audiobookdownloader. BuildConfig.LIBRARY_DIRECTORY=AudioBooks-Preview. SharedPreferences, файловые каталоги, прогресс и виджеты изолированы от стабильной версии. Темы, картинки жанров и центральная кнопка глав сохранены. Прежний исходник AudioBookWidget не был предоставлен; добавлена рабочая совместимость этого компонента с новым рендерером и сохранением обложек.

Обновления Preview публикуются автоматически после push в main; workflow_dispatch позволяет проверять рабочие ветки без публикации.

Сборка: JDK 17, Gradle 8.9, Android SDK 36; gradle :app:testDebugUnitTest :app:assembleDebug. CI запускает реальную проверку адаптеров с RUN_LIVE_SOURCES=1 и ffmpeg. APK и отчёты Preview: BUILD-4.2-PREVIEW.md; прежняя сборка 4.1 описана отдельно в BUILD-4.1.md. Общий аудит: SOURCE-REPLACEMENT.md.

Исходный ключ стабильной версии неизвестен и для отдельной Preview не требуется. Подпись Preview сохраняется через cache/recovery artifact; для постоянного хранения следует создать secret по docs/PREVIEW-UPDATES.md. Обновления публикуются как закрытые prerelease с APK и preview-update.json. Updater проверяет hash, пакет, версию и текущую подпись; ключ GitHub хранит в Keystore, не в отчётах. Не удалять приложение и не очищать библиотеку. Каталог новых источников отличается от старых; конкретные современные книги могут отсутствовать, написание метаданных может отличаться. На телефоне требуется проверка сценариев из BUILD-4.1.md.

Старый tools/run_checks.py и check_*/test-* сохранены как проверки прежних веб-адаптеров и исторических снимков. Для новой версии обязательны настоящая Gradle-компиляция, OpenAudioCatalogTest и LiveCatalogTest; прежние утверждения о четырёх сайтах больше не описывают активную систему. SearchBookIndexTest и LibraryRecoveryRulesTest отдельно проверены на неизменённых классах.

После успешных проверок сборку GitHub запускать автоматически. О задержках сообщать пользователю. Общаться на русском, выдавать конкретные ссылки на файлы. Старые сведения о неполном Gradle-проекте относятся к версии 4.0; теперь проект собирается целиком.


## Обязательная проверка запуска Android

У Preview 100010 воспроизведён VerifyError в AudioBookApp: операция копирования
ссылки Composer в большом DEX-методе отвергалась ART; кадр имел 267 регистров.
103 локальных делегата mutableStateOf перенесены в remembered AbAppUiState,
с сохранением saveable-состояний и существующих SharedPreferences.
Не возвращайте все эти делегаты в один Composable без проверки на Android.
256 не является общим лимитом DEX: проблема подтверждена конкретным VerifyError.

Начиная с исправления, публикация APK требует connectedDebugAndroidTest на Android 36.
StartupSmokeTest открывает главный экран и библиотеку, пересоздаёт Activity,
проверяет аварийный отчёт и отдельный экран обновлений, затем повторяет запуск.
Сбой аварийного экрана в тесте вызывается намеренно через reflection; его запись
в артефакте не означает неуспешный тест. Ориентируйтесь на JUnit-результат.
В CI освобождается место для раздела userdata Android 36 (нужно около 7.4 ГБ).
Подробности и ссылки: STARTUP-RECOVERY.md.
