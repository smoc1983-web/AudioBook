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
