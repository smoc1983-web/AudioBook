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

# Bridge candidate 4.2.93 — not published

Owner selected the third update-screen wording on 2026-11-04. Read
[BRIDGE-4.2.93.md](BRIDGE-4.2.93.md). Code a30451b prepares one original-signer Preview
APK for the old Site multipart feed and the GitHub complete-APK feed. Version100093
is strictly above the owner's screenshot100092; there is no published100092/APK
available for inspecting its actual origin or compatibility. Old Site20/source654f47c
still serves100088 and is unchanged. Runtime checks41763061062 passed (after one quota retry); candidate-only41765003310
passed with explicit version93. Final branch cloud/candidate-bridge93-20261104;
signed SHA249a1cc5173e7c454d08fe5647daaf93d2d784eeb6be46e95e717ebc712abf26.
Site review tar264222720 bytes preserves old files. No Release/Site
publication or protected-branch writes. Separate publication permission still applies.
Historical updater audit results below refer to the prior code01f7c25 stage.

# Review in progress, 2026-11-04 — GitHub updater, NOT PUBLISHED

Owner declined ub-mode for this task. Work is isolated on
`cloud/recovery-github-updater-20261104`, based on current public 4.2.91/main c8301db.
Read [GITHUB-UPDATER-REVIEW-2026-11-04.md](GITHUB-UPDATER-REVIEW-2026-11-04.md) and
[GITHUB-UPDATES.md](GITHUB-UPDATES.md) first. CI41759137706 / code01f7c25 passed both
channels: Debug+Release JVM/build/lint and seven API36 updater tests. Real public
release download and Android Installer confirmation tested; no package replacement
or physical phone test. No release/tag/protected-branch/site changes, no new key.
Publication requires separate owner permission. Existing public latest remains
preview-v100091; older .88/.89 need a one-time same-signer manual bootstrap.
Older release records below are historical, not the head of this audit branch.

# Актуально: Audiobook 4.2.88 / 100088, плеер варианта2

Задача завершена с одобреннымub-mode. [Описание](RELEASE-4.2.88.md),[поведение](PLAYER-DESIGN.md),
[журнал](quality/PLAYER-DESIGN-LOG.md),[проверки](release-4.2.88/verification.json).
Full41220372143 compileda13ac3c6955d95ad999b322ec425b57633d90ec5,treed5bd2329971ff7dd95654c9a48983bfc0ae347a6.
Targeted41221382589/source472ece78b20a80de21ea977abd9821e4264c9310:толькоharness/docdiff,
production paths exactlysame. JVM147each/1skip,native83/3skip,targeted43/44,noerrors;
lint0Error/Fatal,OSV68/0,upgrade85→88 retainedsettings/collection/folder/privateaudioSHA.
PublicSite20/source654f47c414809b0c04e3b6b8bb24c2c90acc34a7 succeeded.
URL https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site .
Nativeheaders85/78:metadata/all4parts200,fullSHA совпалсactualsignedAPK.
Передследующейновойзадачейспроситьвыборub-mode;текущуюнепереспрашивать.
Следующийnormal raw88+offset1=89 приотсутствиипромежуточныхrun. 86/87непубликовались.
Не менятьpackage/cert,неудалятьприложениеилиuserfiles,неприменятьстарыйstashповторно.
Sourceproofmergeиспользует[skip ci];нетнеобходимостипересобиратьнеизменённыйAPK.
Site20архив231086080/268435456:nextpayloadmeasure,один≈33MBAPKможетпоместиться.
Внешняяbackupunreferenced72/73сохранена,proofhosting-retention.json;85/78/74/manual69/70/71retained.
НафизическомS24/AndroidnetworkAPK/PlayProtectнепроверено. SDK23WAVfixture−38,
успешноевоспроизведение23незаявлено,UI/errorstateпротестированы;serviceunchanged.

---

# Актуальный выпуск: 4.2.85 / 100085, 2026-10-31

Текущая задача завершена без ub-mode по явному выбору владельца.
Полный41176810494 и targeted41176809415 прошли; compiled09c9c7446ef8347c561f8ce5130780524bfa40dc,
treefe437283f8e55df6de23c3fe86bf4d09c194c2f5. Пакет/cert прежние; upgrade78→85
проверен с сохранением данных. [Описание](RELEASE-4.2.85.md),
[поведение и ограничения](SEARCH-COLLECTION-LAYOUT.md), [доказательства](release-4.2.85/verification.json).

Public Site19 успешно опубликован из86a0559b0332ea071b5cc5bd645ed67a1380180c;
URL https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site .
Внутренние79–84 не публиковались; не переиспользовать их номера.
Следующий normal raw85+offset1 создаёт86 при отсутствии новых запусков.
Перед новой задачей спросить выбор ub-mode; текущую не переспрашивать.
Не ротировать ключ/пакет, не удалять приложение или пользовательские файлы.
Перед следующим Site-выпуском требуется retention: архив19 уже263976960/268435456.
Не удалять ссылки актуальных manifest/phone-страниц без анализа и внешней копии.
Native HTTP заголовки78/74: metadata и все4части200, fullSHA совпал.
ФизическийSamsung/Android networkAPK/PlayProtect не проверены.

---

## Audiobook4.2.78 опубликована · 2026-10-30

Девять улучшений библиотеки/плеера завершены. Для этой задачи владелец выбрал **без
ub-mode**; выполнены обычные JVM/native/permission/lint/advisory/upgrade проверки.
Для следующей новой разработки спросить выбор снова. Не повторять сборку/публикацию78.

Compiled de9ae460371f4a165762f5cff23834168cbb129b,
tree d69ec76699143f653bc9a74cbcbe88379510ce01. Каноническийmain содержит тот же код
и последующие release/proof документы. Full41099446302/job124213990045 success;
focused41099442777 success (23:33/33,36:34/34). JVM147pervariant/skip1,nativeXML75/
skip3,Lint0Error/Fatal,OSV68complete/0known findings. Отдельно проверен actual signedAPK
и definedDEXmethods, debugfixtureabsence, unchangedpermissions. ActualWi-Fi-only
DownloadManager HTTP bytes/reload/cancelpreserve test passed36. Upgrade74→78 сохранил
контрольные настройки/коллекцию/папку/privateaudioSHA. PhysicalSamsung/actualAndroid
networkAPKdownload/PlayProtect не проверялись. Не обещать отсутствие всех уязвимостей.

APK100078/name4.2.78,labelAudiobook,33052272bytes,
SHA256cd449e8fec5806459002178e636dffd57362212b1a3f67e18dd5b5a84d26655b,
packagecom.audiobookdownloader.preview,cert3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7,
v1+v2,nondebuggable. Originalstable packagecom.audiobookdownloader не трогать;
не удалять установленную новую Audiobook и не очищать данные для обновления.

ExistingpublicSite18 succeeded,source60f72b668dcbd87075bfa3507e3e11716cc01db7,
savedappgprj_6add1a69b6f481918b5e390c8f413992~appgver_4b683e133e288191829999b9897dd9d7,
deploymentappgdep_6ae51e1fcd908191867217560824c257.
https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site
Metadata100078 и все4chunks HTTP200 с nativeheaders74/73, полныйSHA совпал.
Архив227950169compressed/230901760normalizedbytes/36files <268435456limit.
Только unreferenced66/68 chunks предварительно сохранены/проверены и убраны из
активного hosting payload. Current74/73/72/manual69/70/71 сохранены; userbooks не затронуты.

[Функции](LIBRARY-EXPERIENCE.md),[выпуск](RELEASE-4.2.78.md),
[проверки](release-4.2.78/verification.json),[публикация](release-4.2.78/publication.json).
Memory smoc1983-web/ai-project-memory обновляется отдельно после этогоmainmerge.
Локальный stash preserve-local-queue-guards-before-remote-recovery сохранён; изменения
из него уже вошли в remote code, не применять его поверх свежих исходников повторно.

Во время задачи exec временно отсутствовал; изменения были продолжены GitHubAPI.
Теперь exec работает. Промежуточные75/76 не опубликованы (исправлены physical-refresh
UI и3lint findings),77 superseded/cancelled при добавлении явного NETWORK_WIFI.
Следующий новый normal raw78+permanentoffset1 даёт79. Main release/proof merge
использует[skip ci], чтобы не создавать лишний79 и не запускать отклонённые ub-mode tools.
Ключи/credentials не сохранять вGitHub. ForTanya/SimpMusic не часть этой задачи.

### Исторические завершённые этапы

## Audiobook4.2.74 опубликована (2026-10-30)

Описание книги по нажатию на обложку готово и доступно через существующее меню
обновлений. Пользователь разрешил ub-mode для этой завершённой задачи; перед
следующей новой разработкой спросить снова. Не пересобирать74 и не повторять
публикацию ради изменений памяти. Следующий normal run74+offset1 создаст75/100075.

Код: PR6, compiled8528d13e51db5b8565163ff80921cf21021fa3d6,
tree96472954858a70fcd38daa4ba0afe8332b3d6f27. После него менялись только документы,
публикационные материалы и отдельные audit workflows. Проверять remote HEAD перед
любым push: местные и remote IDs исторически различаются при одинаковых trees.

Common AbCover открывает cancellable dialog, AbPoster title — отдельное44dp
прослушивание. Source URL/title/author identity, очистка рекомендаций до сверки,
ограниченный JSON/HTML parser;80files/128KiB private cache,20k text,striped locks,
atomic writes,20s deadline. Offline и failed refresh сохраняют текст; смена URL
не закрывает окно. Audio/public book folders не записываются. Если источник
не дал описание, показано понятное сообщение. Не менять deletion/generation locks
и collection-only notifications; original stable и ForTanya не затронуты.

Scoped41014370607:15/15 API23 и36. Normal41015767058: JVM143/skip1 pervariant,
Android main61/skip3opt-in плюс3upgrade phases; Lint0Error/Fatal,74Warning/67Info;
OSV68modules complete,0known findings. Java parser11/11, freshJaCoCo,CRAP<10,
Dryer no>=0.8,3 targeted guard mutants killed (не whole-file score); Kotlin N/A.
Actual signed APK41018067403: parser/repository/model DEX classes,dialog,notes,
нет debug fixtures,permissions прежние. XML recovery41018566005 не rerun app tests.
Upgrade73→74: старый updater принял APK и открыл system confirmation; adb-r
сохранил контрольные settings/collection/private audioSHA. Physical Samsung,
actual Android network download и PlayProtect verdict не проверялись.

APK32986390bytes,SHA256876b5739e36c41340177534d2766be812cf597959dfa12b24b822ffc0ac01d5d,
packagecom.audiobookdownloader.preview,labelAudiobook,version100074/name4.2.74,
cert3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7,v1+v2,nondebuggable.
Private releasepreview-v100074,APK artifact12959607904. Подпись не менять,
пользователю не предлагать удаление приложения или очистку данных.

Siteappgprj_6add1a69b6f481918b5e390c8f413992,public,version17;
source7fb585db3dca693f081cf33e2b9ce1895be08a2a;
savedappgprj_6add1a69b6f481918b5e390c8f413992~appgver_b48fcc0d3d248191b0f406bacb3f13f8;
deploymentappgdep_6ae4495612bc81919ea8f02c188aa3ed succeeded.
https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site
Feed/updates/preview-update.json=100074. Native-header73/72 HTTP200 metadata/allparts,
fullSHA matches APK. No browser QA for static notes; download.js unchanged.
Archive compressed259692395/raw normalized263065600/40files,limit268435456.
Only about5MiB spare: next APK needs measured packaging/old-hosting-asset planning;
user book files are unrelated and must not be deleted. Existing73/72/manual assets retained.

Источники и доказательства: [release](RELEASE-4.2.74.md),
[verification](release-4.2.74/verification.json),
[publication](release-4.2.74/publication.json),[HTTP](release-4.2.74/public-check.json).
Память: smoc1983-web/ai-project-memory,projects/audiobook/HANDOFF.md,state.json.
Права на разработку/публикацию прежние; не отправлять ключи/credentials в GitHub.

## Owner-requested Actions archive cleanup (2026-10-30)

The owner explicitly selected old GitHub Actions archives only. Inventory41034007926
found348 archives/2566235131bytes. Cleanup41034327218 deleted exact215 old IDs,
2162324626bytes;133 retained archives/403910505bytes all remain. Kept latest3 per
exact name, all today and signing-recovery. Releases, source/history, caches and
signing materials untouched; no scheduled automatic deletion. Historical artifact
IDs in older docs may now be absent; saved proof snapshots/releases/history remain.
Receipt: actions-archive-cleanup-2026-10-30.json. Maintenance branch
maintenance/actions-storage-20261030,commitfb1227bdc3160ec014d8d04744b2169c35f016a5
contains the bounded one-time workflow; no app build was started by cleanup.

## Historical released73 context

## Audiobook4.2.73 опубликована (2026-10-30)

Задача inline-продолжений и понятных меню завершена. PR5 merged,
compiled sourcee403c9e9100d62ed6f7b3481f01fe89c124a04e4,
treecfd0c4958dedf9cbc8a33da5da5ef2680b2dfba0. Final scoped41005118622:
20/20 наAPI23 и36; normal41005934995 success. [Выпуск](RELEASE-4.2.73.md)
и [доказательства](release-4.2.73/verification.json).

AbCollectionBookCard/AbCollectionShelf раскрывают AbCollectionContinuations
внутри полки; выбранная полка имеет такую же кнопку. AbOfficialSeriesPanel(compact)
переиспользует существующий каталог, авторов, цикл/порядок, cache и конфликтные
предупреждения. Source entries привязаны к этой книге даже при mute уведомлений;
filename siblings не объявляются подтверждённым циклом. LazyRow показывает все
книги, длинные панели по5. Серии не запрашиваются при обычной прокрутке закрытых
карточек. Меню полок подписано, дополнительные действия книги свёрнуты.
Delete/download guards предыдущего72 сохранены, файлы/настройки не мигрируются.

Не повторять завершённую публикацию. Следующий normal run73+offset1→74.
Перед следующей новой разработкой спросить ub-mode. Не ротировать package/cert,
не удалять stable. Site прежний, source/audience сохранены; без нового проекта.
Backup+проверка unused62/63 позволили убрать9старых chunks;72/manual69/71 оставлены.
Physical Samsung и реальная Android network загрузка не проверялись.
Ниже исторические checkpoints72 и более ранних выпусков.

## Audiobook4.2.72 опубликована (2026-10-30)

Текущая задача завершена. Скачивание коллекций и надёжное удаление аудиофайлов,
включая race фоновых cover writes, реализованы. [Выпуск](RELEASE-4.2.72.md),
[машинные результаты](release-4.2.72/verification.json). PR4 merged289d6c2cc69b9acadf52049c6799cec807a96c73,
treeff9f09aae0afcd986f6291cacfdf06adeadba2d5. Scoped40991356902 и normal40992606141
успешны. Signed72/100072,32970042 bytes,SHA31e65328fe69d3375f771e7d46ea25a37c74d40ce7aa3513110f2cbe41aee5b4;
package/cert прежние, upgrade71→72 в эмуляторе сохранил контрольные данные.
Site15 deploymentappgdep_6ae4075d038081919f4633de657e577f succeeded,
source4ab34e721a614569cdc460027abc0a24a8da59e4; HTTP metadata/parts/full SHA для
клиентов71/70 проверены. Физический Samsung и Android network download не проверялись.
Далее ждать новой задачи/phone feedback; перед новой разработкой спросить ub-mode.
Не повторять завершённую публикацию. Следующий normal run72 через offset1 даёт73.
Site raw archive266813440 bytes: следующий новый APK потребует безопасного
удаления unreferenced old chunks после проверки всех refs и backup; текущие72,
manual69/при возможности71 сохранить. Ключи не публиковать, stable не удалять.

Ниже исторические checkpoints, включая стадии до завершения72.

## В работе: готовится выпуск удаления/скачивания коллекций (2026-10-30)

Профильный CI40991356902 прошёл final source4806b2b2362ef625002f10cc1fa0452accf226e6:
API23 18 total/1skip, API36 18/18; JVM132/1skip, Lint0Error/74Warning/63Info.
Журнал docs/LIBRARY-FILE-ACTIONS.md и machine receipts library-file-actions/.
PR4 готов к merge; после него normal run71 через offset1 должен произвести72.
До успешных release/upgrade/identity проверок feed остаётся100071.
Разрешение на ub-mode получено для обеих текущих доработок, повторно не спрашивать.
Физический телефон не проверялся. Ниже предыдущий завершённый выпуск71.

## Audiobook4.2.71 опубликована (2026-10-29)

[Выпуск и ограничения](RELEASE-4.2.71.md), [проверки](release-4.2.71/verification.json).
Меню «⋮» получает сведения об издании отдельно от плеера: скачать/дозагрузить,
число глав, полный известный либо частичный/неизвестный размер. Явные фрагменты
блокируются; непомеченная неполнота автоматически не доказывается. Плеер и его
выбранная книга при этом не меняются. Сырые URL с пробелами не срывают чтение.
PR3, merge bd1486fcd3a6f9ea607707aa876b1861f28beb8b, tree d60cc32d528ebebff74995406310a57482ba462f.
Normal40926084946 success; scoped40924074169 success, quality40926084939 success.
APK100071/32953564/SHA2121d367e800d9fc15b6c62e8111692eb3781a22efb828c27777e07560e2af01.
Пакет/подпись прежние. Upgrade70→71 в эмуляторе сохранил контрольные данные.
Site14 deployment appgdep_6ae38d06bd208191a79e217cae0cd2df succeeded; клиенты70/69
получилиHTTP200 и проверенные4 части/общий SHA. Установка71 пользователем ещё не
подтверждена. Следующий normal run71→72 через существующий wrapper, не удалять.
Перед следующей задачей разработки спросить о ub-mode; на завершённую эту задачу
согласие было получено. Ниже история стадий; pending-описания не актуальны.

## Контекстное скачивание и сведения об издании (2026-10-29)

Пользователь выбрал ub-mode для этой задачи. Для каждой следующей задачи сначала
спросить о его применении; новое правило в AGENTS.md заменяет прежнее безусловное.
Добавлены AbMenuEditionDownload/AbReadDownloadEdition и EditionDownloadRules: меню
читает главы отдельным сеансом, показывает число/размер без переключения плеера;
сохраняет metadata только при загрузке через прежнюю очередь. Явные фрагменты
заблокированы. Размер/число файлов не считаются доказательством полноты.
[Журнал](MENU-DOWNLOAD.md). Ветка feature/menu-download-edition.
Исходный remote e42aff5c4c6e1a236a99e5e9868f402e6364f16e: CI40921942040 success,
Android23/36 по6/6 и JVM131 (1 skip), Lint0 Error. Затем raw URL regression
выявила URI.create на пробелах; исправлена без строгого разбора URI,5/5 локально.
Remote55f48c30cbe57fcf6fe04cfff9f27ec98ca65d5b; CI40924074169 success: JVM132 (1 skip), Android23/36 по6/6; Lint0 Error.
Crapper Java max2 на100% JaCoCo, Dryer без>=0.8,1 mutant killed; Kotlin N/A.
Подготовлена обычная сборка следующего номера71; публичный feed пока100070.
После normal pipeline записать фактическую сборку/подпись/upgrade и доставку.

## Опубликована Audiobook4.2.70 через меню обновлений (2026-10-27)

Пользователь явно разрешил публикацию после исправления уведомлений.
[Выпуск и проверки](RELEASE-4.2.70.md), [машинные результаты](release-4.2.70/verification.json).
PR2 merged main source3bf976e4af88d46a7ee045fc88a5a0ea0e364657, treeb8106417.
Normal CI40552492346 run69 success публикует100070 через wrapper offset+1.
Прежние пакет com.audiobookdownloader.preview и cert3a4cecd1… сохранены.
APK32937225 байт, SHA4c1096ab3ceb681a9910c4275ae6da85140f56b6b01a6151170a788e3e0ea6ef.
Site13, sourcecc6bce5b43dad57cfbaaaa404c970a02e05edad9, deploymentappgdep_6ae0e6629fac8191bc41237b564ed920 succeeded.
Feed preview-update.json теперь100070. Заголовки клиентов68/69 дают HTTP200,
каждая из4 частей и общий SHA проверены. Старый updater68 открыл подтверждение;
замена на release70 в эмуляторе сохранила полки, настройки, URI и аудиоSHA.
Ручная69 остаётся в собственной странице; основной сайт/меню выдают70.
Посторонние глобальные Litres notifications прекращены, legacy inbox очищается.
Старый com.audiobookdownloader не тронут. Физический Samsung не проверялся.
Следующий normal run70 выпускает71: постоянный offset+1 не удалять, не заменять
опубликованную70 другим содержимым с тем же номером. Ключи не публиковать.

Ниже история стадий до разрешения; прежние hold-описания относятся к тому моменту.

## Исправление области уведомлений после ручной 4.2.69

Рабочая ветка fix/collection-notices-only. [Причина и изменение](COLLECTION-NOTIFICATIONS.md).
Глобальный LitresReleaseScanner удалён: теперь официальный audio baseline по прямой
серии коллекционной книги. Виртуальный seed «litres» больше не разрешает уведомления.
Legacy global inbox удаляется, posted уведомления отменяются без влияния на плеер;
текущие коллекции фильтруют inbox, route и send. Текстовый v2 baseline сохраняется.
Android23/36 CI40550108807 success: по11/11; Debug JVM127 (1 optional skip).
CI40548734022: notification tests passed, one cold-search assertion in each API
failed due test-cache carryover; fixed isolation, assertions kept.
Итог в collection-notifications/verification.json.
Publication hold продолжается: main, in-app feed100068 и phone APK69 не менялись.

## Ручной APK 4.2.69 для телефона (после аудита)

Пользователь разрешил прямую ссылку, без повторных тестов. См. [PHONE-CANDIDATE.md](audit-4.2.68/PHONE-CANDIDATE.md).
Доставка только в delivery/audit69-phone; main/release/in-app feed остаются прежними.
Номер100069 выдан вручную; дальнейшее обновление владельца требует >=100070.

## Проверка 4.2.68 — публикация запрещена до разрешения пользователя (2026-10-27)

Рабочая ветка audit/4.2.68-ub-mode-hardening, не main. [Отчёт](AUDIT-4.2.68.md).
Hold также в AGENTS.md. Не публиковать APK/релиз/feed/Site и не сливать main
до явного разрешения. CI-кандидат 100069 не распространяется; опубликована 100068.

Исправления: jsoup 1.23.2, потоковый XML импорт, мобильные внешние ссылки AT,
позиции/пагинация, все полученные авторы до take(3), общий текстовый baseline,
тихая миграция official-text-v2 (старые уведомления сохраняются), один DOM,
WebView isolation, независимые временные APK, точечная совместимость Java API с Android 23,
константы ориентации и состояние Compose. Jsoup на API23 требует сохранённый
coreLibraryDesugaring 2.1.5. Отдельный upgradeProbe не содержит копий приложения/j$
и выравнивает AndroidX ABI с установленным target.

Прежний build-apk сохранял private key в artifact/cache. В audit-ветке новые такие
копии запрещены: artifact upload удалён, cache только restore. Старые копии не удалять
до гарантированного постоянного Actions secret AUDIOBOOK_PREVIEW_KEYSTORE_BASE64.
Аудит ключ не скачивал и не публиковал. Без доступного исходного ключа сборка обязана
остановиться, сохранив возможность дальнейшего обновления установленных приложений.

Lint впервые показал 18 ошибок; зелёный continue-on-error не означал чистый XML.
scripts/check-android-lint.py и его gate + OSV добавлены перед публикацией build-apk.yml.
Не путать Error/Fatal с оставшимися Warning/Information.

Два XML mutation survivors эквивалентны из-за независимых запретов DTD/entities;
не удалять эти настройки и не тестировать исходный текст ради процента.
Шесть чистых Java-компонентов проверены JaCoCo/Crapper, max CRAP 9.0;
это не покрытие всех Android/Kotlin классов. CI — audit-4.2.68/verification.json.
Итог: Android23 7/7; Android36 31 (3 opt-in skip) + 3 отдельные фазы реального
release upgrade, всё без ошибок. Full run40474299508 attempt2 success; attempt1
системный ANR эмулятора. Debug/Release JVM по123 (1 необязательный skip), Lint0Error
при76Warning/59Information; OSV68 зависимостей без найденных advisories.
Обновление не опубликовано. Перед публикацией проверить постоянный signing secret.

## Правила следующих изменений: ub-mode (2026-10-27)

По явной просьбе пользователя сохранены AGENTS.md, точный upstream SKILL.md
и docs/QUALITY.md. Crapper/Dryer/Mutator устанавливаются закреплёнными версиями
через scripts/quality-tools.py install. Ограничения поддержки и интерпретации
метрик обязательны: Kotlin не поддерживается, N/A не означает прохождение gate.
Отдельный quality workflow не выпускает APK и не имеет доступа к ключам.
Приложение остаётся 4.2.68; настройка инструментов не требует его обновления.

## Текущий выпуск 4.2.68: официальные текстовые и аудиопродолжения (2026-10-27)

В плеере → «Серия» и настройках продолжений добавлен каталог Литрес/Author.Today. [Реализация и ограничения](TEXT-SERIES-CATALOG.md), [проверки](verification/checks-100068.json). Новые OfficialSeriesParser.java и OfficialSeriesCatalog.kt: отдельная проверка точного произведения и автора в каждом справочнике, собственная прямая серия, её явный список, форматы/публикация/даты. Хранятся files/official-series-v1 (до 80 JSON по 1 МиБ), prefs official_series_v1; чтение и сеть вне UI. До 6 страниц/500 изданий, запросы только по той же серии. Повторная сверка заново читает известную страницу, затем при необходимости ищет. Нераспознанные страницы пагинации/ещё означают неполный список. Предыдущий список сохраняется при ошибке. Глобальная фоновая проверка — 2 коллекционные книги за проход с курсором и отдельным HTTP-бюджетом; кнопка конкретной книги принудительная.

SeriesAuthorityParser поддерживает мобильные work-row-content/work-header-content, поиск со ссылкой на a.work-row и абсолютные собственные ссылки. Проверка провайдера не позволяет чужому домену стать AT/Litres серией. При противоречии прямой серии или нумерации данные показаны отдельно/с предупреждением, текстовые уведомления заблокированы. Текст и аудио одной работы объединяются по прямой серии+автору+названию. Карточки открывают официальные страницы браузером; платный текст/аудио не извлекается.

ContinuationNotice получил format с default audio для старых записей. continuations_v1.text_enabled по умолчанию false. Первая полная успешная проверка текста, включая пустой набор продолжений, — тихая; далее только опубликованные/пишущиеся новые тома с явным номером после текущего. Предзаказы, неизвестный порядок, неполные/спорные списки, отключённые или удалённые подписки не создают оповещений. Нажатия текстовых и официальных аудиокниг уходят во внешний браузер, не в аудиопарсер. Старые отметки/полки/аудиофайлы не менялись.

CI run 40454108463, job 122023311172, attempt 1 successful. JVM Debug и Release: 105 passed + 1 optional skip каждая, в том числе 8 новых parser tests. Android 36: 27 total, 24 passed / 3 opt-in skips; все три opt-in выполнены отдельно. Три новых теста подтверждают обе площадки, baseline без новинок, новый текст/дедупликацию/офлайн/неполноту/отписку, конфликты и реальные фильтры Compose. Переход release 66→68 сохранил полки/настройки/URI/приватный маркер/WAV SHA-256; старый verifier и настоящее окно Android приняли APK. Run 67 не публиковался: новые тесты выявили отказ старого селектора читать абсолютную собственную ссылку. Исправлены селектор и ожидание асинхронного UI, проверки не отключались.

APK /workspace/deliverables/Audiobook-Preview/Audiobook-4.2.68.apk: 32626692 байта, SHA-256 06fc6db6c81d085c575f5647a80635c2ff01f025a64aff613e28403f68d229cb. Package com.audiobookdownloader.preview, cert 3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7, release non-debuggable, v1/v2 verified, 16 KiB alignment, root DEX 104 registers, 14645 classes. Дополнительных разрешений нет; security gate сохранён. ForTanya и старый stable package не менялись.

Site version 11: source d61150524b322e266105821f0bbac551b07e6a2c, saved appgprj_6add1a69b6f481918b5e390c8f413992~appgver_b1b55a4b7a888191bc73b61a82bd38ef, deployment appgdep_6ae0371756e8819186e31322aa4906e0 succeeded. Публичный доступ и origin прежние. Из-за известного предела 256 МиБ удалены только старые части 100050 из активного dist; история Git/Sites и GitHub release их сохраняют. Parts 53/55/57/62/63/66/68 оставлены. Old UA62 с точными Accept/identity headers загрузил все четыре части и проверил общий SHA. Живой Chromium сохранил APK и ZIP, локальные retry/corruption/wrong-package проверки прошли. Stable feed остаётся unavailable.

Live metadata: AT series 7666 — 4 текста; Litres series 714498 — 4 текста+4 аудио, Gold — прямой Хранитель кладов #2. Широкий поиск может не найти точную книгу на первой странице: отсутствие подтверждения не означает отсутствия томов. Нативный reference transport в тестах подменён fixture-fetcher; физический Samsung, настоящая загрузка сетевым Android-updater и вердикт Play Protect не проверялись. DNS-сбой телефона из предыдущего обращения удалённо не исправлялся; не заявлять обратного.

## Доступ к обновлению 4.2.62 (2026-10-27)

Поступили HTTP 401/403 и затем явный DNS `Unable to resolve host`. Хостинг public, feed 66 и точные заголовки 62 из исполнителя дают HTTP 200; оба публичных DNS возвращают адрес. APK 62/66 совпадают по пакету/подписи. Сеть телефона не исправлена удалённо; не выдавать серверные проверки за физический OTA-тест. Приложение и feed не менялись. [Диагностика и действия](UPDATE-NETWORK.md).

## Текущий выпуск 4.2.66: прокрутка (2026-10-26)

Подписанная release 4.2.66 / 100066 опубликована в прежний канал, package и cert не менялись. [Изменения](SEARCH-SCROLL-PERFORMANCE.md), [проверки](verification/checks-100066.json). `AbSearchRows.kt` готовит карточки и индекс скачанных книг в Dispatchers.Default; `AbSearchScreen` сохраняет снимок по результатам/библиотеке/ревизии серий, вместо пересчёта всей выдачи при тиках плеера. Не измерять пустую LazyColumn до первого снимка: иначе потеряется восстановленная позиция. `AbSearchResultCard` — одна карточка с полным содержимым и постоянным URL/contentType. Сверка прямых серий и порядок томов сохранены. SHA-ключи серий совместимы со старым хранилищем, LRU 1024. Обложки ждут остановки прокрутки; фиксированы 32+32 блокировки и TTL промахов 30 с / 256 записей, загрузки отменяются при чтении. Обычный скроллинг рисуется без записи экрана; для действительного загиба `AbPageTurn` ждёт два кадра перед GPU readback. Проверки текстурного перелистывания прошли.

CI run 40377566746 / job 121764691202 успешен: JVM 97 passed + 1 optional skip на каждый вариант, Android 36 — 21 passed / 3 opt-in skips; все три отдельные проверки release→release выполнены успешно. Старый 4.2.63 verifier и настоящее окно Android приняли 66; замена сохранила полки, настройки, URI, приватный маркер и WAV SHA-256. `run-as` для release не используется: `UpgradeCandidateTest.seedInstalledUpgradeData` переносит только тестовые файлы через FD UiAutomation в одноразовом эмуляторе, с прежней подписью и SHA-проверкой. Этот метод отсутствует в release DEX. Run 65 прошёл общую нативную часть, но не мог подготовить release baseline через старый run-as; исправлена инфраструктура, проверки сохранности не отключались.

600 результатов: подготовка 336 мс вне UI, максимум 12 активных карточек; вертикальный свайп, переход до последней книги, открытие/возврат, расширение 300→600 без потери позиции прошли. Root DEX 104 регистра, 16 КиБ alignment, разрешения по прежнему gate. APK 32593767 байт, SHA-256 4ca319ad2f1fbedc8acb09938e1d8fb59ec4a148c99509a9c4a837a8f0d14055, cert 3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7. Локальная копия /workspace/deliverables/Audiobook-Preview/Audiobook-4.2.66.apk.

Публичный Site version 10, source bec9a5219986c2b3d09daefa1279e9ead5804779, saved appgprj_6add1a69b6f481918b5e390c8f413992~appgver_44c5f025e4c48191a2e6c62678b05a0d, deployment appgdep_6adf9f6a3eb48191874950efbed47b4e succeeded. Доступ public сохранён. Site отклонил архив более 256 МиБ: из текущего dist удалены только 5 частей 100048; они остаются в Git/Site history и GitHub release. Части 50/53/55/57/62/63/66 сохранены, feed 66 и stable unavailable. Старый UA 4.2.63 полностью загрузил/сверил все четыре части; публичный мобильный Chromium сохранил APK и ZIP с тем же SHA, локальные retry/corruption/wrong-package проверки прошли. Физический Samsung/FPS, собственно сетевой загрузчик внутри Android и вердикт Play Protect не проверены; не выдавать проверки компонентов за эти результаты. ForTanya не менялся.

## Изменение после сообщения Play Protect (2026-10-26)

Текущий устанавливаемый APK должен быть release без отладки, с прежними `com.audiobookdownloader.preview` и сертификатом. [Разбор](PLAY-PROTECT.md). Не возвращать в main службу специальных возможностей для чтения старых экранов, Shizuku, ILegacyCollections или чтение чужих приватных настроек. Они выведены в history/privileged-collection-transfer-4.2.62; старые инструкции ниже — история разработки, не активная функция.

Перенос через файл: [актуальная инструкция](COLLECTION-TRANSFER.md), CollectionTransferActivity/Store/Rules. Gate scripts/audit-installable-apk.py проверяет манифест и реальные DEX-классы публикуемого release. Native PermissionSurfaceTest проверяет установленную поверхность API, UpgradeCandidateTest — переход debug 4.2.62 → release с сохранением полок, настроек, URI, приватного файла и WAV SHA-256. AOSP CI не содержит Google Play Protect и не может подтвердить принятие APK Google. Не выдавать аудиты за такой вердикт и не просить отключать защиту. Если блокировка останется, официальный следующий шаг — проверка/апелляция Google по новому APK; автоматически она не подавалась.

## Продолжить работу с сериями и уведомлениями (2026-10-25)

Новая разработка сохраняет пакет `com.audiobookdownloader.preview`, подпись и библиотеку установленного нового канала. Стабильный старый пакет не меняется. См. [сверка серий и продолжения](SERIES-VERIFICATION.md).

Основные файлы: `SeriesAuthorityRules.java`, `SeriesAuthorityParser.java`, `SeriesAuthority.kt` — публичные справочники Author.Today/Литрес и кеш прямой серии. `KnigobludSeriesParser.java`, `SeriesContinuationRecovery.kt` — явно указанный цикл, восстановление старого адреса, строгий источник при отсутствии совпадения в справочнике. `LitresReleaseParser.java` и `LitresReleaseScanner.kt` — отдельная подписка на новые аудиокниги, проверка даты и тихая исходная выборка. `ReleaseNotesActivity.java` и `assets/release-notes.json` — изменения после обновления.

Основные настройки серии остаются в `audiobook_downloader` (`book_series_authority_v1_*`, `book_series_revision`). Отслеживание, список доступных томов и уведомления — в `continuations_v1`; исходные снимки — в `files/continuations-v1/`. `available` отделён от `inbox`; не создавать уведомления из локальных совпадений названий и не обнулять снимки при сетевой ошибке.

Нативный тест `SeriesRecoveryTest` проверяет пропавший адрес, уже вышедший второй том, отделение чужой подсерии, новый третий том, повторы, сбой источника, отключение отслеживания и исходную выборку Литрес. Короткие тестовые файлы эмулятора передаются из обычного файлового дескриптора, с ограниченными повторами и обязательным SHA-256; stdin PIPE мог терять небольшой XML. Проверка `scripts/test-ci-file-transfer.py` не обращается к устройству.

# Продолжение разработки Audiobook Preview 4.2

Код и история книг находятся только в smoc1983-web/AudioBook; музыкальные изменения — в smoc1983-web/ForTanya. Актуальная ветка книги: main; рабочая ветка integration/verified-sources объединена через PR #1.

Канонические исходники экрана и сервиса: current/MainActivity.kt и current/PlaybackService.kt. Gradle syncCurrentSources копирует их в build/generated перед компиляцией. Дополнительные классы/ресурсы current/app/src/main подключены напрямую; новые адаптеры и совместимость старого виджета — в app/src/main/java/com/audiobookdownloader.

Активны 13 источников: прежние четыре и девять дополнительных со скриншотов. Lis10Book, Слушкин Всем и Audioboo удалены по следующему запросу пользователя. AbExtraSources и abExtraSourceJs используют общий реестр; сохранённые настройки выбора очищаются от удалённых записей с сохранением остальных предпочтений. Подробности — SOURCES-13.md. Историческое исследование версии 34 находится в SCREENSHOT-SOURCES.md. LibriVox/Internet Archive не возвращались в активный поиск; совместимость старой локальной библиотеки сохранена.

AbPageTurn.kt записывает страницу через rememberGraphicsLayer/toImageBitmap; при недоступном GPU readback рисует Compose subtree непосредственно в software Canvas. Не вызывать drawRenderNode при software-выводе. CylinderPageMesh.java деформирует сетку 64×96 по цилиндру; PageCurlRenderer.java рисует BitmapShader отдельными пакетами front/back, затем бумагу/тени. Обратная сторона должна перекрывать лицевую независимо от порядка строк. Snapshot и shader освобождаются после перехода. Жест учитывает высоту касания/вертикальное смещение, короткое движение отменяется, кнопки используют тот же эффект. Нижняя навигация неподвижна, header/мини-плеер входят в лист. SaveableStateHolder сохраняет разделы. В плеере прямое действие «В коллекцию» открывает общий AbShelfPicker. Источники, ограничения и история проверок: CYLINDRICAL-PAGE-CURL.md.

SleepShakeDetector.java обрабатывает физическое ускорение на временных метках датчика, отделяя гравитацию. PlaybackService регистрирует wake-up-акселерометр (fallback обычный) на 50 Гц без пакетирования. Два отдельных импульса перезапускают выбранную длительность с защитой от повторов. Состояние публикует shakeListening и sleepRestartedAt; в логах есть регистрация и перезапуск. Подробнее о порогах, проверках и отсутствии физического S24: PLAYER-CURL-SLEEP.md.

Ярлык: полумесяц, Play и открытая книга, PNG в app/src/main/res/drawable-nodpi/ab_launcher_crescent_art.png. Adaptive v26/v33: относительный inset18.5%; монохромный crescent-вектор v33. На исходном листе options.png это №4; пользователь указал №3, но явно назвал полумесяц, который и выбран. Исходный лист вариантов сохранён. namespace/applicationId не менялись.

С версии 4.2 приложение собирается как отдельная Audiobook Preview: applicationId com.audiobookdownloader.preview, при сохранённом namespace com.audiobookdownloader. BuildConfig.LIBRARY_DIRECTORY=AudioBooks-Preview. SharedPreferences, файловые каталоги, прогресс и виджеты изолированы от стабильной версии. Темы, картинки жанров и центральная кнопка глав сохранены. Прежний исходник AudioBookWidget не был предоставлен; добавлена рабочая совместимость этого компонента с новым рендерером и сохранением обложек.

Обновления Preview публикуются автоматически после push в main; workflow_dispatch позволяет проверять рабочие ветки без публикации.

Сборка: JDK 17, Gradle 8.9, Android SDK 36; gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest. CI выполняет 163 детерминированные JS-проверки реальных извлечённых обработчиков, JVM-тесты и инструментальный Android 36 тест. scripts/audit-book-sources.py записывает текущие HTTP-ответы главных страниц отдельно: этот аудит не доказывает поиск/воспроизведение и не блокирует исправления при внешней недоступности сайтов. LiveCatalogTest сохранён как необязательная проверка старых архивных изданий (RUN_SAVED_ARCHIVE_COMPATIBILITY=1, требуется ffmpeg); это больше не проверка активного поиска. Актуальные изменения: SOURCES-PAGE-TURN.md.

Исходный ключ стабильной версии неизвестен и для отдельной Preview не требуется. Подпись Preview сохраняется через cache/recovery artifact; для постоянного хранения следует создать secret по docs/PREVIEW-UPDATES.md. Обновления публикуются как закрытые prerelease с APK и preview-update.json. Updater проверяет hash, пакет, версию и текущую подпись; ключ GitHub хранит в Keystore, не в отчётах. Не удалять приложение и не очищать библиотеку. Работа внешних сайтов зависит от доступности и актуальной разметки. Не объявлять полный цикл поиска/скачивания проверенным только по доступной главной странице или DOM-фикстурам.

tools/run_checks.py включает текстовые проверки исторических версий и не служит полным современным критерием приёмки. Выполняемые JS-сценарии current/test-*.cjs и current/tests/test-author-search.cjs/test-source-directory.cjs читают актуальный Kotlin и выполняют его извлечённые обработчики на DOM-фикстурах. SearchBookIndexTest и LibraryRecoveryRulesTest отдельно проверяют неизменённые классы.

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
проверяет прежние четыре источника, расширенный список из 16, сохранение переключателей общего поиска, выбор одного и всех, сохранение выбора, свайпы в обе стороны, аварийный отчёт и отдельный экран обновлений, затем повторяет запуск.
Сбой аварийного экрана в тесте вызывается намеренно через reflection; его запись
в артефакте не означает неуспешный тест. Ориентируйтесь на JUnit-результат.
В CI освобождается место для раздела userdata Android 36 (нужно около 7.4 ГБ).
Подробности исходного исправления: STARTUP-RECOVERY.md. Последняя успешная сборка 100027: CYLINDRICAL-PAGE-CURL.md; три Android 36 теста прошли с включёнными анимациями на AOSP ATD. Цветные software-снимки и стадии действующего renderer сохранены в docs/verification/. Аппаратный screenshot и GPU readback ATD недоступны; в UI подтверждён реальный программный захват, не только навигация. В CI используется 360×800/density140 (411×914 dp) для снижения нагрузки программного GPU. GMS отключён только в одноразовом эмуляторе; ошибки самого приложения не закрываются тестовым обработчиком.

Проверка визуального рендерера: PageCurlRenderTest экспортирует восемь стадий тем же Canvas-движком. PNG сохраняются через MediaStore.Downloads (IS_PENDING), затем извлекаются до очистки UTP. У Android ATD аппаратный screencap чёрный, GPU readback возвращает null; проверять software PNG и готовность настоящей texture. Полный Google образ неоднократно терялся как device not found, причина не подтверждена стеком приложения. Не ослаблять UI-тест: единственный семантический Canvas должен сообщать «Перелистывание страницы · цилиндрический загиб» только после получения реального bitmap. Второй полноэкранный семантический overlay закрыл бы первый от UIAutomator/TalkBack.

## Последняя сборка: источники со скриншотов

Актуален 4.2-preview.34 / 100034, исходный commit e27e02e6cdf86552eddbd9ab243e53a912d68060, run 39645389655. Подробности — SCREENSHOT-SOURCES.md; доказательства — docs/verification/checks-100034.json и preview-100034.json. Все 163 JS-сценария, 36 JVM и три Android-теста прошли. Подпись та же, что у 100027. Общая функция abAudioRequestHeaders используется MediaPlayer и DownloadManager; cookies берутся только для адреса аудио. В аварийном отчёте ограничена высота текста, чтобы кнопки помещались на узком экране. Прежние цилиндрическое перелистывание, ярлык-полумесяц, кнопка коллекции и shake-таймер сохранены.

## Удаление трёх источников после Preview 34

По запросу пользователя удалены Lis10Book, Слушкин Всем и Audioboo. В активном реестре девять дополнительных источников плюс прежние четыре, итого 13. Все остальные записи сохранены без изменения URL и адаптеров. Старое значение search_source сбрасывается, если выбран удалённый источник. Из search_enabled_sources убираются только отсутствующие записи; выбранные оставшиеся сайты сохраняются, а пустой после очистки список восстанавливается до текущих источников по умолчанию. Проверка миграции включена в Android smoke test. Исторические отчёты Preview 34 выше описывают прежние 16 записей.

## Актуальная сборка: Preview 35

4.2-preview.35 / 100035, source commit 98ae7b8bd27c8f126e54bcd9c5d21c993ac19f78, run 39653650919. Все 161 JS-проверка, 36 JVM-тестов и три Android-теста прошли. Три сайта удалены полностью из активного реестра; остальные 13 сохранены. История версии 34 выше оставлена как запись о предыдущем состоянии. Подробности — SOURCES-13.md и docs/verification/checks-100035.json. Подпись и пакет Preview сохранены, данные приложения не очищаются.

## Актуальная сборка: Preview 38 и кандидат стабильной версии

Источник d63d970dc16107e601a40d9d0fb8a1ef07e7ed0d, run 39751710274, версия 100038. Исправлены MediaSession metadata/artwork/error/timestamp, ограничена подготовка главы 30 секундами. Три виджета получили полноценные previewLayout, pin extras и живые макеты в настройках; старый AudioBookWidget сохранён. 161 JS, по 38 JVM для двух каналов и четыре Android 36 теста прошли; 27 сочетаний виджетов проверены внутри Android-теста. APK Preview сохраняет прежнюю подпись. Подробности — MEDIA-WIDGETS-STABLE.md и verification/checks-100038.json.

Пользователь изменил прежнее требование параллельной установки: теперь нужна также установка поверх стабильной Audiobook с сохранением книг. Добавлен -PaudiobookChannel=stable: com.audiobookdownloader, Download/AudioBooks, прежние SharedPreferences, отдельный stable updater. Стабильная версия собиралась в Code on the Go (первоначальный ответ про AIDE уточнён пользователем). Её ключа на сервере нет. Подготовлен unsigned APK и скрипт для подписи на исходном телефоне с обязательной сверкой сертификата установленного APK. Подписанного стабильного APK на сервере и фактического теста обновления пользовательской установки нет; не выдавать кандидат за установочный файл. Ключ не нужно передавать из Code on the Go. Инструкция — STABLE-INSTALL.md. Старое приложение не удалять. Музыкальный ForTanya не затронут.

## Исправление PKCS12-подписи на телефоне

Пользователь подтвердил, что сертификат стабильной 1.0 совпадает с исходным debug.keystore Code on the Go, alias androiddebugkey, тип PKCS12. Значение публичного сертификата и актуальная инструкция — STABLE-INSTALL.md. Закрытый ключ и пароль на сервер не передавались. scripts/sign-phone-update.sh теперь явно передаёт store/key passwords через отдельные process env-ссылки, задаёт PKCS12 и использует JDK keytool, когда SDK jar доступен. Подтверждённый сертификат закреплён в скрипте; обязательны пакет com.audiobookdownloader, повышение версии и совпадение подписей. Итоговый APK создаётся переименованием после всех проверок; оригинальное хранилище не меняется. Новый файл для телефона называется sign-phone-update-fixed.sh; старый скрипт из архива Preview 38 следует заменить.

Реальные PKCS12/apksigner проверки находятся в scripts/tests/test-phone-signing.py и docs/verification/phone-signing-pkcs12.json. Пароли и тестовые private keys генерируются временно и не публикуются. Подписание пользовательским ключом не выполнялось на сервере. APK и Android-код не менялись: полный Android build не перезапускался ради замены shell-скрипта.

## Актуально: онлайн-время и общий поиск, сборка 41

Источник b3e54df15505b1e0712b9d921e6d47f41da22679; run 40124351821; Preview и unsigned stable 100041. Оба варианта проходят по 53 JVM-теста (один optional skip), 162 JS и пять Android 36 тестов. AbAudioDurations/AbDurationRanges читают диапазоны онлайн-файлов в фоне и локальные/content главы в смешанном плейлисте, кешируют метаданные, останавливают сетевое чтение при отмене. AbBookTimeRules считает остаток только по текущей/будущим главам, учитывает скорость, неизвестные не считает нулевыми. Общий поиск перенесён в AbSearchCoordinator: две независимые задачи, одинаковый dispatcher для одиночного/общего режимов, таймауты и изоляция ошибок. Сайты и библиотека не менялись. Подробности — ONLINE-TIME-SEARCH.md и verification/checks-100041.json.

Контрольный запрос пользователя «сыны грома» проверен настоящим Chromium производственными JS: Bookinfo нашёл книгу и 30 глав без длительности. Отдельная ограниченная ffprobe-проверка получила объявленные длительности всех 30 MP3; не скачивалась целая книга. Это не Android live playback и не проверка всех 13 сайтов. Физический S24 недоступен. В Книгоблуд за шесть секунд после штатной отправки запроса результатов не было; фиктивного успеха не заявлять.

В новом стабильном архиве sign-phone-update-fixed.sh уже лежит под правильным именем, с явными PKCS12 паролями и pin сертификата пользователя. Подпись stable остаётся локальной на Code on the Go. Перед повторным запуском старый файл Audiobook-update.apk нужно переименовать/убрать из папки вывода: скрипт не перезаписывает файлы. Само установленное приложение не удалять. Конкурентные устаревшие CI-сборки теперь отменяются, чтобы актуальная проверка не ждала их в очереди. ForTanya не затронут.


## Актуально: продолжения в коллекциях, сборка 43

Источник d37e235aead748f75c24b7a047d9a6f2e56bdb20; run 40128473380; Preview / unsigned stable 100043. По 67 JVM-тестов на канал (один optional skip), 162 JS-сценария, шесть Android 36 тестов за 42.007 с. Полный отчёт: CONTINUATION-NOTIFICATIONS.md и verification/checks-100043.json. Ранняя сборка 42 остановилась только на объявлении checked JSONException в тестовом конструкторе; исправление проверено в финальной сборке.

AbContinuationAlerts / SeriesContinuationWorker используют WorkManager 2.10.1: период 12 часов, сеть и достаточный заряд для периодического задания, отдельная ручная проверка, общий Mutex, короткая первичная проверка после изменения shelves_v1. AudiobookApplication регистрирует слушатель коллекций; фоновые задачи не открывают WebView и плеер. Новые настройки и список до 100 новинок хранятся отдельно в continuations_v1; снимки — files/continuations-v1. Первый полный успешный снимок молчит, последующие подтверждают аудио на странице нового тома. Неуспешная/неполная проверка не заменяет прежний снимок. UI — настройки «Продолжения» и кнопка в коллекциях. Уведомление содержит только сохранённый id; открытие через него не запускает звук автоматически.

Ограничения: до 16 книг коллекций по очереди и 24 HTTP-запросов/8 МиБ/6 минут за проход, до 6 страниц и 500 книг на цикл. Требуется явный цикл и полный публичный HTML-список без JS; нельзя заявлять поддержку фоновых циклов всех 13 сайтов. Bookinfo реально отдаёт четыре тома «Каменный век [Белин]»; native-парсер отличает пустой audioFiles тома 6 от 30 файлов «Сынов грома». Эти проверки не являются прослушиванием всей книги или наблюдением будущей публикации. Исходники, фикстура и безопасные метаданные сохранены без cookies/токенов.

Preview SHA-256: 2081969fc5c84e5bfffff0fae764e8c30d9d63e31832c3fad9db7e94f1927f90; прежний сертификат Preview сохранён. Stable SHA-256: eb89baf39e9e7a15493167c1dbbb46c182aac2ba5b49ee52b2abbd79a1dee648; пакет com.audiobookdownloader, папка Download/AudioBooks. Он намеренно не подписан: original PKCS12 остаётся на телефоне в Code on the Go; архив содержит тот же исправленный sign-phone-update-fixed.sh. Установленное приложение не удалять. Новый кандидат на пользовательском телефоне ещё не проверен. DEX AudioBookApp использует 119 регистров; не раздувайте корневой Composable. ForTanya не изменён.


## Текущий выпуск: 45, будущий том присутствует до первой проверки

Источник 4401dfcd935db79ca8794ce7b40e4474a166a40c, run 40130292353, versionCode 100045. Сборка полностью прошла по 67 JVM-тестов на канал (по одному optional skip), 162 JS-проверки и шесть Android-тестов за 38.003 с. Предыдущая 43 также прошла проверки; финальный сценарий дополнительно защищает случай уже опубликованной страницы будущего тома без аудио при первой подписке. В исходный снимок не включаются неподтверждённые пронумерованные будущие тома выше номера книги коллекции: проверяются до 10 самых новых. После появления аудио они остаются кандидатами на уведомление. Для одного тома проверяется до трёх озвучек; сетевой сбой первой не скрывает готовую альтернативную. Все неподтверждённые альтернативы исключаются из снимка до подтверждения хотя бы одной, чтобы дедупликация не скрыла будущий аудиорелиз. Ошибка сети при этой первичной проверке не создаёт снимок. Схема снимка 3 намеренно пересоздаёт старые снимки 43 без изменения пользовательских коллекций и библиотеки.

Итоги: verification/checks-100045.json, preview-100045.json, stable-candidate-100045.json. Preview SHA-256 ee3b0ac92a3f1251f728f1324c92f7a0d498f270515ad4685cead61a1436e679; stable UNSIGNED SHA-256 04b19015eae168382f66a0214bc44a7e3c3a55262d8570c422a972f3f3a217c7. Прежние packageId, библиотека, исходный телефонный ключ и инструкция подписи сохранены. Установленную стабильную версию не удалять. ForTanya не затронут.


## Текущая работа: Предание.ру и обновления без аккаунта GitHub

Добавлен четырнадцатый источник Предание.ру, сохранены предыдущие 13. Производственный адаптер распознаёт аудиокарточки и JSON #audioPlaylistRoot[data-playlist], сохраняет длительности и удаляет повторный URL главы. Чисто текстовые и видео-страницы не превращаются в аудиокниги. При отсутствии корректного плейлиста не используется общий небезопасный fallback. search_enabled_sources получает источник однократно через predanie_source_added; ручное отключение после миграции сохраняется. В общем каталоге поправлен пропуск пустого URL от javascript/mailto-ссылок. Реальный Chromium: «Гоголь» — 11 аудиорезультатов, «Пирамида» — 162 уникальные главы, первая 911 с, 105 категорий; HTTP range MP3 отдельно подтверждён. См. PREDANIE-AND-DIRECT-UPDATES.md и verification/predanie-browser-live.json.

В PreviewUpdatesActivity ровно три кнопки проверки/загрузки/установки, без EditText и GitHub подключения. PreviewUpdater читает публичный read-only HTTPS-feed с точного BuildConfig.UPDATE_ORIGIN. Ограничены redirect, URL, объём метаданных/файла/частей; SHA-256 проверяется для каждой части и всего APK, затем PackageManager сверяет пакет, повышение версии и исходный установленный сертификат. Cache .part публикуется атомарно; незавершённые загрузки удаляются; установку подтверждает Android. Части по 8 МиБ обходят ограничение размера отдельного статического файла. Приватные ключи/пароли и GitHub токены в APK/канале отсутствуют. Автопроверка раз в сутки сохраняет прежний pref auto; новый переключатель и кнопки подключения не добавлялись.

Для канала использован Sites, существующий project_id appgprj_6add1a69b6f481918b5e390c8f413992, origin https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site. Доступ публичный, revision 2, исходники AudioBook остаются приватными. Полный Site source /workspace/sites/audiobook-updates, blueprint hosting/updates; не создавать другой Site и не менять origin в следующем выпуске. tools/package-public-update.py принимает только готовый подписанный APK с закреплённым сертификатом. Для публикации: подготовить parts/JSON, push точного Site source краткоживущими credentials только через скрытый stdin/env без записи токена, собрать tar из .openai/hosting.json и dist, save_site_version затем deploy_site_version с сохранённым id. GitHub release автоматически сам feed Sites не обновляет. После публикации сверить анонимную загрузку всех частей и целый хеш.

Stable-feed available:false до получения APK, подписанного исходным Code on the Go ключом. Ключ остаётся на телефоне; владелец использует тот же sign-phone-update-fixed.sh и присылает только готовый Audiobook-update.apk. Неподписанные кандидаты публикационный скрипт отвергает. Стабильную установку не удалять и данные не очищать; пакет com.audiobookdownloader и Download/AudioBooks сохранены. Preview отдельная .preview. ForTanya не менялся.

Промежуточная сборка 47 остановлена до выпуска: старое ожидание 13 источников обновлено до 14 с явной проверкой миграции; коллекционный UI-тест теперь ждёт включённую кнопку и закрытие окна, прежде чем читать SharedPreferences. Source head повторной сборки b5e1e6cde94d0bfd0fb63f1ded8ee61c4e2ac81f, локальный соответствующий 7560f74, идентичный tree 779ba6494a9accbe2f33d23b2a8b16c9276f6dfb; run 40151361610, номер 48. Окончательные результаты приведены ниже.


Финал 48: run 40151361610 завершился success. 170 JS, 69 JVM passed + 1 optional skip на каждый канал, Android 36 8/8 за 39.598 с, DEX root 119 регистров. Preview SHA-256 372429e5b4c6f651baf6df88d4c9b25390053a2c8ca69ac2fd54bc8a2329defd (37024328 байт), прежний сертификат 3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7; apksigner verify v1/v2 true. Stable unsigned SHA-256 feea270f45e32e9f9852b2f7cd68f51acaf921bd141378e4fc6513c4281399d5, пакет com.audiobookdownloader, versionCode 100048; исходный телефонный ключ не получен и не использован на сервере. В обоих фактических DEX найдены новый источник/миграция, новый origin и три подписи кнопок. Release preview-v100048 содержит Preview APK, старый совместимый preview-update.json и stable-sign-on-phone.zip. Локальные копии /workspace/deliverables/Audiobook-Preview/Audiobook-Preview-4.2-preview.48.apk и /workspace/deliverables/Audiobook-Stable-48/.

Публичный Site version 2: appgprj_6add1a69b6f481918b5e390c8f413992~appgver_86b15b445d708191b9624e9d59211d83; Site source commit 56429d0c123e8ce251367710b4f259e5eb9160df; deployment appgdep_6add6012c40c819187402fd118ecd187 succeeded. Анонимно по HTTPS полностью загружены все пять частей Preview 48, их размеры/хеши и общий SHA-256 совпали с реальным APK и CI. Stable JSON по-прежнему available:false; попытка скормить публикационному скрипту фактический unsigned stable 48 завершилась отказом без изменения feed. Подтверждения — verification/checks-100048.json, preview-100048.json, stable-candidate-100048.json, direct-update-channel-100048.json, source-availability-100048.json. Для окончания публикации стабильного канала нужен только готовый подписанный Audiobook-update.apk с телефона владельца; private key/пароль не запрашивать. Не выдавать unsigned архив за устанавливаемый APK. ForTanya не изменялся.


## Поиск исходного ключа по GitHub после удаления Code on the Go

Пользователь сообщил, что Code on the Go удалён, затем прислал TXT со справочными путями/alias/отпечатками без самого keystore. По явному запросу проверены все пять веток AudioBook, 67 коммитов/66 деревьев, 15 тегов и релизов, 50 исходных ZIP, 1115 локальных blob и метаданные 156 артефактов 50 запусков. Исходный stable debug.keystore (cert 020e6c23...) не найден. Обнаружено 38 preview-signing-recovery, четыре различных ZIP; реальные сертификаты во всех четырёх проверены keytool и равны cert Preview 3a4cecd1..., не stable. Временные локальные копии приватных артефактов удалены, значения ключей/паролей никуда не публиковались; GitHub backup оставлен неизменным. Не подписывать stable этим другим ключом и не заявлять возможность обновления поверх прежней установки без оригинального ключа. Подробности и пределы поиска — SIGNING-KEY-SEARCH.md и verification/signing-key-search-2026-10-25.json. ForTanya не трогали; код и APK 48 не менялись.


## Решение владельца: Preview становится основным приложением

25 октября 2026 пользователь явно выбрал параллельную установку Preview с подключением файлов старой Audiobook и дальнейшим обновлением именно Preview. Отсутствие original stable key больше не блокирует основной путь поставки. Для всех следующих функций/исправлений выдавать подписанную Preview, сохранять com.audiobookdownloader.preview, сертификат 3a4cecd1... и updates/preview-update.json на существующем origin. Не переименовывать package в старый stable и не менять ключ ради слова «основное». Stable-install/sign-on-phone не предлагать как обязательный шаг.

Выпуск 48 уже содержит нужный folder picker/сканер/нативный updater; APK не менялся, пересборка ради инструкции не запускалась. Владелец устанавливает готовый APK Preview рядом со старым приложением, затем «Книги → Синхронизация библиотеки → Выбрать папку библиотеки → Download/AudioBooks». Android требует ручное подтверждение доступа; нельзя заявлять удалённую установку или уже выданное разрешение на физическом телефоне. Новые загрузки остаются в Download/AudioBooks-Preview, выбранная старая папка добавляется к чтению. Настройки/коллекции/позиции старой установки автоматически не импортируются; старую Audiobook не удалять. См. PREVIEW-AS-MAIN.md и verification/preview-as-main-2026-10-25.json. ForTanya не затронут.


## Публичная загрузка APK/ZIP и сообщение об отказе установки

Пользователь показал зависшую загрузку GitHub 37.02/37.02 МБ. Создана публичная страница скачивания на прежнем Site/origin https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site, project_id не менялся, доступ public. Статическая страница download.js собирает опубликованные части в браузере, ограничивает размеры/число/URL, проверяет SHA каждой части и целого APK и только затем показывает явные кнопки сохранения APK и ZIP с тем же APK. Содержимое приложения/подпись/версия 48 и feed не менялись. ZIP хранит один APK без внешнего установщика; проверены CRC и байты APK после извлечения. Готовые Blob URL не отзываются при pagehide, чтобы фоновый переход Android не ломал сохранение. Ошибки сети повторяются до трёх раз; уже проверенные части сохраняются в памяти страницы до следующей попытки, не после перезагрузки/закрытия.

Локальный Chromium в мобильной эмуляции сохранил оба файла, проверены обрыв/повтор без повторного чтения первой части, чужой package и повреждение байтов. После публикации настоящий HTTPS Chromium повторно полностью сохранил APK и ZIP; общий APK SHA совпал с CI 48. Physical Samsung не тестировался. Site source d01663abb178babc2e5db90411510cb39ed20f1a; saved version 3 appgprj_6add1a69b6f481918b5e390c8f413992~appgver_0c87550fcf448191b1f69e8ba2dec6f9; deployment appgdep_6add7f5e8ce481919d6dabf14a07af51 succeeded. Blueprint index.html/download.js в hosting/updates, воспроизводимая браузерная проверка docs/tools/test-public-download.cjs; результаты verification/public-download-2026-10-25.json. Android rebuild не нужен, изменились только материалы раздачи APK.

В ходе работы пользователь показал «Приложение не установлено» и подтвердил, что отдельной Preview до попытки не было — только старая Audiobook. Не приписывать ошибку другой уже установленной Preview без доказательств. Реальная причина отказа установщика на телефоне пока неизвестна. Серверный APK прошёл apksigner, ARM64 присутствует, ZIP и ELF LOAD выровнены на 16 КиБ, sharedUserId/testOnly отсутствуют, все authorities и signature-permission имеют .preview-префикс. Пользователю предложена новая проверенная загрузка без GitHub и запрошен именно скачанный неустанавливающийся APK для сравнения. Не удалять старое приложение и не менять ключ/пакет Preview для обхода неизвестной ошибки. ForTanya не трогали.


### Отказ установки закрыт подтверждением владельца

Пользователь сообщил: «Все установилось, была ошибка при скачивании». Preview установлена на телефон по подтверждению владельца; исходный неустанавливавшийся файл не предоставлялся и его повреждение побайтно не исследовалось. Это пользовательское подтверждение, не проведённый агентом физический тест Samsung. Запрос старого APK снят; проблему подписи, ABI или package для этого случая не считать установленной причиной и не продолжать менять сборку ради уже решённого отказа. Новый код/ключ/версия не требовались. Основным каналом остаётся Preview с прежним сертификатом и package. Выдача доступа к Download/AudioBooks пользователем пока отдельно не подтверждена; при необходимости дать короткий путь «Книги → Синхронизация → Выбрать папку библиотеки». Старые настройки и коллекции автоматически не переносятся.


## Новый текущий выпуск: Audiobook 4.2.50, переименование Preview

По просьбе пользователя основной канал переименован в Audiobook: PRODUCT_NAME/app_name/audiobook_product_name и видимое versionName теперь 4.2.<run>. Пакет com.audiobookdownloader.preview, сертификат 3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7, папка AudioBooks-Preview, preview-update.json и техническое имя Audiobook-Preview.apk неизменны — иначе установленная 48 отвергнет обновление. На сайте публичное имя сохраняемого файла уже Audiobook-4.2.50.apk/zip. Старую стабильную com.audiobookdownloader не обновляем и не удаляем.

Источник финальной сборки 59debe54f42081f0c395fab8598ad5e5b3eb5958 (локальный c726761, tree d56904da59d13260fdfdc4214365f71e17b28335), run 40162825944, job 121077774202, номер 50, success. 170 JS, 69 JVM passed + 1 optional skip на каждый вариант, 8 основных Android 36 за 34.632 с. UpgradeCandidateTest имеет два opt-in теста, в общем проходе 10 методов/2 skip, затем каждый успешно выполняется отдельно. scripts/fetch-upgrade-baseline.py загружает предыдущий подписанный канал через GH в CI. scripts/test-preview-upgrade.py запускается только при CI=true и serial emulator-*, устанавливает реальный 48, пишет контрольные preferences/shelf/path/file, выдаёт READ_MEDIA_AUDIO/POST_NOTIFICATIONS, сверяет SHA каждого переданного файла, вызывает старый PreviewUpdater.verifyApk(new), затем adb install -r и проверки сохранения данных/имени/запуска новым кодом. Это не автоматический сетевой download/installer UI на физическом телефоне; владелец сейчас испытывает три кнопки сам. Реальный SAF grant в fixture не выдавался — проверены строка пути и отдельно runtime-permissions.

Промежуточная 49 остановилась на дополнительном probe, release и публичного feed для 49 нет. Финальный тест копирует файлы через adb exec-in run-as … dd и обязательно сверяет device sha256sum перед нативной проверкой; продовый обновлятор не ослаблялся и не менялся. Фактический APK50: label Audiobook, code100050, size37024303, SHA a9c921fa758967608a842c08690f73709aed468bd9ceeb56055d68414b472ef3, прежняя подпись; native старой48 принял кандидат50. DEX root119. Stable-кандидат50 всё ещё unsigned и для основного принятого пути не нужен.

Публичный Site project прежний appgprj_6add1a69b6f481918b5e390c8f413992, audience public, source commit33b8b6cea8a5d966892864190441ed0086060473, version4 appgprj_6add1a69b6f481918b5e390c8f413992~appgver_8b2eaeae7d648191aeb98b103dd5ef1f, deployment appgdep_6add8adebfd081919f91914d51f592a3 succeeded. Полный анонимный download с UA Audiobook-Updater/4.2-preview.48 совпал по всем частям/общему SHA; metadata100050 новее установленной100048. Части48 сохранены. Chromium тоже сохранил APK/ZIP и извлечённый APK идентичен. Доказательства: checks-100050.json, preview-100050.json, installed-upgrade-100048-to-100050.json, direct-update-channel-100050.json, rename-download-browser-100050.json. Инструкция — AUDIOBOOK-RENAME-UPDATE.md. Владелец должен открыть именно установленную Preview48 → Настройки → Обновления → Проверить/Загрузить/Установить и подтвердить Android. После установки видимое имя Audiobook. Пока physicalPhoneUpdateConfirmed=false до сообщения владельца. ForTanya не менялся.
