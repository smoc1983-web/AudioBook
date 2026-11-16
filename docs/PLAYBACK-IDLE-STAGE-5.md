# Этап 5 — периодическая работа плеера и виджетов

Статус: этап5 завершён и проверен, не опубликовано.
База `60a952aa14e103dc7f3391ea6fcbb35b3c77d4e1` (этап4), чистое дерево;
remote refs сверены, рабочая ветка `cloud/playback-idle-20261116`.
Изменения только этапа5; этап6 и публикация не разрешены.

## Подтверждённая причина и исправление

PlaybackService до изменения безусловно повторял handler callback каждые500мс,
включая паузу без таймера: readPosition, volume, shake, publish, notify до1раза/с.
updateSessionState дополнительно отправлял тот же widget snapshot через
совместимый AudioBookWidget, хотя publish уже обращался к AbGlassWidgets.
Виджеты переобновляли неизменное состояние каждые5с, а одиночный seek на паузе
мог потеряться за ограничителем5с без последующего periodic callback.

PlaybackPulseRules задаёт решения в одном месте:

| Состояние | Новый callback |
|---|---|
| Остановленный сервис | Не планируется |
| Пауза/пустой плеер без часового таймера | Не планируется |
| Воспроизведение или подготовка | До500мс, сохраняя прежний cadence |
| Пауза с часовым таймером | До1000мс и не позже ближайшего дедлайна |
| Таймер уже истёк |1мс для обработки, без повторяющегося idle loop |

Сохранение позиции каждые5с во время воспроизведения осталось; pause/seek/
смена главы по-прежнему сохраняют состояние сразу. readPosition и volume
в periodic callback выполняются только при воспроизведении. Shake-подписка
обновляется при publish/событиях; pause снимает датчик и CPU lock.
До конца главы таймер действует через существующий completion callback;
на паузе ему не нужен часовой опрос. События, focus, MediaSession и команды
по-прежнему вызывают publish. Единственный handler pulse не отодвигается
последующими событиями на более позднее время; idle/stop его снимают.

AbGlassWidgets различает события и periodic progress. Неизменный snapshot
не порождает рендер; переходы и explicit seek/timer/settings обновляются сразу,
periodic position — с прежним ограничением5с. Совместимая entry point
AudioBookWidget сохранена, повторный вызов из updateSessionState убран.
Host update/options/theme и ручной refresh продолжают работать независимо
от limiter. Backoff30с при отсутствии установленных виджетов сохранён.

Два события заменяют прежнее ожидание polling:
- Сохранение обложки уведомляет живой сервис: сбрасываются кеш картинки/metadata,
  обновляются notification, MediaSession и виджеты. Новый сервис не запускается.
- Поздние duration preferences на паузе объединяются100мс и обновляют виджет
  с учётом backoff при отсутствии виджетов. Постоянного metadata polling нет.

Пакет, versionName/versionCode, signer, ID книг/глав, позиции, закладки,
скачанные файлы, коллекции и макеты интерфейса не менялись. Updater не изменялся.

## Файлы

- `current/PlaybackService.kt`: event scheduling, pause/timer pulse, artwork event,
  one-shot metadata refresh, removal of duplicate widget publication.
- `current/app/src/main/java/com/AbGlassWidgets.kt`: changed-snapshot gate,
  periodic/event distinction, metadata refresh respecting empty-host backoff.
- `app/src/main/java/com/audiobookdownloader/AudioBookWidget.kt`: artwork signal.
- `app/src/main/java/com/audiobookdownloader/PlaybackPulseRules.java`: pure rules.
- JVM PlaybackPulseRulesTest и native IdlePlaybackTest.
- `.github/workflows/playback-idle.yml`, quality harness/results и handoff.

## Журнал и проверки

1. Проверены refs/дерево, прочитан ub-mode; TokenEconomizer CLI status выполнен.
   Sessions0, hooks чата не подтверждены; экономия токенов не заявляется.
2. В отдельной ветке зафиксирована база и red:5 ожидаемых отказов из6,
   воспроизводящих старый always500мс/5с limiter.
3. Реализованы расписание и доставка по событию, сохранены timer deadline,
   foreground/session, position persistence и stop cleanup.
4. Добавлены события обложки и поздней длительности, которые раньше могли
   зависеть от периодического обновления на паузе.
5. Локально6 новых Java-тестов, JaCoCo:100% инструкций двух методов,
   CRAP6/5 (<10), Dryer>=0.8:0 кандидатов, Mutator17/17 killed,0survived.
   Kotlin этими анализаторами не поддерживается: для него Android/JVM/compile.
6. Native тесты используют реальный MediaPlayer с60с WAV, MediaSession controller,
   Handler.hasCallbacks, NotificationManager, сохранённую позицию и цвет новой
   MediaMetadata обложки. POST_NOTIFICATIONS выдаётся shell только тестовому APK
   на эмуляторе. Тест проверяет pause→seek→settings/artwork→resume→pause,
   таймер на паузе и во время воспроизведения. Остальные selected tests:
   VisiblePlayback, BookProgressWidget, PlaybackOptionsUI, LocalContinuation,
   WidgetPreview всех тем. Итоговый
   [CI43889295359](https://github.com/smoc1983-web/AudioBook/actions/runs/43889295359)
   SUCCESS на исходниках `2a1ef379a1d8422fdafaf1d7af368b2ac706acc2`.
   Stable Debug/unsigned Release, Preview Debug/AndroidTest с существующим signer
   собраны. JVM Debug/Release: по332,331 исполнен,1 прежний LiveCatalog skip,
   0failures/errors. Native API36:10/10 успешно. Lint Debug/Release:
   0Fatal/Error,76Warning/76Information; новых/удалённых диагностик по сравнению
   с этапом4 нет, с учётом сдвига строк. Сохранённые parser/JS/JVM fixtures прошли.
   Проверены pause/resume, MediaSession PAUSED state, notification presence,
   seek/persistence, speed change, artwork metadata, clock timers на паузе и
   во время воспроизведения, chapter timer, queue, hidden screen и widget layouts.
   Отчёт `docs/quality/playback-idle/results.json` и логи рядом.

## Воспроизведение

Локально `bash docs/quality/playback-idle/java-tests.sh` с
`QUALITY_JUNIT_CLASSPATH` (JUnit4/Hamcrest). JaCoCo0.8.12 задаётся
`QUALITY_JACOCO_AGENT`/`QUALITY_JACOCO_CLI`. Crapper `--use-existing-coverage
--threshold 9`, Dryer `--threshold 0.8`, Mutator `--no-coverage --max-workers 1
--mutate-all --test-command "bash docs/quality/playback-idle/java-tests.sh"`
на PlaybackPulseRules.java. Это реальные JVM-тесты мутантов, не scan.

CI Java17/Gradle8.9/SDK36:

```sh
gradle -PaudiobookChannel=stable :app:testDebugUnitTest :app:testReleaseUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --max-workers=2 --console=plain
```

Preview Debug/native — существующий signer, API36 aosp_atd x86_64, без
публикации APK. Локального Android SDK нет; APK/lint проверяются именно в CI.
Сохранённые parser/JS/JVM fixtures — `python3 tools/run_checks.py` отдельно.

## Ограничения

Измерений энергопотребления и времени CPU нет: об экономии батареи не заявляем.
Реальный телефон, физическая гарнитура, Bluetooth, OEM launcher и глубокий сон
не проверены. MediaSession controller проверяет программный маршрут системного
управления, не физическую гарнитуру. Wall-clock таймер остаётся на Handler
с elapsedRealtime, как раньше: это не новый exact alarm и не гарантия пробуждения
спящего устройства; фоновое воспроизведение сохраняет прежний CPU wake lock.
Подготовка сохраняет500мс cadence, paused service остаётся доступным для управления.
Адаптация UI/новые функции/архитектурная переработка не выполнялись.

Далее только после подтверждения — этап6, устойчивая идентификация библиотеки.
