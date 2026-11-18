# Этап8 — несколько папок библиотеки проверены, не опубликованы (2026-11-18)

Ветка cloud/library-roots-20261118 от19cb6ee (чистая база/fetch совпали).
Проверены исходникиb5c0ecf6964bcdb2a855b4acb5be7248da3a934c;
CI44229653029 attempt2 SUCCESS: Stable Debug/unsigned Release, Preview Debug/
AndroidTest/upgradeProbe с прежним signer, legacyFixture compile/runtime audit.
JVM по410 (409executed/1oldLiveCatalogskip), native75/API23+77/API36 (152,0fail/
error/skip). Lint0Fatal/Error,271Warning84Hint: −1UseKtx,+1AutoboxingStateCreation,
без подавлений. Java5green (red5), CRAPmax7/Dryer0scoped/Mutator39из39; Kotlin
ими не поддерживается. Parser/source/JVM/shell syntax checks green.
Библиотека→управление доступом→Папки библиотеки: до32 treeURI. Новая папка не
заменяет старую; read-only persistent grant допустим. Legacy library_tree_uri
переносится добавочно и не меняется. Явное[] не восстанавливает старый grant.
Отключение подтверждается, файлы/позиции/заметки/очередь/коллекции не удаляются,
shared grant не отзывается (может быть нужен плееру/другим функциям). Default
Download/LIBRARY_DIRECTORY и Share-копии остаются отдельными источниками.
Место загрузок и реальная структура папок прежние; обход прежней глубины:
выбранная папка + непосредственные папки книг. Ошибки per-root, остальные
доступные книги сохраняются. No recursive/cloud client/OAuth added.
Doc authority+opaqueID/SHA dedup перекрытых grants; 32bit-hash collision paths
исправлены. First observed URI в audiobook_library_origins_v1 и identityOrigin
сохраняют старый bookId/ambiguous-edition при смене только grant, actual URI для
чтения/удаления актуальная. Origin batch commit/first-writer, corrupt raw preserved.
Root list bad JSON не сбрасывается: подтверждённый snapshot raw/type и recover;
первый damaged snapshot не перезаписывается. Failed commit не признаёт RAM за
сохранение, last-committed корни/pending/nonce обеспечивают безопасный повтор.
Root settings/grants/origin-map не экспортируются в backup; diagnostics — counts,
не URI/имена папок. Chapter/progress format/IDs не менялись. Title-only BookRef/
unknown same-title ограничения прежней модели остаются; не угадывать издание.
Исправлен actual red: title-only unfinished filter скрывал чужую внешнюю главу;
теперь только own download folders incl canonical aliases. Owned unverified глава
остаётся скрытой; реальный private symlink проверен. Production updater/manifest/
PlaybackService/package/version/signer не изменены, файлов пользователя не касались.
Native fixture fixes: test DocProvider exported+MANAGE_DOCUMENTS, явный registry
owner для ContextWrapper, reflexive proxy, grant ack after onDestroy/own task.
API23 старый Seek fixture timeout/ANR в waitForIdle→Scenario.close: cleanup до
ожидания idle, все body assertions прежние. Это не доказанный фикс real-device
ANR. CI44228767587 тоже green75/77; финал повторил с diagnostic capture безANR.
Последний native36 attempt1 остановлен Maven429 до тестов; только job rerungreen.
Physical phone/OEM/SD/USB/live cloud providers, diskfull/processkill/reboot,
Installer/батарея/RAM не проверялись; failed commits моделировались. Binder/query
провайдер может задержать весь scan, harddeadline в приложении нет. Не путать
120s/test+8min/run guards CI с пользовательскими тайм-аутами. Весь исторический
native suite не запускался, фильтры/классы перечислены в results/report.
Отчёт docs/LIBRARY-ROOTS-STAGE-8.md и docs/quality/library-roots/results.json.
Source main c8301dbf2fb558c8330d87baa67cacf307708529/public4.2.95 прежние.
Этап8 НЕ завершён: дальше metadata editor, consented Wi-Fi next-volume, filters.
Публикация отдельно с разрешения. Ub-mode одобрен для текущего этапа8;
TokenEconomizer CLI без повторных вопросов, status sessions0, hooks/savings не
подтверждены. Предыдущие история/мониторинг/Share/копии/SAF/Яндекс сохранены.
Историческое «далее несколько папок» ниже уже выполнено.

# Этап8 — импорт аудио через «Поделиться» проверен, не опубликован (2026-11-17)

Ветка cloud/share-import-20261117 от3c37db9 (чистая база/fetch совпали).
Проверенные исходникиcf3c363a68786b45dd0750ec4593f4e9c0daad17;
CI44149578424 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest/
upgradeProbe с прежним signer, legacyFixture compile, dependency audit. JVM по402
(401 выполнен/1 прежнийLiveCatalogskip); native59/API23,61/API36 (120,0fail/error/skip).
Lint0Fatal/Error,272Warning83Hint: +1ApplySharedPref/+1UseKtx регистрации, без
подавлений, commit наIO нужен для результата. Java5/5, CRAPmax9/Dryer0scoped/
Mutator33из33; Kotlin ими не покрыт. Parser/source/JVM checks green.
Импорт: Android Share/Open with либо Библиотека→Импортировать файлы; content URI,
подтверждение названия/порядка1,2,10/перестановки, копия в filesDir/imported-audio-v1.
Без автоматического воспроизведения. Оригиналы не меняются; 128файлов,2ГиБ/файл,
8ГиБ/книга; streamcopy,size/MediaExtractor/audio packets/hash, HTML/плейлисты и
неполные/неподдерживаемые файлы отклоняются, суффикс по фактическому формату.
Ordered content SHA определяет edition; внутренние numeric chapter names стабильны
при переименовании внешних файлов/смене URI. Дубликат не создаётся, недостающая
глава восстанавливается, изменённая существующая не затирается. Титулы старых
позиций/очереди/коллекций защищены; checkpoint/заметки/закладки сохраняются.
Manifest/staging+atomic rename, отмена; старый owned staging чистится на следующем
импорте, автоматического resume послеprocesskill нет. Missing pref registration
восстанавливается из манифеста, corrupt binding сохраняется. Existing delete/backup
интегрированы; удаление только private copies. При uninstall копии пропадут — экспорт.
Android6 redCI44147675850:7 отказов корректного short WAV, API36 green. AOSP23
advance освобождает текущий sample, EOF виден только следующему fetch; добавлен
next sample index/time guard после первого пакета. Итог проверяет short/long WAV,
семь прежних форматов, HTML/HLS/DASH/размер/повреждения без ослабления assertions.
Отдельный testAPKUID/grants, pipe unknown size, AFD range, rotation безautoimport/
playback, repeat/repair/delete/progress/backup contracts пройдены на23/36.
Сторонний провайдер может застрять в Binder, игнорируя cancel; UI не ждёт, жёсткий
deadline/полная декодировка/DRM не обещаны. Реальные Telegram/Yandex/Google provider,
телефон, diskfull/processkill/reboot, Installer, батарея/RAM не проверены. Весь
исторический native suite не запускался; выбранные регрессии перечислены в отчёте.
Отчёт docs/SHARE-IMPORT-STAGE-8.md и docs/quality/share-import/results.json.
Source main/public4.2.95/package/version/signer/пользовательские оригиналы прежние.
Этап8 НЕ завершён: далее несколько library roots, metadata editor, consented Wi-Fi
next-volume, дополнительные фильтры. Публикация только после разрешения.
Ub-mode разрешён для текущего этапа8; TokenEconomizer CLI без повторных вопросов,
status sessions0, hooks/экономия не доказаны. Предыдущие история/мониторинг/копии/
SAF/Яндекс сохранены (Яндекс только writable tree provider, безOAuth).
Исторические указания «следующее — импорт» ниже уже выполнены.

# Этап8 — ручной мониторинг источников проверен, не опубликован (2026-11-17)

Ветка cloud/source-health-20261117 от033e002 (чистая база/fetch совпали).
Проверенные исходникиd31a8c80003cb8f18eed44bb2dc67ab8a35aee05;
CI44133946728 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest/
upgradeProbe с прежним signer, fixture compile, dependency audit. JVM по395
(394 выполнены/1 прежнийskip), native42/API23,44/API36 (86,0fail/error/skip).
Lint0Fatal/Error,270Warning83Hint: +1ComposableNaming/+1UseKtx к предыдущему шагу;
подавлений нет. Локальные5Java/CRAPmax8 методов/Dryer0scoped/Mutator35из35,
parser/source/JVM и runner argument contract9/10classes пройдены; Kotlin tool gap.
Поиск→Источники→Проверить включённые источники. Ручной анонимный GET home из
существующего реестра; SourceHttp/NetworkAddressRules/ContinuationPolicy используются
повторно. HTTPS443, точный источник на каждом redirect, никаких cookies/auth/browser,
сертификаты не ослаблены. Ответ сайта не подтверждает аудио/каталог/скорость скачивания.
Открытие только читает private source_health_v1 (без URL/секретов/тел страницы),
проверяется выбранный на момент запуска список. 3одновременно, connect/read4с,
UIdeadline10с,128КиБ распакованного префикса/256КиБ wire, gzip поддержан; неизвестные
кодировки нейтрально-ошибочные. Пулы ограничены:3network+3queue,1close+6queue.
Отмена/закрытие scoped tasks отменяет; неотменяемый DNS может ещё занимать ограниченный
поток, UI его не ждёт. Поздняя фабрика не начинает запрос после отмены.
Цвет+текст+время/HTTP, stale после15мин, force не обходит1мин/capped24h Retry-After.
Нет WorkManager/новых разрешений/периодического опроса/новых зависимостей. Источники
не выключаются/не сортируются/не скрываются; поиск/главы/аудио-CDN/updater прежние.
Clock-future/плохой schema/числа/HTTP-status/backoff не получают ложный fresh/доверие;
кэш сохраняет raw при чтении плохой записи и может обновляться ручной проверкой.
load сериализован после отмены; RAM cooldown не теряется при ошибке записи/reopen.
CI44131594987: verify+36green;23 1fail/42 при listOf vararg JSONObject fixture,
до assertions. Линейная подготовка сохраняет все случаи; причина ART/D8 не доказана.
Итог42/44green. Реальные сайты/VPN/аккаунты/Cloudflare на телефоне не проверены;
TLS-всех провайдеров,реальный disk fail/process-kill/reboot,Installer,батарея/RAM — нет.
Отчёт docs/SOURCE-HEALTH-STAGE-8.md и docs/quality/source-health/results.json.
Source main/public4.2.95/package/version/signer/аудиофайлы/позиции/закладки не менялись.
Этап8 НЕ завершён: далее share-import, несколько library roots, metadata editor,
consented Wi-Fi next-volume, дополнительные фильтры. Публикация только с разрешения.
Предыдущие история/копии/SAF/Яндекс сохранены; Яндекс только SAF writable tree,
безOAuth; аккаунт по-прежнему не проверен. Ub-mode разрешён на текущий этап8;
TokenEconomizer CLI без повторных вопросов: status, sessions0, hooks/экономия не доказаны.
История ниже; рекомендации «следующее — мониторинг» уже выполнены вручную.

# Этап 8 — история перемоток проверена, не опубликована (2026-11-17)

Ветка cloud/seek-history-20261117 отaa3e8f3 (чистая база, удалённая совпадала).
Проверенные исходникиed7cf99789e4851f0caa43258f5de6e7960296fd,
CI44123938415 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest/
upgradeProbe с прежним signer, fixture compile/dependency audit. JVM по378
(377 выполнены/1 прежнийskip), native35/API23 и37/API36 (72,0fail/error/skip).
Lint0Fatal/Error,268Warning83Hint: к прошлому пакету только+3UseKtx, подавлений нет.
Локальные7Java, CRAPmax4 методов/Dryer0scoped/Mutator6из6 (append/resolve), parser
и shell class-filter contract пройдены. Kotlin инструментами ub-mode не покрыт.
История: Плеер→Закладки→История перемоток, максимум50 общих точек устройства,
показ только текущего edition, stable chapter ID, до/после позиции, без URL/секретов.
Возврат сохраняет паузу/скорость/минутный таймер, работает после перестановки глав и
пересоздания службы; чужие/неточные главы отклоняются, возврат сам не записывается.
Обычное прослушивание/auto chapter/resume rewind/кнопки next-previous/закладки не
записываются. Очистка своего издания подтверждается; повреждённый JSON не заменяется
молча, подтверждённый reset сохраняет raw в damaged. Хранение отдельно seek_history_v1,
apply без новых периодических задач; в backup/SAF история не добавлена.
Canonical chapter helper общий со StableProgress; wire format/IDs/аудио/закладки прежние.
Для UI все ID рассчитываются одним проходом и кэшируются до смены каталога/плейлиста.
Дополнительно исправлен прежний API23 applySettings: pause разрешён только если
native player.isPlaying, а не в Prepared после setPlaybackParams. Red CI44122287510:
API23 4fail/34,36green,verifygreen; logcatpause state8/error-38. После guard35/37green.
Первый CI44120323342 ошибочно запускал всю историю тестов из-за отдельных shell
в emulator-runner. Исправлен runner script; старые Settings/reboot/server/sensor
сценарии нуждаются в специальных стендах, StartupSmoke list14/Lis10 устарел.
Подробности/старые5отказов/API36 в отчёте; не заявлять green всего исторического набора.
Отчёт docs/SEEK-HISTORY-STAGE-8.md и docs/quality/seek-history/results.json.
Физический телефон/гарнитура/OEM/process-kill/reboot/Installer/батарея/RAM не проверены.
Source main/public4.2.95/package/version/signer/пользовательские файлы не менялись.
Весь этап8 НЕ завершён: далее мониторинг источников, share-import, несколько library
roots, metadata editor, consented Wi-Fi next-volume, дополнительные фильтры.
Предыдущие копии/SAF/Яндекс сохранены: Яндекс только через установленный SAF-провайдер
с persistable writable tree, без OAuth; реальный аккаунт по-прежнему не проверен.
Ub-mode разрешён на текущий этап8; TokenEconomizer CLI без повторных вопросов,
status выполнен, sessions0, hooks/экономия не подтверждены. Релиз только с разрешения.
Исторические записи ниже; их рекомендации «следующее — история» уже выполнены.

# Этап 8 — первый пакет проверен, не опубликован (2026-11-17)

Владелец разрешил этап8 и ub-mode. Ветка cloud/development-stage8-20261117,
базаeadc0d7; проверенные исходники1eb342ee1a1038b80789d33f311ad02160fab626.
CI44046401127 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest/
upgradeProbe с прежним signer, fixture compile, dependency audit; по369 JVM
(368 выполнены/1 прежнийskip), по28 native/API23,36 (56 исполнений,0fail/skip).
Lint0Fatal/Error,265Warning83Hint: +11UseKtx,+2ApplySharedPref,+7Int-state hints
к уведомлениям; подавлений нет, commit нужен для результата сохранения. Локально11Java,
CRAPmax5/Dryer0scoped/Mutator34/34; Kotlin этими инструментами не покрыт.
Реализовано: ручная локальная проверка аудиоструктуры/размера/SHA256 без удаления;
opt-in WorkManager24h и7 исправных локальных копий metadata/progress без аудио;
ручной обмен stable edition/chapter positions через2 SAF-подключения (общая папка,
Яндекс). По умолчанию ничего не передаётся. Владелец выбрал Яндекс именно через
системный выбор папки, без OAuth; client_id/пароль не спрашивать. Работает только
если провайдер Диска предоставляет persistable writable tree; реальный аккаунт
не проверен. Preview/confirm, channel/package/schema/IDs, fresh-local/changed-remote/
chapter guards; нулевая и более ранняя позиция принимаются только явно.
Автоматической облачной синхронизации нет. Внутренние копии теряются при uninstall;
для внешнего хранения есть экспорт. Кэш приостановленного плеера очищается перед
подтверждённым восстановлением, чтобы не затереть полученную позицию.
API23 AAC исправлен явным диапазоном FD. Тайм-аут старого HTTP-теста подтверждён
Android400 без запросов; только disposable API23 CI Debug manifest получает HTTP,
обычная сетевая политика Debug/Release/main и updater не менялись. Assertions не ослаблены.
Этап8 НЕ завершён: ещё история перемоток, глобальный мониторинг источников, share-import,
несколько library roots, metadata editor, consented Wi-Fi next-volume, новые фильтры.
Следующий небольшой пакет — история перемоток, затем мониторинг. Не смешивать миграции.
Отчёт docs/DEVELOPMENT-STAGE-8.md и docs/quality/development-stage8/results.json.
Физический телефон, реальный Yandex/DocsUI grant,24h/reboot,Installer,батарея/RAM
не проверены. Generic DocumentsProvider и восстановление ActivityResult tested.
Main/package/version/signer/IDs/аудиофайлы/public4.2.95 не изменены; релиза нет.
TokenEconomizer CLI без повторных вопросов: status выполнен, sessions0, hooks/
экономия не подтверждены. Ub-mode разрешён на продолжение того же этапа8.
Исторические записи ниже, их «этап8 не начат» больше не актуально.

# Кнопка уведомлений — исправлено, не опубликовано (2026-11-17)

Владелец разрешил отдельный фикс и ub-mode. Ветка cloud/notification-settings-20261117
от98ebc80; production fix975a27a, проверенные исходники42e210f323397a89865782c67a2ae36033fb5b44.
CI44026236216 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest/
upgradeProbe с прежним signer, fixture compile; по358 JVM(357pass/1прежнийskip),
по5 native на API23/25/26/36(20 исполнений,0fail/skip), реальное открытие Settings
и неизменность prefs. Lint0Fatal/Error,252Warning/76Hint: ровно2 InlinedApi
устранены, новых диагностик нет, подавления не добавлялись. Red CI44024427787
подтвердил API23:2fail,API36:3fail. CI44025285454: старые3OS green,36 ATD без
Settings не подходит для UI integration; официальный docs это подтверждает.
В42e210f только CI36 переведён на полный google_apis; assertions сохранены.
API23–25 открывает карточку своего пакета;API26+ сначала экран уведомлений,
при ActivityNotFound/SecurityException один fallback на карточку. При полном
отказе понятный Toast, без молчаливого игнорирования. API33+ permission branch
сохранена. Worker/подписки/package/version/signer/updater/пользовательские данные
не менялись. Main/public4.2.95 прежние; этап8 и публикация не начаты.
Отчёт docs/NOTIFICATION-SETTINGS-FIX.md, docs/quality/notification-settings/results.json.
Физический телефон/OEM/разрешительный диалог13+/Installer не проверены.
Crapper/Dryer/Mutator не поддерживают Kotlin; gate/score не заявлен. TokenEconomizer
CLI status выполнен: sessions0, hooks/экономия не подтверждены. История ниже.

# Этап 7 — локальный шаг проверен, не опубликован (2026-11-17)

Ветка cloud/modularization-20261117 отe965387. Извлечены13 неизменных функций
коллекций/избранного/очереди и модели в LibraryCollections.kt; MainActivity -261
строка, публичный Java/Kotlin facade и wire format сохранены. До переноса
CI44008105152 SUCCESS, после dede2bb CI44008965769 SUCCESS без lint delta.
Инструменты отдельным ad9d9ec: AGP8.10.1/Gradle8.11.1/Kotlin+Compose compiler2.2.21,
JDK17/SDK36/min23 прежние, suppressUnsupportedCompileSdk удалён, pins22workflows
и Windows launcher согласованы. Условия/команды публикации не менялись.
Финальные исходники7f7cf7e6d972887c7f484c4ddde76dd7e69f1137,
CI44010555019 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest/
upgradeProbe с прежним signer, fixture compile; по358 JVM (357 выполнены,1skip),
28native/API36. Lint0Fatal/Error,254Warning/76Hint: новый анализатор добавил174
UseKtx рекомендаций и2 InlinedApi старого action настроек уведомлений;76Info
переименованы в Hint,5 obsolete сообщений переформулированы. Не заявлять no-new-
lint после смены инструмента. На API23–25 кнопка может ничего не открыть (не
проверено на устройстве); рекомендован отдельный fallback patch. Новый Kotlin
предупреждает о3 Release visibility объявлениях UpdateSession для будущего2.4.
Отчёт docs/MODULARIZATION-STAGE-7.md; docs/quality/modularization/results.json.
Runtime-граф68модулей сохранён. Физический телефон/API23–35/Windows/Installer,
полная визуальная регрессия и измерения производительности не проверялись.
Crapper/Dryer/Mutator не поддерживают изменённый Kotlin, gate/score не заявлен.
TokenEconomizer CLI status: sessions0, hooks/экономия не подтверждены.
Main/package/version/signer/public4.2.95/данные неизменны. Activity разделён лишь
частично, legacy corruption поведение коллекций не менялось. Публикация и
этап8 только после решения владельца; перед развитием разобрать compatibility
замечания. Исторические записи ниже.

# Этап 6 — проверен, не опубликован (2026-11-16)

Ветка cloud/library-identity-20261116 отf372216; проверенные исходники
b26f32fee057b770eddc56906b1249737ab30a5d. CI43939142477 SUCCESS:
Stable Debug/unsigned Release, Preview Debug/AndroidTest с прежним signer;
по353 JVM (352 выполнены,1 прежний skip),24 native/API36. Lint0Fatal/Error,
78Warning/76Info: +2 ApplySharedPref в BookmarkStore, commit нужен для результата
записи; остальные диагностики совпадают с этапом5. Локально5 Java, CRAPmax7,
JaCoCo100% инструкций2 методов, Dryer0 scoped, Mutator9/9; Kotlin ими не покрыт.
Метаданные/членство/known-edition закладки разделены по editionId, добавлен
bookId записи; переименование сохраняет метаданные/позицию. Старые ключи/ID,
файлы, полки/очередь/настройки сохраняются; неоднозначные папки не угадываются.
Миграция добавочная, не полное устранение title-зависимостей; новых completion/
каталогов старый код не увидит, произвольное перемещение папки не отслеживается.
Отчёт docs/LIBRARY-IDENTITY-STAGE-6.md, docs/quality/library-identity/results.json.
Физического телефона/Installer, повторного reboot/force-stop этапа3 и измерений
RAM/батареи/UI write latency нет. Source main/package/version/signer/public4.2.95
не изменены. Этап7 и публикация только после отдельного подтверждения.
TokenEconomizer CLI status выполнен; sessions0, hooks/экономия не подтверждены.

# Этап 5 — проверен, не опубликован (2026-11-16)

Владелец разрешил этап5 и ub-mode. Ветка cloud/playback-idle-20261116
от60a952a; проверенные исходники2a1ef379a1d8422fdafaf1d7af368b2ac706acc2.
CI43889295359 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest
с прежним signer, по332 JVM (331 исполнен,1 прежний skip),10 native/API36.
Lint0Fatal/Error,76Warning/76Info, новых диагностик относительно этапа4 нет.
Локально6 Java, JaCoCo100% инструкций двух методов, maxCRAP6, Dryer0 scoped,
Mutator17/17. Анализаторы не покрывают Kotlin; для него compile/JVM/native.
Playback pause без часового таймера снимает periodic handler pulse; playing/
loading до500мс, paused clock timer до1с/дедлайна. Position persistence5с при
прослушивании и immediate pause/seek сохранены. Виджеты пропускают неизменные
snapshots, события доставляются сразу, periodic progress ограничен5с.
Обложка/поздняя длительность обновляются событиями, duplicate service widget path
убран. Реальные MediaSession, MediaPlayer, handler idle, timer, seek/persistence,
metadata cover, progress widgets/очередь/hidden UI проверены на API36.
Отчёт docs/PLAYBACK-IDLE-STAGE-5.md; docs/quality/playback-idle/results.json.
Измерений батареи/CPU, физического телефона, гарнитуры/Bluetooth/OEM launcher,
глубокого сна нет; это не exact alarm. Main/package/version/signer/данные и
публичная4.2.95 неизменны. Этап6 и публикация только после подтверждения.
TokenEconomizer CLI без вопросов; hooks/экономия чата не подтверждены.
Исторические записи ниже.

# Этап 4 — проверен, не опубликован (2026-11-16)

Владелец разрешил этап4 и ub-mode. Ветка cloud/security-diagnostics-20261116
от49fcd1c; проверенные исходники1cf01f19dbd4b9b461ddf43fe8cc333a49ccc0cd.
CI43885364170 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest
с прежним signer, по326 JVM (по1 прежнему skip),18 native/API36 +2 отдельных
instrumentation запуска с force-stop/reboot. Lint0Fatal/Error,76Warning/76Info,
новых диагностик относительно этапа3 нет. Локально20 Java, maxCRAP8 с JaCoCo,
Dryer0 scoped, Mutator83/83. Эти анализаторы не покрывают Kotlin.
Диагностика обезличена, старые source/search/playback записи фильтруются при
чтении, новые записи не сохраняют поисковые запросы/примеры. Перед отправкой
есть preview; clipboard/TXT дополнительно фильтруются. Общие HTTP(S)/redirect
правила, секретные заголовки не уходят на другой origin. Updater сохраняет
строгие GH/CDN allowlist и проверки хеша/версии/package/подписи.
Отчёт docs/SECURITY-DIAGNOSTICS-STAGE-4.md; docs/quality/security-diagnostics/results.json.
Реального телефона/Installer не было; DNS rebinding и платформенные subresource
redirect не перехватываются. Source main, package/version/signer и публичная4.2.95
не изменены. Этап5 и публикация — только после подтверждения владельца.
TokenEconomizer CLI без вопросов; hooks/экономия чата не подтверждены.
Исторические записи ниже.

# Этап 3 модернизации — проверен, не опубликован (2026-11-16)

Владелец разрешил этап3 и ub-mode. Ветка cloud/durable-queue-20261116,
база742e361; проверенные исходники2dd0d8ef0b98a100ec64662a1d06c425a8883907.
CI43872154868 SUCCESS: Stable Debug/unsigned Release, Preview Debug/AndroidTest
с существующим signer, по302 JVM (по1 прежнему skip),16 native/API36 +2 отдельных
instrumentation запуска с force-stop и реальной перезагрузкой эмулятора.
Одна передача/файл, прежняя позиция/закладка подтверждены. Физический телефон не проверялся.
Lint0Fatal/Error,76Warning/76Info; новых диагностик нет, одно предупреждение убрано.
Атомарная очередь + backup, сохранение повреждённых исходников, строгая миграция,
ограниченные повторы, дедупликация и reuse проверенного аудио. При отсутствии
пригодных копий явная блокировка/повтор восстановления, без удаления книг/прогресса.
Локально8 Java, maxCRAP7 с JaCoCo, Dryer0 scoped, Mutator32/32. Kotlin ими не покрыт.
Отчёт docs/DURABLE-QUEUE-STAGE-3.md; docs/quality/durable-queue/results.json.
Main/package/version/signature и публичная4.2.95 неизменны; этап4/публикация
только после подтверждения. TokenEconomizer CLI без вопросов; hooks/экономия
текущего чата не подтверждены. Исторические записи ниже.

# Этап 2 модернизации — проверен, не опубликован (2026-11-16)

Владелец разрешил этап2 проверки скачанного аудио и выбрал ub-mode.
Ветка cloud/audio-validation-20261116 от4d88454; проверенные исходники
300a94841afefdf505e1a59e9dd115a3052c730b. CI43861250296 SUCCESS:
Stable Debug/Release APK, по289 JVM (по1 прежнему skip), lint без новых сообщений;
7 native/API36 прошли, включая все форматы, повтор/отмену, старые задания,
запоздалые ошибки и сохранность готовой главы/позиции. Max Java CRAP8 с JaCoCo,
Dryer0 на изменённом Java-файле, Mutator34/34. Kotlin ими не анализировался.
Новая фаза verifying; HTTP success больше не означает проверенное аудио.
Полного декодирования/доверенной суммы аудио нет; риски см.в отчёте.
Пакет/версия/подпись/позиции сохранены, public4.2.95 прежний.
Читайте docs/AUDIO-VALIDATION-STAGE-2.md и docs/quality/audio-validation/results.json.
Не начинать этап3 и не публиковать без подтверждения; source main/backup не менять.
TokenEconomizer CLI использовать без новых вопросов (постоянное разрешение);
выбор ub-mode перед новой задачей остаётся отдельным правилом.

# Предпочтение TokenEconomizer, 2026-11-16

Владелец просит использовать TokenEconomizer CLI без повторных вопросов при
следующих работах. Это не отменяет отдельный выбор ub-mode перед новой задачей.
CLI status выполнен; hooks текущего ChatGPT-чата не подтверждены, экономия не заявлена.

# Этап 1 модернизации — проверен, не опубликован (2026-11-16)

Владелец одобрил ub-mode ТОЛЬКО для этапа 1: единый прогресс книги.
Ветка cloud/progress-unification-20261116, исходная точка cac497c; проверенные
исходники14811d74e71ce8040490829966929a16c355ca18. CI43842485672 SUCCESS:
Stable Debug/Release APK, по282 JVM (по1 прежнему skip), lint без новых сообщений;
4 native/API36 прошли. 13 локальных Java-тестов; max CRAP6 с JaCoCo, Dryer0,
Mutator66/67, один эквивалентный survivor. Kotlin этими анализаторами не покрыт.
Позиции/ID/файлы/подпись/package/version сохранены. Public4.2.95 прежний.
Читайте docs/PROGRESS-STAGE-1.md и docs/quality/progress/results.json.
Не начинать этап2 и не публиковать без подтверждения. Source main/backup не менять.
TokenEconomizer CLI status проверен; hooks этого чата/экономия не подтверждены.
Ниже исторические состояния, для текущей задачи приоритет у этого заголовка.

# Source resilience fixes, 2026-11-11 — unpublished

Owner chose NO ub-mode. Branch cloud/fix-source-resilience-20261111.
Final CI43061821679 SUCCESS/source8ee96ae: parser suite, Stable Debug/Release
compile/unit/lint, AndroidTest Kotlin compile. GET bounded retries/deadline,
DownloadManager transient observations, explicit HLS/DASH errors. Updater security
unchanged; extra hash/version/signer assertions passed. No device installation,
no release; public95 remains. See docs/SOURCE-RESILIENCE-2026-11-11.md and patch.
Earlier CI43060865604 lint BOM error fixed, no baseline/suppression. Main/backup unchanged.

# Опубликована Audiobook 4.2.95, проверено 2026-11-11

Владелец разрешил публикацию: «Выложи обновление». Публичный preview-v100095,
versionCode100095, прежний пакет/сертификат Preview. Release440653507,
source aade1a88688219571eaef0cef93557e8230739f6; CI42860160270 SUCCESS:
Release build/unit/lint, подпись и API digests, анонимный production Java probe
полностью скачал APK и подтвердил95. Размер33151692байта. Установку на физическом
телефоне не проверяли. Скачивание всех книг шести ручных сайтов не гарантировано.
См. docs/RELEASE-4.2.95.md и docs/release-4.2.95/publication.json.
Ветка cloud/publish95-20261110; main/backup не менялись. Задача без ub-mode.
Следующий релиз требует нового поручения; 95 не заменять и не переподписывать.

# Скачивание из дополнительных сайтов, 2026-11-10

Без ub-mode по выбору владельца. Ветка cloud/recovery-source-downloads-20261110.
Audioknigi.top: встроенный поиск и штатный API глав; живое аудио206/audio-mpeg
проверено частичным запросом. Для остальных шести browser-only источников —
встроенный просмотр и явная проверка плейлиста/скачивание доступных глав.
Это НЕ шесть проверенных автоматических адаптеров: сайты нестабильны/недоступны,
специальные плееры могут не поддерживаться. См. docs/SOURCE-DOWNLOADS-2026-11-10.md.
15 JS test files,12 новых кейсов,7 Chromium fixture checks; Stable Debug compile,
257 JVM/0 ошибок/1 прежний skip,AndroidTest compile,lint прошли. Native/physical,
Release/Preview и новый подписанный APK не проверялись/не собирались.
Релиз94 остаётся публичным; новая публикация не разрешена. Main/backup не менять.

# Очистка рабочей ветки,2026-11-07

Удалены из дерева history/releases/design/references/hosting, архивные манифесты,
восстановитель ZIP и исторические проверки снимков. Нужные DOM/JS fixtures
и структурный Kotlin checker перенесены в current/tests. tools/run_checks.py
прошёл (14 JS-файлов тестов и Java-правила). Production Kotlin/Java/resources,
Gradle и Android-тесты не изменены. История Git, main, backup и релиз94 сохранены.
Рабочая ветка cloud/publish94-20261105. Очистка не означает уменьшение Git history;
не делать force-push/gc истории или новый выпуск без отдельного поручения.
Текущее состояние приложения и правила ниже остаются в силе.

# Опубликована Audiobook4.2.94 (2026-11-05)

Владелец явно разрешил публикацию словом «Публикуй». Release434996201,
preview-v100094,source608b366,33135759байт. Прежние Preview package/certificate
сохранены. Полный анонимный production-Java probe выбрал94, получил metadata
и APK из одного релиза и проверил независимый GitHub digest/размер/SHA-256.
Скачанный публичный APK также прошёл apksigner v1/v2, один прежний подписант.
Доказательства в docs/release-4.2.94/. Физической установки не было.

https://github.com/smoc1983-web/AudioBook/releases/tag/preview-v100094
Прямая ссылка: https://github.com/smoc1983-web/AudioBook/releases/download/preview-v100094/Audiobook-Preview.apk
GitHub updater обнаруживает94; старый strict Site origin не исправлен,
по-прежнему может потребоваться разовая установка APK поверх прежней Preview.
Stable не смешивать; main/backup исходников не менялись. Следующие публикации
требуют отдельного разрешения. Новый код10этапов прошёл41997033226:оба канала,
512 JVM на канал/48 native/Debug/Release/lint. Release94 unit/build/lint прошли
42002041844; итог workflow красный из-за запроса draft по ещё не созданному тегу.
Два файла уже были загружены; проверены через releaseID434996201 и опубликованы
CLI без пересборки/замены. Первый42001207923 остановлен безопасно на неверном
номере из reserved Actions env; исправлен process env94. Draft lookup исправлен
в82d37a5[skip ci]. Финальная анонимная проверка выполнена после публикации локально.

# Все 10 этапов проверены, выпуск не опубликован (2026-11-05)

Production5afee8e733d96e2fde36ed967588c553e225c46c, ветка cloud/recovery-search-editions-20261104.
Финальный CI41997033226 SUCCESS: Preview и Stable Debug/Release/unit/build/lint,
512 JVM на канал,0 failures,2 skipped;48 native,0 failures/errors/skipped.
Lint0 Fatal/Error,76 Warning,75 Info. 600 карточек:655мс подготовки,12 peak composed;
возврат/догрузка/прокрутка прошли. Это эмулятор, не измерение FPS телефона.
Проверен повтор завершённой главы, HTTP403 загрузки, коллекционные уведомления,
настоящий публичный APK через UpdateSession/поворот/закрытие до Installer.
Подтверждение установки не нажимали, физический телефон не проверен.
Все10 направлений реализованы в оговорённых границах; см. docs/IMPROVEMENT-SEQUENCE.md
и тематические документы. Не обещать универсальный refresh/все31 рабочих сайта.
Публичная4.2.93 осталась прежней. НОВЫЙ РЕЛИЗ НЕ ПУБЛИКОВАТЬ без разрешения.
main/backup исходников не менять. Ub-mode одобрен на всю эту последовательность.
Следующий шаг: пользовательское тестирование/подготовка кандидата с новым номером
только при соответствующем поручении; тестовый100090 не распространять поверх93.
Ниже — исторические записи.

# Все 10 этапов реализованы; окончательный CI идёт (2026-11-05)

Production5afee8e, ветка cloud/recovery-search-editions-20261104.
Последние application checks41992767173/2486cbe:48 native без отказов,
оба канала Debug/Release/unit/build/lint прошли. Итог Preview красный только
из-за сборщика JSON после удаления тестового пакета Gradle. Исправлен5afee8e,
также разделены queueRequest и generation плеера для повтора законченной главы.
Локальные unit/native compile/lint прошли,3 fixture отчёта прошли.
ФИНАЛЬНЫЙ CI41997033226 проверить перед объявлением полного успеха.
Updater9:39 native прошли41991693857, настоящий GitHub APK дошёл до Installer;
установку не подтверждали. Этап10: последний том по формату, история/диагностика,
только коллекции. Подробности в тематических docs за2026-11-05.
Ub-mode одобрен на всю серию. Релиз НЕ публиковать; main/backup не менять.
Публичная4.2.93, физического теста новых изменений нет. Ниже — история.

# Этап 7 проверен, этап 8 проверяется; начат 9 (2026-11-05)

CI41988776548/816974f: оба канала Debug/Release/unit/build/lint успешны,
480 JVM на канал (2 skipped),34 native без отказов; lint0 Fatal/Error,87 Warning,
75 Info. См. docs/LOCAL-NEXT-VOLUME-2026-11-05.md.
Этап8/1b14455 проходит CI41989854360; локальные unit/native compile/lint успешны.
Этап9 сейчас разрабатывается: приложение владеет заданием updater, Activity
только наблюдает. Не оставлять заглушки UpdateSession как готовый результат.
Этап10 пока не начат. Ub-mode одобрен на всю последовательность. Не публиковать,
main/backup исходников не менять; публичная4.2.93. На телефоне не проверено.
Ниже — исторические записи.

# Этап 6 проверен; далее локальный следующий том (2026-11-05)

Ветка cloud/recovery-search-editions-20261104, production e621e42.
CI41957628713 прошёл оба канала Debug/Release/unit/build/lint:466 JVM на канал,
0 failures,2 skipped;31 native без отказов. Lint0 Fatal/Error,87 Warning,75 Info.
Этапы1–6 реализованы с ограничениями источников; docs/DOWNLOADS-2026-11-05.md.
Следующий этап7: локальный том до сети, приоритет таймера/ручной очереди.
Этапы7–10 ещё не завершены. Ub-mode одобрен на всю серию, повторно не спрашивать.
main/backup исходников и публичную4.2.93 не менять, релиз не публиковать.
Физического теста нет. Ниже — исторические записи.

# Проверены этапы4–5; следующий этап6 (2026-11-05)

Ветка cloud/recovery-search-editions-20261104; CI41948342440/2da7af5 прошёл:
оба канала Debug/Release/unit/lint,454 JVM на канал (0 failures,2 skipped),
18 native без отказов. Production8592c5d; следующие коммиты меняли harness.
Закладки/скорость/таймер готовы к дальнейшей интеграции, но не опубликованы.
Этапы6–10 ещё не реализованы. Следующий: выбор книг коллекции, место, ссылки
загрузок; сохранять кнопку всей коллекции, удаление аудио без потери прогресса.
Ub-mode одобрен на всю последовательность, не спрашивать повторно. main/backup
исходников не менять; новую версию/сайт не публиковать без отдельного разрешения.
Физического теста новых функций нет. Подробности в тематических docs, ниже история.

# Этапы4–5 проверяются, 2026-11-05

Текущий код58c2e44 в `cloud/recovery-search-editions-20261104`; CI41946517122.
Читайте docs/BOOKMARKS-2026-11-05.md и docs/PLAYBACK-OPTIONS-2026-11-05.md.
Этап4 unit448/без ошибок, оба канала Debug/Release/lint успешны41945750654;
native остановился на Compile Android tests. Подробная диагностика этого шага
добавлена в58c2e44. Исправления импорта/ProduceState уже учтены; новый индекс
remember+LaunchedEffect. Этап5 добавлен после локального red/green Java3/3,
CRAP2–4, Dryer0, mutations13 killed/1 equivalent. Полная проверка ещё идёт.
Этапы6–10 пока не реализованы. Публикацию не выполнять, main/backup не менять.
Одобренный ub-mode действует на всю задачу. Ниже — история, не актуальный итог.

# Продолжение 2026-11-05: закладки проверяются, выпуск не опубликован

Работа на `cloud/recovery-search-editions-20261104`, user-approved ub-mode для
всех десяти этапов и источников; не спрашивать повторно внутри этой задачи.
CI41885930067/958aa9d прошёл оба канала: Debug/Release unit/build/lint,
440 JVM-запусков без отказов (2 skipped),14 native без отказов. Этап3 ограничен
KB/App/XSPlayer и обновлением query того же CDN-файла; физического теста нет.

Этап4 реализован вafa34ac +0ea2243 +7571277: BookmarkStore, имена/заметки/редактор,
хешированные ключи с чтением старых, стабильные id и безопасный переход по главе,
общая вкладка библиотеки с IO и собственным счётчиком изменений. Читайте
`docs/BOOKMARKS-2026-11-05.md`. CI41944237994 проверяетad992b5, результат ещё
не проверен. Более ранний41943799087 полезен для первой диагностики;
промежуточный41943960333 отменён. Этапы5–10 не реализованы. План в
`docs/IMPROVEMENT-SEQUENCE.md`. Защищённые main/backup не менять, релиз и сайт
не публиковать без нового разрешения. Старый hostname updater всё ещё имеет
ограничение, описанное в BRIDGE-4.2.93.md. Ниже — исторические записи.

# Текущая работа: поиск, источники и надёжность (2026-11-04)

Ветка `cloud/recovery-search-editions-20261104`; main и backup не изменять.
Пользователь выбрал ub-mode для всей последовательности из десяти улучшений
и добавления источников Nova. Повторно спрашивать внутри задачи не нужно.
Читайте `docs/IMPROVEMENT-SEQUENCE.md` и `docs/SEARCH-EDITIONS-2026-11-04.md`.
Первый этап прошёл CI41829324122 (оба канала). Фильтр демо: CI41829923861
сборки/unit/lint прошли, native получил initializationError из-за не-void метода
нового теста; исправлен в680b801, повтор41831190181 прошёл полностью. Начат ограниченный refresh
ссылки главы (dbaf6aa, CI41831665408 остановился на unit/build; диагностика41884716149: несовместимый Files.readString в новом тесте; исправлен958aa9d; повтор41885930067). Результаты следующих прогонов проверять,
не объявлять их успешными заранее. Этапы4–10 ещё не выполнены.
Новый выпуск НЕ опубликован; разрешение предыдущего выпуска относилось к4.2.93.
Ноутбук не подключён к текущей среде; heartbeat2026-11-01T15:45:41Z,
резерв b62c2ea, omittedNewFiles пуст. Продолжаем более свежую облачную ветку,
не откатываем исправленный updater. Ниже — исторические записи.

# Published4.2.93; legacy hostname migration BLOCKED

Owner approved publication2026-11-04. Public GitHub preview-v100093/source6b41836
and Sites21/source736a98d are deployed. Read docs/BRIDGE-4.2.93.md and
publication.json. Native Sites canonical URL changed workspace suffix to smoc1983;
old amplecoot2048 host now HTTP307s to it. Known old updater forbids cross-host
redirect, so never claim transparent migration for that origin. Sites rejected
restoring its generated host as a custom domain. Phone100092 APK/origin remains
unverified. GitHub-origin clients can discover93; old-origin clients require the
platform to restore direct200 or a one-time same-package manual update.
Full anonymous GitHub APK and Site parts hashes verified. No new signing key,
no user-data deletion, no physical-phone installation, AudioBook main unchanged.
Current work branch cloud/publish-bridge93-20261104. Status below is historical.

# Current review status, 2026-11-04 — candidate93 NOT PUBLISHED

Public latest is preview-v100091/main c8301db; owner reports installed100092,
whose APK/origin is not available for inspection. Read docs/HANDOFF.md and
docs/BRIDGE-4.2.93.md before resuming. Branch cloud/candidate-bridge93-20261104 holds
signed100093 and a legacy-Site-to-GitHub bridge prepared WITHOUT ub-mode by the
owner's choice for this updater task. Runtime test/build/lint steps41763061062
passed; its candidate step rejected a stale version. Isolated candidate
CI41765003310 passed; publication requires separate owner permission.
No protected branches, live feeds, tags, APK releases or keys were changed.
Older status entries below are historical.

# Current Audiobook release:4.2.88/100088,2026-10-31

## Execution host preference (owner instruction, 2026-11-01)

Prefer the owner's connected Windows laptop DESKTOP-QG19B0C for development.
Before new or resumed work, read docs/REMOTE-DEVELOPMENT.md and check the actual
Codex host connection plus scripts/check-development-host.py. If the laptop is
unavailable, continue in a cloud Codex environment from backup/audiobook-laptop
on a new cloud/recovery-* branch, reporting the snapshot time and omitted files.
Do not overwrite local uncommitted work or push cloud work to the backup branch.
An offline local chat cannot migrate itself: do not claim autonomous failover.
The existing per-task ub-mode choice and release/signing safeguards still apply.

Compact player option2 and selected themes task completed WITH owner-approved ub-mode.
Full41220372143/sourcea13ac3c6955d95ad999b322ec425b57633d90ec5 passed.
Focused41221382589/source472ece78b20a80de21ea977abd9821e4264c9310 passed43/44;
onlyAndroidTest/docdiff, production paths identical. PublicSite20 serves100088;
upgrade85→88 retaineddata, originalpackage/certificatepreserved. Read latestdocs/HANDOFF.md,
docs/RELEASE-4.2.88.md,docs/PLAYER-DESIGN.md and docs/quality/PLAYER-DESIGN-LOG.md.
Before nextNEWdevelopmenttask ask owner's ub-mode choice. No further tests/builds for
unchanged completedwork. Nextnormal89 unless interveningruns;mainproofmerge[skip ci].
Hostingarchive231086080bytes;measure nextpayload against256MiB. Preserve user files.
Historical instructions/status below are superseded by this current header.

# Audiobook development instructions

Latest user preference (2026-10-29): before starting each application development
task, ask whether to use https://github.com/unclebob/ub-mode. Await the choice
before applying or bypassing that workflow. The user approved ub-mode for the
context-menu downloads / edition details task (2026-10-29).
The user also approved ub-mode for reliable book deletion and collection downloads
on2026-10-30; do not ask again within that combined task. Ask for the next task.
User approved ub-mode for inline collection continuations and simpler menus on2026-10-30; do not ask again within this task.
User approved ub-mode for book description on cover tap on2026-10-30; do not ask again within this task.
This supersedes the earlier unconditional ub-mode instruction below.

The nine library/player improvements task completed2026-10-30 as4.2.78/100078.
Owner explicitly declined ub-mode for this task; ordinary JVM/native/security/upgrade
checks passed. Before the next new development task ask the owner's choice again.
Do not rebuild/re-publish78 or rotate package/signature. See docs/LIBRARY-EXPERIENCE.md,
docs/RELEASE-4.2.78.md, docs/HANDOFF.md and exact docs/release-4.2.78/ evidence.
Current public Site18 serves100078; full41099446302 and focused41099442777 passed.
Compiled de9ae460371f4a165762f5cff23834168cbb129b. Native-header74/73 downloaded every
part and matched the signed APK SHA. Next normal raw78+permanentoffset1 produces79.
Physical phone, Android updater network APK download and PlayProtect remain untested.
Main release/proof merge uses[skip ci]; no extra application build is required.

The user explicitly requested ub-mode on 2026-10-27. For future code changes,
read and apply [.agents/skills/ub-mode/SKILL.md](.agents/skills/ub-mode/SKILL.md)
and [docs/QUALITY.md](docs/QUALITY.md). Treat this file as durable project memory.
Read docs/HANDOFF.md before resuming work. Keep short progress updates during work.

- Reproduce defects, fix causes, make small verifiable changes, test observable
  behavior, simplify duplication, and record checks and limitations honestly.
- Use the pinned Crapper, Dryer and Mutator for supported changed source files.
  Do not interpret an unsupported language or an empty scan as a passing gate.
  Kotlin and embedded JavaScript need the existing Android and JS tests.
- Start mutation checks with a specific file and a real passing test command;
  avoid a whole-repository mutation run or release publishing from mutant workers.
- Keep source edits separate from disposable tool caches and metrics. Preserve
  uncommitted work and existing user authorization. Never publish signing keys.
- Preserve the installed package/signing identity, downloaded library, preferences
  and collection data. Keep original stable Audiobook intact. Do not touch the
  ForTanya music repository while working on Audiobook.
- Skill practices do not grant permission to contact others or spawn subagents.
  Existing user instructions and higher-priority instructions take precedence.
- Before GitHub changes inspect the remote head: this checkout can have different
  local commit IDs with identical trees. Never blindly push or force-push main.

Task2026-10-31 completed as4.2.85/100085, WITHOUT ub-mode by explicit owner choice.
Full41176810494 and focused41176809415 passed; compiled09c9c7446ef8347c561f8ce5130780524bfa40dc.
Public Site19 serves100085 with preserved package/cert; upgrade78→85 retained data.
See docs/RELEASE-4.2.85.md, docs/SEARCH-COLLECTION-LAYOUT.md and release proofs.
Next normal86 unless intervening runs; merge/docs commits use[skip ci].
Ask the owner about ub-mode before the next NEW development task.
Current Site archive263976960 bytes is near256MiB: prepare verified retention before next publication.

Active task: compact player option2, bound to selected Audiobook themes. Owner approved ub-mode for this task. Do not ask again. Baseline85 preserved until verified replacement.
