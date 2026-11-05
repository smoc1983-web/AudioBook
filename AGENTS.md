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
