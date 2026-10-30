# Audiobook4.2.74 — описание по нажатию на обложку

**4.2.74 опубликована и доступна через меню обновлений.**
[Страница загрузки](https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site).
«Настройки → Обновления → Проверить → Загрузить → Установить».

Нажатие на обложку открывает описание выбранного издания,без запуска другой книги.
Текст загружается со страницы источника,сохраняется offline; ошибка обновления
не удаляет сохранённую аннотацию. Название карточки отдельно открывает прослушивание.
Закрытие отменяет загрузку,восстановление ссылки не закрывает окно.
Если источник не передал описание,показано понятное сообщение. Скачанные аудиофайлы,
коллекции,настройки и подпись сохраняются.

Проверки: native15/15 наAndroid23 и36; JVM143/1optional skip наDebug/Release;
Android main61/3opt-in skips и3upgrade phases отдельно; Lint0Error/Fatal;
OSV68modules/complete/0known findings. Java parser11/11,actual coverage/CRAP<10,
Dryer no>=0.8,3 targeted guard mutations killed (не whole-file mutation score).
Kotlin metricsunsupported/N/A. Реальная release73→74 замена в эмуляторе сохранила
контрольные настройки,коллекцию,audioSHA. Physical Samsung,Android network download
и PlayProtect verdict отдельно не проверялись.

APK32986390bytes,SHA256876b5739e36c41340177534d2766be812cf597959dfa12b24b822ffc0ac01d5d,
packagecom.audiobookdownloader.preview,cert прежний3a4cecd1…/v1+v2,labelAudiobook.
Реальный APK проверен на наличие классов описания,dialog,notes; debug fixtures
отсутствуют,permissions прежние. [Все результаты](release-4.2.74/verification.json).
[Private release74](https://github.com/smoc1983-web/AudioBook/releases/tag/preview-v100074)
для владельца с входом GitHub. Не удалять приложение ради установки.

Site17 успешно опубликован2026-10-30, без пересборки APK. Оба клиента73/72
анонимно получили metadata и все4части поHTTP200;32986390bytes и общаяSHA256
совпали с проверенным APK. [Публикация](release-4.2.74/publication.json),
[HTTP-проверка](release-4.2.74/public-check.json). Подпись прежняя,original stable
и ForTanya не изменены. Следующий normal run создаст75.
