# Компактный плеер: вариант2

2026-10-31: владелец выбрал второй макет и связь с выбранными темами.
ub-mode разрешён для этой задачи. Сначала native regression на прежнемUI:
обложка перед названием, заметный размер, коллекция/продолжения в одномряду,
5видимых нижнихдействий, добавлениезакладки вдиалоге, сменафона темы1/5/10.
Проверка использует настоящие MainActivity/PlaybackService и локальныйWAV,
без копии реализации плеера. Baseline85 не изменён. Ожидаемыйred: новый
порядок обложка→название/неткнопкиПродолжения.

## Журнал

- Прочитаны AGENTS/HANDOFF/ub-mode/QUALITY; выбор пользователя сохранён.
- Добавлена проверка настоящего UI до изменений production: commit0620d93,
  native run41214774641, API23/36. Подтверждениеred ожидается.
- Изучены palette AbTheme и настройки dim/text/cardOpacity: отдельная палитра
  плеера не нужна. Отсутствующие данные о чтеце не будут придуманы.
- Подготовлен локальный черновик компоновки вне source tree: нижний транспорт
  закреплён, доступность большого шрифта сохраняется в центральной области.
- Pinned Crapper(--no-coverage) применён к новому Java UI-test: CC6/6/1/1;
  coverage/CRAP N/A, не заявлен CRAP gate. Dryer0.8:кандидатовнет.
  Kotlin/MainActivity этими инструментами не поддерживается: Mutator --scan
  сообщаетNo source files, полноценное mutation testing не заявлено.
- В тестовомWAV writer заменёнjava.nio.file.Files наFileOutputStream для API23;
  ключ масштаба исправлен на настоящийappearance_text_scale. Production безизменений.

- Red подтверждён реальным XML Android36 (run41214774641, artifact13048205456,
  ZIP SHA706643b82e9dbdb4ba486079d81be63d8ac066b93985b02efa779520857c601d):
  CompactPlayerTest → AssertionError: Cover must precede the title. ОстальнойUI
  остаётсяbaseline85. После чтения этого результата применён новыйproductionUI.
- В новомUI используется существующийtheme, фон с лёгкойзатемняющейподложкой,
  обложка сверху и адаптивнаявысота. Транспорт/нижниедействия закреплены вне
  прокруткиметаданных; крупныйшрифт сохраняется. Главы/серия/очередь —прежниедиалоги.
  Добавлениезакладки перенесено вдиалог. Длиннаяперемотка сохранена поудержанию.

- Первый compile новогоUI остановлен на пропущенном импортеBoxWithConstraints
  (focused41215459502). Добавлен явныйимпорт; проверки не подавлялись.

- Добавлены проверка150%textscale и захватреальныхскриншотов1/5/10/150
  вtargetedemulator до егоостановки; testscript сохраняет исходныйexitcode.
- Подготовлена внешняя tarbackup8unreferenced72/73 hostingparts(65956503bytes),
  каждый файлсверенпослеархивации; Siteисточник/public пока безизменений.
  Это место для следующегоAPK, не пользовательскаябиблиотека.

- Focused41215689341 compiled both platforms, ноCompactPlayerTest остановилсяна
  unrelatedprecondition «Жанры» допроверокплеера. Тесттеперьявновыбирает «Плеер»
  черезнавигацию, не предполагаетстартовыйэкран. Всеassertionsмакета сохранены.
  Добавленыfailurehierarchy/screenshot/privatecrash/logcat дляследующейдиагностики.
  Full86 superseded до публикации, требуетсяfull87 наитоговомтесте.

- Проверена повторившаясяpremise остановкинастартовомэкране: тест предполагал,
  чтоMainActivity ужеимеетвсеразрешения. УrequestMissingPermissions дваобязательства
  (libraryaudio на23+/notifications на33+), fixture выдавал толькоaudio на33+.
  Убранаэтазависимостьотпорядкадругихтестов: fixture явно выдаёт существующие
  runtimepermissions передlaunch. Productpermissionsнеизменены. Full87 superseded
  до публикации; следующийfull88 только послефактическогоfocusedlayoutрезультата.

- Реальныйper-testlogcat e6 показалGrantPermissionsActivity поверхMainActivity;
  missingpermissions —подтверждённаяfixtureошибка. Послеисправления7e UI36
  прошёлзапуск,ноreadbounds поймалStaleObjectException: тестдержалустаревший
  accessibilitynode междукадрами. Теперь каждыйclick/bounds занововыбираетузел;
  boundedretry толькоStaleObjectException, assertion/producterrorsнеподавляются.
- API23 UiAutomation.grantRuntimePermission отсутствует: fixtureпереведённа
  pmgrant черездоступный23+shell сassertпроверкойpermission. Productнеизменён.
- AGPcleanup удаляетapp-specificexternalfiles дотранспортаартефактов: снимки
  теперь используютпроверенныйTestBitmapExports/MediaStore иshellcopyдля23,
  hierarchy прикладываетсякassertion иfailurePNG сохраняетсявpublicDownload.

- c353 actualfailurehierarchy: «ОбновлениеAudiobook / Доступна4.2.85 / Позже /
  Обновления». Targeteddebugверсия100071 нижеlive85, поэтомуштатныйbackground
  updater перекрылтестовыйплеер. Убранапредпосылкасвободногоstartupwindow:
  толькоfixture выключаетpreview_updates.auto, послеcase восстанавливаетBoolean.
  ReleaseNotesseen выставлендляfixture; ниupdatepolicy,ниproductionповедениенеменяются.

- 5196 реальныеPNG1/5 просмотрены: правильныеpalette,всеосновныедействиявидны,
  нофиксированныйhero оставлялбольшойпустойпромежутокмеждупрогрессомичаптерseek.
  API23 впервые дошёлдомакета: Cover is too small. Удалёнheuristicreserve400:
  обычныйэкран измеряетmetadata исначаладаётостатокобложке(weightedlayout,cap320).
  Дляlargefont/короткогоокнаmetadataвscroll,нижниеcontrolspinned. Вертикальныегапы
  уплотнены, footer54dp сохранёнбольше48dp touch target;chapterdropdown доступенизfooter.
- Android36 проходилtheme1/5, останавливалсянаcloseпослеaddbookmark3: тестотключил
  idleожидание(0), ановаяbookmarkrow двигаетцентрдиалога. Установлено500msframework
  idleпередUiAutomatoroperations; проверкизакрытиясохранены,bookmarkзаписьasserted,
  проверкавремениbookтеперьявная. Приошибкезакрытиясохраняетсяhierarchy+PNG.

- 455b:Android36 targeted44/44pass, включаяtheme1/5/10/150,закрытиеbookmarkdialog,
  реальнуюзаписьbookmark/bookremaining. Android23:Cover is too small повторился.
  Проверенаскрытаяpremise:twoactor-layouts —weightedregular иfixed96dpscroll;
  прежнийheight<480 направлялобычныймалыйportraitвfixedветку,поэтомуправка
  weightedизмерения не затрагивалаеё. Убранаэтажёсткая480assignment:scroll
  требуетсядляlargefontилидействительнокороткогоviewport<360. Передsizeassert
  snapshot/boundsтеперьсохраняются длядиагностики, thresholdassert неизменён.
- ThenMakeRight:membership-labelmemo теперьзависитотJSONколлекций, непересчитывает
  весьshelflist прикаждомmedia-progressprefupdate. FullStartupassert проверяет
  переключение«Вколлекцию»→«Вколлекции» послесохранения. Playback/storageнеизменены.

- Full88/a13 настоящийAndroid36 startup/recovery/upgrade шагpassed; идётlint/finalpackaging.
  Focuseda13 Android36 failurehierarchy —«Process system isn't responding»,неAudiobook.
  Использовантотжесуществующийallowlist обработчикStartupSmokeTest(Waitдляknownsystem,
  никогдаAudiobook/unknownANR); решениеодно,выделенpackage-static overload,prodнеизменён.
- A13 Android23 actualRect(425,222—655,541),screen1080×1920: cover319pxприthreshold320.
  Правильныйweightedмакет отличаетсяна1pxrounding. Уточнёнтолькоgeometrytesttolerance1px;
  порядок,размерныйпорог,видимостьвсехкнопок/booktime/bookmark ицветапроверяются.
- Этотcommit меняеттолькоAndroidTest/журнал. Не пересобиратьнеизменённоеприложение:
  full88 compiledsourcea13 остаетсяисточникомподписанногоAPK. Latestfocusedбудет
  runсобновлённымtestharness, production/manifest/resources/gradle/scriptsidenticala13.

## Финальная проверка

Full41220372143 success,compiled a13ac3c6955d95ad999b322ec425b57633d90ec5.
Scoped41221382589 success(API23 43/43,API36 44/44),source472ece78b20a80de21ea977abd9821e4264c9310.
Только2AndroidTestфайлаижурналотличаются;всеAPKproduction/build/scriptpaths совпадают.
JVM147pervariant/1skip,fullnative83/3skip,0failure/error,lint0Error/Fatal(85Warning/74Info),
OSV68modulescomplete0findings. Exact signedAPK33069766bytes,f45039978b18e9408ba5c13870e5be6a0c79fac45e2914caf9ee74967e6b4cd9,
nondebuggable, v1/v2, package/certunchangedfrom85, definedUIhelpermethods present,
CompactPlayerTest/testfixtures/testcleartextconfigabsent. Upgrade85→88 settings/shelf/folder/privateaudioSHAkept.

Crapper(--no-coverage) changedCompactPlayerTestmaxCC9,coverage/CRAP N/A. Dryer0.8:
no duplicate candidates betweenchangedUItest andStartupSmoke. Mutator --scan current/MainActivity.kt:
No source files to mutate (Kotlinunsupported);Java-testscan earlier56sites, NOTmutationtesting.
No CRAP/coverage/mutation-completeness claim for Kotlin. Source review/native functional tests aretheUIgate.

Actualimageinspection:themes1/5beforeweightedchange;weighted1/5/10/150source455b;finalGraphite36
andSDK23 150. SDK23 first-themeoffscreenbitmapwasblack;previousA13same-productionframe inspected.
SDK23WAVfixtureMediaPlayer−38observed (unchangedService); UI/error-state tested, no successfulaudio23claim.
Native36actualWAV/sleep/shake/media/upgradepassed. PhysicalS24/FPS/AndroidnetworkAPK/PlayProtectunverified.

Cleanup: preserved activepinnedtools/receipts/oldstash; no uncommittedwork removed.
Sourcehistory/nativereports retained. Hostingonly8unreferenced72/73APKparts externallybackedup
withper-filehashverificationbeforedeletion,65956503bytes;currentandmanualfeeds retained.
