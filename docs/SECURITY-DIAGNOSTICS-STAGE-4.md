# Этап 4 — безопасность и диагностика

Статус: этап4 завершён и проверен. Не опубликовано.
База `49fcd1ccdecc11cedabaab4d51a91596ce9ed740` (этап3), чистое рабочее дерево,
remote refs проверены; рабочая ветка `cloud/security-diagnostics-20261116`.
Разрешены только изменения этапа4. Следующие этапы и публикация не разрешены.

## Диагностика

`DiagnosticPrivacy.java` фильтрует credential-заголовки целиком, поля секретов,
cookies, email, локальные пути и IP, URL credentials/path/query/fragment,
включая экранированные JSON-ссылки. URL заменяется на публичный origin либо
маркер скрытия; подписанные адреса глав не попадают в отчёт. Неизвестные сообщения
исключений и их продолжения исключены: сохраняются тип исключения и stack frames.

`DiagnosticSections.kt` пропускает только согласованные технические числовые/
булевы поля и известные имена источников/состояния, ограничивает вложенность и
массивы. Запросы поиска, примеры книг, имена файлов, произвольные source payload
не экспортируются. Старые записи фильтруются при чтении, новые поисковые записи
уже не сохраняют запросы/примеры. События плеера маскируют книгу и удаление.
Имеющиеся пользовательские настройки/библиотека и старые файлы не очищаются.

`AbCrashReport` применяет границы при сборе и сохранении стека, сохраняя сведения
о сборке, устройстве, типе завершения, PSS/RSS и технические показатели.
Сырые описания ApplicationExitInfo не копируются. Clipboard и TXT FileProvider
имеют дополнительный фильтр. В обычном Compose-окне добавлен выделяемый,
прокручиваемый предпросмотр перед действиями; аварийное Android-окно запуска
уже показывало текст и теперь получает тот же обезличенный отчёт.
Отправка остаётся только явным действием пользователя через системный chooser.
FileProvider остаётся non-exported, доступ даётся только к выбранному URI.

## Сетевые границы

`NetworkAddressRules.java` задаёт общую проверку HTTP(S), authority/port,
credentials, управляющих символов и encoded CR/LF, правил переходов.
Запрещены HTTPS→HTTP и переход публичного адреса на известные локальные/
приватные literal IP/localhost/.local/.internal. Разрешены относительные ссылки,
публичные IPv4/IPv6, обычный HTTP для источников, которым он уже нужен,
переходы на публичные CDN и подписанные параметры **без изменения параметров**.
Referer лишается query/fragment; cross-origin не получает cookies, Authorization
или произвольные пользовательские заголовки. Переносятся только UA, Accept*,
Range, If-Range. Источник-специфичные allowlist и ограничения сохранены.

`SourceHttp.java` заменяет автоматические редиректы у загрузчиков обложек и
публичных метаданных: GET, максимум6 соединений, visited set, timeout,
отмена по interrupted flag, освобождение отвергнутых соединений. HTTP-статус
остаётся доступным вызывающему коду; текущие лимиты тела/кэши не убирались.

Общие проверки добавлены в AbDurationRanges, ContinuationHttp,
SeriesAuthorityHttp, OpenAudioCatalog, Азбуку, внешний playlist JSON,
проверку размера аудио и сравнение адресов WebView-книги. WebView renderer-loss
обработка сохранена. DownloadManager/MediaPlayer и WebView subresources —
платформенные транспорты; их внутренние редиректы этим helper не перехватываются.
DNS pinning не реализован, защиты от DNS rebinding не заявляем: фильтр локальных
адресов проверяет известные имена и literal IP, не произвольные DNS-ответы.

Updater получает дополнительную синтаксическую проверку, сохраняя собственные
строгие GitHub/CDN allowlist, выбор релиза/канала, tag/versionCode/package,
проверки SHA-256/размера и установленной подписи. Общий CDN-допуск источников
никогда не используется вместо updater policy. Секреты в приложение не добавлялись.

## Проверки и журнал

1. Зафиксирована база, создана отдельная ветка, разрешён ub-mode. TokenEconomizer
   CLI status выполнен: sessions0, hooks текущего чата не подтверждены;
   экономия не заявляется, повторное разрешение на CLI не требуется.
2. Зафиксированы red-тесты:4 ожидаемых отказа из6 — текст/JSON privacy и
   malformed/downgrade/local redirect. Созданы минимальные реализационные границы.
3. Введены фильтр, явные редиректы, предпросмотр и тесты на HTTP/session/CDN.
4. Native первая попытка:17/18 прошли, не прошла проверка сохранения имени Aume.
   Причина: browser-only источник не входил в диагностический список известных
   имён. Добавлены именно перечисленные browser-only имена, без допуска произвольных.
5. Тесты уточнили многострочные сообщения и malformed URL с пробелом: исходные
   секреты не должны оставаться в хвосте. Исправлены границы маскирования.
6. Локальный HTTP-сервер подтвердил получение тела после относительного перехода.
   Crapper/Dryer/Mutator запускаются на поддерживаемых новых Java-классах.
   Kotlin ими не поддерживается; его проверки — JVM/Android/компиляция.
7. Мутации выявили непроверенные допустимые публичные literal IP,65535 порт,
   первую позицию CR/LF, numeric aliases и неизвестный IPv6 scope. Добавлены
   реальные граничные случаи. Редундантный широкий запрет `0x*`-имён убран:
   URI-граница и single-label/literal проверки уже отвергают числовые псевдонимы,
   а нормальные CDN-домены с таким префиксом не должны терять поддержку.
8. В старом playback trace свободный текст исключения и разделители внутри
   названия книги дополнительно скрываются; сохранены тип и технические поля.
9. Итоговый [CI43885364170](https://github.com/smoc1983-web/AudioBook/actions/runs/43885364170)
   SUCCESS на исходниках `1cf01f19dbd4b9b461ddf43fe8cc333a49ccc0cd`.
   Stable Debug/unsigned Release, Preview Debug/AndroidTest с существующим signer
   собраны. По326 JVM в Debug/Release:325 исполнены,1 прежний LiveCatalog skip,
   0failures/errors. Native18/18 +2 отдельных instrumentation запуска с реальной
   перезагрузкой эмулятора прошли. Проверены одна передача/файл, позиция и закладка.
   Lint Debug/Release:0Fatal/Error,76Warning/76Information; новых/удалённых
   диагностик относительно этапа3 нет, с учётом смещения строк.
   Сохранённые parser/JS/JVM fixtures прошли. Локально20 новых Java-тестов,
   JaCoCo: maxCRAP8 (<10) на трёх новых классах; Dryer>=0.8:0 кандидатов.
   Mutator83/83 killed,0 survived; реальная JVM-команда, не scan.
   Отчёты: `docs/quality/security-diagnostics/results.json` и логи рядом.
   Устаревшие промежуточные43884619566/43885192980 отменены после запуска
   полного прогона последнего коммита, не выдаются за успешные проверки.

## Изменённые части

- `current/MainActivity.kt`: диагностика, preview, source HTTP call sites.
- Новые `DiagnosticPrivacy.java`, `DiagnosticSections.kt`, `NetworkAddressRules.java`,
  `SourceHttp.java` в `app/src/main/java/com/audiobookdownloader/`.
- `AbDurationRanges.java`, `ContinuationHttp.kt`, `SeriesAuthority.kt`,
  `OpenAudioCatalog.java`, `PreviewUpdateHttp.java`: общие проверки.
- JVM DiagnosticPrivacyTest, DiagnosticSectionsTest, NetworkAddressRulesTest,
  SourceHttpTest; native DiagnosticPrivacyAndroidTest.
- `.github/workflows/security-diagnostics.yml`, quality harness/отчёты и handoff.

## Ограничения

Физическое устройство и установка обновления не проверялись. Все внешние сайты
в сети не проверялись: использованы сохранённые fixtures и локальные/fake HTTP
сценарии. CDN с нестандартным переносом сессионных заголовков требует отдельной
проверки на конкретном источнике; ослаблять общую границу ради него нельзя.
Предпросмотр остаётся необходимым: произвольные сторонние данные не имеют
универсальной гарантии распознавания всех персональных сведений regex-фильтром.
Source JSON и стеки поэтому ограничиваются структурно, а не только regexp.
Защита системного logcat и сторонних приложений не заявляется.

Следующий этап после отдельного подтверждения — уменьшение периодической работы
PlaybackService/виджетов на паузе. Измерений экономии батареи здесь не выполняли.

## Воспроизводимые команды

Локальный harness (нужен `QUALITY_JUNIT_CLASSPATH` JUnit4/Hamcrest):
`bash docs/quality/security-diagnostics/java-tests.sh`.
JaCoCo0.8.12 задаётся `QUALITY_JACOCO_AGENT`/`QUALITY_JACOCO_CLI` и пишет XML в
`target/site/jacoco/jacoco.xml`. Crapper: `--use-existing-coverage --threshold 9`,
Dryer: `--threshold 0.8`, на DiagnosticPrivacy/NetworkAddressRules/SourceHttp.
Mutator: `--no-coverage --max-workers 1 --mutate-all --test-command
"bash docs/quality/security-diagnostics/java-tests.sh"` на этих трёх классах.
Это настоящий запуск тестов на мутантах; `--no-coverage` выбирает все места,
а измерение покрытия выполняется отдельно JaCoCo. Границы CRAP/Dryer относятся
к этим новым поддерживаемым классам, не ко всему историческому Java/Kotlin.

CI Java17/Gradle8.9/SDK36:

```sh
gradle -PaudiobookChannel=stable :app:testDebugUnitTest :app:testReleaseUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --max-workers=2 --console=plain
```

Native Preview: существующий signer, API36 aosp_atd x86_64,18 обычных тестов
(список в workflow) и два отдельных instrumentation запуска с force-stop/reboot.
Локального Android SDK нет: Android-сборки/тесты выполняются именно в CI.
`python3 tools/run_checks.py` — сохранённые parser/JS/JVM fixtures, отдельно от APK.
