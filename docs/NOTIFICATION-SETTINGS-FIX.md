# Исправление кнопки настроек уведомлений

Ветка `cloud/notification-settings-20261117`, база `98ebc80439f6285b474186fba1dcacd87e73834f`. Перед изменениями дерево чистое; актуальные refs проверены. Разрешён ub-mode, публикация не разрешена.

Причина: прежний обработчик отправлял ACTION_APP_NOTIFICATION_SETTINGS/EXTRA_APP_PACKAGE без проверки API26. На API23–25 этот публичный переход не поддерживается. При отказе системы исключение игнорировалось без запасного экрана и пояснения пользователю.

Исправление локальное: на API23–25 открывается ACTION_APPLICATION_DETAILS_SETTINGS с `package:` URI текущего приложения. На API26+ сначала открывается экран уведомлений того же пакета; ActivityNotFoundException и SecurityException вызывают единственную попытку перехода в карточку приложения. Если открыть нельзя и её, функция возвращает false, кнопка показывает поясняющий Toast. NEW_TASK позволяет использовать и application context. Предварительный resolveActivity не используется: фильтрация видимости пакетов не должна создавать ложный отказ.

Ветка запроса POST_NOTIFICATIONS на API33+ не изменена. Права не выдаются программно: изменение настройки подтверждает пользователь. Worker/подписки/фильтры уведомлений, оформление, данные, package, версия, подпись и updater не менялись.

## Проверки и журнал

1. Создана отдельная ветка; baseline.json фиксирует исходное состояние. Прочитаны AGENTS и ub-mode. TokenEconomizer CLI status выполнен по постоянному разрешению; 0 observed sessions, hooks/экономия не подтверждены.
2. Старый обработчик извлечён без изменения выбора/запуска в openAppNotificationSettings, добавлены четыре теста выбора экрана и отказов. CI44024427787 подтвердил red: API23 —2 падения выбора экрана, API36 —3 падения fallback/числа попыток. Остальные тесты прошли; компиляция завершилась. Это не исправленная сборка.
3. После подтверждения red исправление сохранено в975a27a. CI44025285454 проверяет API23/25/26/36, включая реальное открытие AOSP Settings и неизменность SharedPreferences.
4. Локальный parser/source/Java suite прошёл; он не является Android-сборкой. Локально Android SDK/Gradle отсутствуют, Android-проверки выполняются в Actions.
5. Crapper/Dryer/Mutator запущены на изменённом Kotlin: инструменты его не поддерживают (`No source files`), успешный gate/coverage/mutation score не заявлен. Логи сохранены.

Промежуточный CI44025285454: все5 тестов прошли на API23/25/26. На API36 четыре проверки логики прошли; реальный запуск вернул false в ATD, где нет приложения Settings. Официальная документация подтверждает удаление Settings из ATD: https://developer.android.com/studio/test/managed-devices#atd . Это неподходящая среда для проверки внешнего системного экрана. В42e210f изменён только workflow: API36 использует google_apis, перед тестом проверяется наличие Settings. Приложение и все assertions сохранены. Повторный CI44026236216 SUCCESS.

Итоговый [CI44026236216](https://github.com/smoc1983-web/AudioBook/actions/runs/44026236216) — SUCCESS на42e210f (production fix975a27a; последний commit меняет только среду проверки).

- Stable Debug и unsigned Release собраны, AndroidTest Kotlin скомпилирован. Preview Debug/AndroidTest/upgradeProbe собраны с прежним signer, legacyFixture compile завершён.
- Debug и Release: по358 JVM,357 выполнены успешно,1 прежний skip,0 failures/errors.
- API23(Android6),25(Android7.1),26(Android8),36(Android16): по5 native-тестов,20 исполнений суммарно,0 failures/errors/skips. На всех четырёх подтверждены выбор экрана, обработка ActivityNotFound/SecurityException, ограниченное число попыток, реальное открытие системных Settings и неизменность SharedPreferences.
- Lint Debug/Release:0Fatal/Error,252Warning/76Hint. По сравнению с этапом7 удалены ровно2 InlinedApi этого обработчика; новых диагностик нет. Предупреждения не подавлялись, baseline не добавлялся.
- Parser/source/Java suite и git diff --check прошли. Все Android-проверки выполнены в Actions, не на локальной машине.

Результаты в docs/quality/notification-settings/results.json. Main и публичная4.2.95 неизменны.

## Файлы и ограничения

Production: `current/MainActivity.kt`, `app/src/main/java/com/audiobookdownloader/NotificationSettings.kt`. Проверки: `NotificationSettingsTest.kt`, `.github/workflows/notification-settings.yml`, `docs/quality/notification-settings/`.

Физический телефон и OEM-настройки не проверены. Имитируемые отказы подтверждают обработку обоих типов исключений; тест AOSP не гарантирует наличие экранов на любой прошивке. Если оба отсутствуют, пользователь получает сообщение. Разрешительный диалог Android13+ и восстановление приложения через Installer не проверяются этим фиксом; ветка запроса разрешения сохранена в коде. Этап8 не начат, релиз не опубликован.
