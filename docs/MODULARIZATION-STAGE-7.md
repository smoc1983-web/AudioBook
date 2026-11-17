# Этап 7 — локальное разделение кода и совместимая сборка

Ветка `cloud/modularization-20261117`, база `e96538799085caab34000601f913879a3e48603a` (этап 6). Исходное дерево было чистым; проверены актуальные refs. Main и публичная 4.2.95 не изменяются. Публикация и этап 8 не разрешены этим этапом.

## Объём изменений

Выделен первый самостоятельный компонент `LibraryCollections.kt`: модели BookRef/Shelf, сериализация ссылок, хранение избранного, коллекций и очереди. 13 тел функций перенесены без изменения поведения (проверка extraction.json). MainActivity уменьшен на 261 строку. Публичные функции MainActivityKt, их имена, сигнатуры, default parameters и константы остаются совместимыми делегатами для Java/Kotlin; квалифицированные имена моделей сохранены. Внутренний helper идентичности размещён рядом с моделями.

Это первый локальный шаг разделения крупного Activity, а не завершённая новая архитектура. UI, загрузки, воспроизведение, updater и формат пользовательских данных не переписывались. Существующая реакция legacy loaders на повреждённый JSON не менялась; это не новый механизм восстановления коллекций.

Отдельным изменением инструментов выбрана официально совместимая связка:

| Компонент | Было | Стало |
|---|---|---|
| AGP application/test | 8.7.3 | 8.10.1 |
| Gradle | 8.9 | 8.11.1 |
| Kotlin / Compose Compiler | 2.0.21 | 2.2.21 |
| JDK / JVM target | 17 | 17 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 23 | без изменения |
| Build Tools | 35.0.0 | без изменения |

Удалено подавление `android.suppressUnsupportedCompileSdk=36`: новый AGP поддерживает API36. JVM target перенесён с устаревающего kotlinOptions на compilerOptions, остаётся 17. Gradle pins согласованы в workflows и Windows launcher; условия запуска и команды публикации не расширены. Windows launcher требует установленный Gradle8.11.1, автоматически ничего на ПК пользователя не устанавливает.

Версии Compose BOM, coroutines, AndroidX, jsoup, WorkManager вручную не обновлялись. Изменение транзитивного Kotlin runtime из-за нового компилятора проверяется по разрешённому графу зависимостей. Источники решения: официальные AGP release notes и Kotlin compatibility table; ссылки и диапазоны в toolchain-evidence.json. Массовое обновление библиотек без необходимости не выполнялось.

## Проверки

Финальный [CI44010555019](https://github.com/smoc1983-web/AudioBook/actions/runs/44010555019) — SUCCESS на исходниках `7f7cf7e6d972887c7f484c4ddde76dd7e69f1137`.

- Stable Debug и unsigned Release собраны; AndroidTest Kotlin скомпилирован. Preview Debug/AndroidTest и upgradeProbe собраны с существующим Preview signer; compileDebugSources тестового legacyFixture завершился (агрегат UP-TO-DATE, prerequisites выполнены).
- Debug и Release: по358 JVM-тестов, 357 успешно выполнены,1 прежний skip,0 failures/errors. Пять новых контрактных тестов прошли до и после переноса и после обновления инструментов.
- Эмулятор API36: 28 разных тестов,0 failures/errors/skips — коллекции/импорт/undo, сохранность метаданных/файла/позиции/закладок/очереди, виджеты, загрузки, idle и локальное продолжение.
- Lint Debug/Release:0 Fatal/Error,254 Warning и76 Hint. После чистого переноса было прежних78 Warning/76 Information без новых диагностик. Новая версия lint добавила174 рекомендаций UseKtx и2 InlinedApi для старого кода уведомлений;5 сообщений ObsoleteSdkInt переформулированы,76 Information теперь называются Hint. Это изменение анализатора, не появление176 новых дефектов от переноса. Ни suppression, ни lint baseline не добавлялись.
- Два InlinedApi относятся к Settings.ACTION_APP_NOTIFICATION_SETTINGS/EXTRA_APP_PACKAGE (API26) в прежнем обработчике кнопки «Разрешить уведомления». На API23–25 этот action может не открыть экран; исключение игнорируется. Код существовал до этапа7; фактическое поведение OEM/старого Android не проверено. Рекомендуется отдельный небольшой patch с fallback ACTION_APPLICATION_DETAILS_SETTINGS, без смешивания с этим рефакторингом.
- Сохранён разрешённый runtime-граф из68 модулей: kotlin-stdlib2.2.21; совместимые транзитивные jdk7/jdk8 shims остаются1.8.22. Прямые версии остальных runtime-библиотек не менялись. См. runtime-dependencies.json.
- Parser/source checks и git diff --check прошли. Локальные Android SDK/Gradle отсутствуют; Android-сборки и тесты проведены в Actions, не на локальной машине.

Локально прошли parser/source/Java checks и git diff --check. Они не являются Android-сборкой. На этой машине нет Gradle/Android SDK; сборки, JVM-тесты и испытания Android выполняются в GitHub Actions.

Crapper, Dryer и Mutator запущены на изменённом Kotlin-компоненте: эти инструменты не поддерживают Kotlin и сообщили `No source files`. Это не прохождение CRAP/duplication/mutation gate; покрытие и mutation score Kotlin не заявлены. Не добавлялся бессмысленный Java-код ради метрик. Контроль рефакторинга — неизменность тел функций, тесты поведения, компиляция и instrumentation.

TokenEconomizer CLI status выполнен по постоянному разрешению. Он показывает 0 observed sessions; подключение hooks текущего чата и экономия токенов не подтверждены.

## Журнал

1. Создана отдельная ветка, база записана в baseline.json; прочитаны AGENTS и ub-mode.
2. До переноса добавлены пять контрактных тестов старого JSON, порядка очереди, операций коллекций, отсутствия перезаписи при чтении и Java facade. Первый CI44007657011 выявил ошибку кавычек в новом fixture; исправлена 6388932. Этот прогон не считается green и не описывается как обнаруженная ошибка приложения.
3. Повторные проверки исходного поведения перед переносом: CI44008105152 SUCCESS, по358 JVM (357 выполнены,1 прежний skip),28 native/API36, lint78Warning/76Info/0errors.
4. 13 функций, модели и helper перенесены без функциональных исправлений (dede2bb). CI44008965769 SUCCESS: те же по358 JVM/1skip,28 native, lint полностью совпадает с исходной проверкой без учёта перемещения строк. Проверены parser suite, точное сравнение тел функций и совместимость Java facade.
5. Обновление AGP/Gradle/Kotlin выполнено отдельным коммитом ad9d9ec после проверки переноса, без смены пакета/подписи/версии и без публикации. CI44009956425: native прошёл, unit compilation выявила две JVM-only keySet() в новых контрактных тестах. В 7f7cf7e заменены на общий Android/JVM keys().asSequence().toSet(), набор проверяемых полей не изменён. Финальный повтор: CI44010555019 SUCCESS.
6. Итоговые XML тестов/lint и граф зависимостей сверены отдельно от сообщений статуса Actions; результаты сохранены в results.json и runtime-dependencies.json.

## Риски и ограничения

- Физический телефон, Android API23–35, полная визуальная регрессия всех экранов, Windows/Powershell, OEM-прошивки, Bluetooth/гарнитура и системная установка APK не проверялись.
- Полное устранение монолитного Activity не выполнялось. Продолжать следует небольшими проверяемыми компонентами.
- Не заявлены измерения RAM, времени запуска или батареи; механический перенос сам по себе не обещает ускорения.
- Gradle8.11.1 должен быть установлен на локальных сборочных машинах; прежний Gradle8.9 для нового AGP непригоден. На старых коммитах остаётся их прежняя связка.
- Предупреждения устаревающих GitHub Actions/JDK APIs не скрываются; они отделены от Android lint. Новый Kotlin также предупреждает о трёх объявлениях UpdateSession.kt, экспонирующих package-private Release: в языке 2.4 это станет ошибкой. В выбранной 2.2.21 это предупреждение; границы updater не менялись ради его подавления.

## Основные файлы

`current/MainActivity.kt`, `app/src/main/java/com/audiobookdownloader/LibraryCollections.kt`, `app/src/test/java/com/audiobookdownloader/CollectionStoreContractTest.kt`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `.github/workflows/*.yml` (Gradle pins и новый проверочный workflow), `scripts/windows-development.ps1`, `README.md`, этот отчёт и `docs/quality/modularization/`.

Сохранены package/applicationId, алгоритмы bookId/editionId/chapterId, ключи/форматы данных, пути файлов, signing configuration и схема versionCode/versionName. Релиз не опубликован. Этап 8 — только после решения владельца.
