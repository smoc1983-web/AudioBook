# Audiobook 4.2.88 · 2026-10-31

Опубликован компактный плеер по выбранному владельцем варианту2.
Обложка расположена над названием, коллекция и продолжения рядом, шкалы книги
и главы соседствуют, управление воспроизведением и инструменты закреплены снизу.
Плеер использует выбранную тему Audiobook, включая светлую и «Графит» без свечения;
настройки читаемости сохраняются. Крупный текст допускает прокрутку сведений о книге.

В нижнем ряду — главы, закладки, очередь, таймер и скорость. В окне закладок есть
«Добавить закладку» для текущего момента. Обычная перемотка использует короткий
выбранный интервал, удержание — длинный. Эквалайзер доступен через настройки плеера.
Подпись коллекции обновляется после сохранения, её данные кэшируются по снимку полок.

Выпуск устанавливается поверх новой Audiobook с прежним пакетом и сертификатом.
Удаление и очистка данных не требуются; старая отдельная стабильная Audiobook не меняется.
Проверка85→88 сохранила контрольные настройки, коллекцию, папку и SHA приватного аудиофайла.
Старый updater принял APK и открыл системное подтверждение установки.

Работа с одобренным ub-mode. JVM Debug/Release:по147тестов,0ошибок,по1optional skip;
Android36 full:83теста,0ошибок,3opt-in skips; targeted23:43/43 и36:44/44.
Lint0Error/Fatal(85Warning/74Information), OSV68модулей/0совпадений.
Кotlin не поддерживается закреплёнными Crapper/Dryer/Mutator: показателиCRAP/coverage
для него не заявлены; UI проверен настоящими MainActivity/PlaybackService и Android-тестами.
Crapper изменённогоJavaUI-test:максимальныйCC9,coverage/CRAP N/A;Dryer0.8 безкандидатов.
[Журнал действий и ограничений](quality/PLAYER-DESIGN-LOG.md).

Actual signed release APK:33069766bytes,
SHA256 f45039978b18e9408ba5c13870e5be6a0c79fac45e2914caf9ee74967e6b4cd9,
cert3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7,
package com.audiobookdownloader.preview,labelAudiobook,nondebuggable,v1/v2.
Разрешения какв85, новыеUIhelpers определенывDEX, тестовыеfixture отсутствуют.
Compiledsourcea13ac3c6955d95ad999b322ec425b57633d90ec5,
treed5bd2329971ff7dd95654c9a48983bfc0ae347a6,full41220372143.
Targeted41221382589/source472ece78b20a80de21ea977abd9821e4264c9310 меняеттолько
2AndroidTestфайлаижурнал (1pxrounding/known-system-ANR handler); production paths identical.

Реальные снимки1/5/10/150 просмотрены. УSDK23 первый offscreen-снимок оказалсячёрным,
поэтомутакжеосмотренпредыдущийa13-frameс тойжепроизводственнойкомпоновкой.
SDK23WAV встаромэмуляторе давалMediaPlayer−38: провереныUI/errorstate,
успешноевоспроизведениеаудиона23не заявлено;PlaybackService вэтойзадачене менялся.
Native36WAV/media/sleep/shake проверены. PhysicalS24/FPS/AndroidnetworkAPK/PlayProtectнепроверены.

PublicSite20,source654f47c414809b0c04e3b6b8bb24c2c90acc34a7,deploysucceeded.
Передпубликацией8unreferenced72/73APKparts(65956503bytes)скопированывнеSite,
сверенывсехеши,провереныактуальныессылки,затемудаленыизhostingpayload.
Current85/78/74 иmanual69/70/71 сохранены. Архив228126598compressed/231086080normalized,
36files;следующийвыпускпроверятьна256MiBпредел,≈37MBзапас.

[Скачать / обновить](https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site)
· [Проверки](release-4.2.88/verification.json)
· [Публикация](release-4.2.88/publication.json)
· [HTTP](release-4.2.88/public-check.json).
