# Audiobook4.2.72 — опубликована через меню обновлений

- Коллекции → «Скачать книги» (включая все коллекции): предварительные сведения
  об издании, затем последовательная очередь без переключения плеера. Уже
  скачанные главы и явные фрагменты пропускаются. Детали по5 книг на страницу.
  Закрытие останавливает будущие книги, принятая системой загрузка продолжается.
- Удаление скачанных файлов в начале «⋮», доступно и при пустом индексе. Все
  поддерживаемые аудиоформаты, отмена собственных DM jobs, проверка точной папки/URI,
  повторная проверка остатков/sidecars, продолжение после выдачи доступа к папке.
- Общая защита записи обложек и удаления исключает создание папки поздним callback.
  Коллекции, закладки, private thumbnails, настройки и другие документы сохраняются.
  Автоматического удаления после прослушивания нет.

[Разработка и причины](LIBRARY-FILE-ACTIONS.md), [проверки](release-4.2.72/verification.json).
PR4 merged289d6c2cc69b9acadf52049c6799cec807a96c73,
treeff9f09aae0afcd986f6291cacfdf06adeadba2d5. Scoped40991356902: API23
18 cases/1skip (Downloads MediaStore29+), API36 18/18. Normal40992606141 success,
run71+offset1=72: JVM Debug/Release132/1optional skip каждый, Android36 50cases/
3opt-in skips и все3 реальные release-upgrade фазы отдельно успешны.
Lint0 Error/Fatal,74 Warning,63 Information. OSV68 modules,complete,0 known findings.
Kotlin не поддержан pinned Crapper/Dryer/Mutator: их результат N/A, не passing score.

APK100072/4.2.72,32970042 bytes,
SHA31e65328fe69d3375f771e7d46ea25a37c74d40ce7aa3513110f2cbe41aee5b4.
Packagecom.audiobookdownloader.preview,labelAudiobook,certificate3a4cecd1… прежний,
release non-debuggable,v1/v2 verified. Debug fixture/provider и test classes отсутствуют.
Старый updater71 принял72 и открыл реальное Android confirmation; release замена
в disposable emulator сохранила настройки,коллекцию,folder/private file и audioSHA.
Физический Samsung,actual Android network APK download и PlayProtect verdict не проверены.

Site15 source4ab34e721a614569cdc460027abc0a24a8da59e4,
deploymentappgdep_6ae4075d038081919f4633de657e577f succeeded.
https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site
Feedpreview-update.json=100072. Клиенты71/70 получилиHTTP200 metadata и4 parts;
полный SHA совпал с подписанным APK. download.js не менялся, новый browser QA
для статического текста не повторялся. Старые части и manual69 сохранены.
Текущий hosting archive уже266813440 raw bytes при лимите256MiB: следующий выпуск
потребует проверить ссылки всех metadata и убрать только неиспользуемые старые chunks
с backup/Git history, сохранив текущий72,manual69 и при возможности предыдущий71.

Следующий normal run72 выпускает73 через тот же offset1; не запускать повторную
сборку ради этих docs. Не ротировать ключ/package и не трогать original stable.
Перед следующей новой разработкой спросить о ub-mode; согласие на эту задачу было.
