# Audiobook

Android-приложение для поиска, загрузки и прослушивания аудиокниг.

Текущий опубликованный выпуск: **4.2.94 / 100094**, канал Preview,
пакет `com.audiobookdownloader.preview`. Прежняя подпись сохранена.

[Скачать APK](https://github.com/smoc1983-web/AudioBook/releases/download/preview-v100094/Audiobook-Preview.apk)
· [Релиз](https://github.com/smoc1983-web/AudioBook/releases/tag/preview-v100094)
· [Проверки и ограничения выпуска](docs/RELEASE-4.2.94.md)

Обновление устанавливается поверх прежней Preview. Удалять приложение не нужно.
В версиях с GitHub updater доступно меню обновления. Старый адрес сайта может
потребовать разового ручного APK; подробности в [описании перехода](docs/BRIDGE-4.2.93.md).
Stable имеет другой пакет и не смешивается с Preview.

## Структура проекта

- `app/` — Android-модуль, код функций приложения, manifest, unit- и Android-тесты.
- `current/MainActivity.kt`, `current/PlaybackService.kt` — основной интерфейс и сервис; Gradle копирует их в generated sources.
- `current/app/src/main/` — дополнительные исходники и ресурсы основного модуля.
- `current/tests/` — тесты парсеров, fixtures и структурная проверка Kotlin.
- `current/js-check/` — извлечённые из Kotlin скрипты для тестов парсеров.
- `legacyFixture/`, `upgradeProbe/` — тестовые модули совместимости обновлений.
- `scripts/`, `tools/`, `.github/` — сборка, проверки и публикация.
- `docs/` — инструкции, описание функций и подтверждения проверок приложения.
- `AGENTS.md` — правила продолжения работы и актуальное состояние проекта.

## Проверки и сборка

Для сборки нужны JDK17, Gradle8.9, Android SDK36 и build-tools35.0.0.

```sh
python3 tools/run_checks.py
gradle :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease -PaudiobookChannel=stable
```

Первая команда проверяет исходники, парсеры и Java-правила; она не заменяет
компиляцию Android и испытания на устройстве. Тестовые fixtures не входят в APK.
Для подписанного выпуска Preview используется только существующий сертификат.
Не создавать замену ключа и не изменять package для обхода ошибки обновления.

## Документация

[Последовательность доработок](docs/IMPROVEMENT-SEQUENCE.md),
[передача проекта](docs/HANDOFF.md), [правила качества](docs/QUALITY.md).

Старые копии исходников, ZIP-архивы и макеты убраны из текущего дерева ветки.
История Git и опубликованные GitHub Releases сохранены. Старые ссылки внутри
исторических отчётов относятся к состоянию до очистки; его можно открыть
в коммите `6de4d878b315adf55bcdccd17b0c1d0a326e535b`.
