# AudioBook

Android-приложение для поиска, скачивания и прослушивания аудиокниг.

**Основная версия:** [4.2.101](https://github.com/smoc1983-web/AudioBook/releases/tag/preview-v100101) (`versionCode 100101`). 23 ноября 2026 года подтверждено успешное обновление на Samsung через экран приложения.

> Технический идентификатор основной версии — `com.audiobookdownloader.preview`, а теги релизов — `preview-v...`. Слово Preview сохранено **только ради обновлений существующих установок**; основная ветка разработки — **`main`**.

| Назначение | Git-ветка | Android-пакет | Канал обновлений | Библиотека |
| --- | --- | --- | --- | --- |
| Основное приложение | `main` | `com.audiobookdownloader.preview` | `preview-v...` / `Audiobook-Preview.apk` | `AudioBooks-Preview` |
| Экспериментальная версия | `test` | `com.audiobookdownloader.test` | `test-v...` / `Audiobook-Test.apk` | `AudioBooks-Test` |

[Скачать основную версию](https://github.com/smoc1983-web/AudioBook/releases/download/preview-v100101/Audiobook-Preview.apk) · [История выпусков](https://github.com/smoc1983-web/AudioBook/releases)

**Важно:** обе программы устанавливаются параллельно; библиотеки, настройки и прогресс в их закрытых хранилищах независимы. Тестовые APK **никогда** не устанавливать поверх основного приложения. Для первого тестового APK: `gradle :app:assembleRelease -PaudiobookChannel=test`; он должен быть подписан *постоянным* ключом тестового канала.

## Как выпускать обновления

1. Разрабатывать и проверять изменения в ветке `test`; её GitHub Actions выпускает отдельно подписанные `test-v...` релизы. Встроенный обновлятор Audiobook Test проверяет **только** эти теги.
2. После успешных проверок переносить изменения в `main` через pull request. `main` собирается и тестируется, но **не публикует APK автоматически**.
3. Выпускать основную AudioBook отдельным проверенным процессом с пакетом `com.audiobookdownloader.preview`, прежним сертификатом SHA-256 `3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7` и `versionCode` выше опубликованного.

Историческая Stable (`com.audiobookdownloader`, 1.0) более не поддерживается сборочной цепочкой: её закрытый ключ потерян. Исходники и архивы доступны в истории Git; рабочее приложение не нужно удалять для обновления.

## Где находится код

- `app/`: Android, Kotlin/Java и ресурсы.
- `current/MainActivity.kt`, `current/PlaybackService.kt`: основные части интерфейса и аудиосервиса.
- `scripts/` и `.github/workflows/`: проверки и сборка.
- `upgradeProbe/`, `legacyFixture/`: тестовые инструменты совместимости (не являются старой Stable APK).
- `docs/`: техническая документация.

## Локальная проверка

```sh
python3 current/check.py
gradle :app:testDebugUnitTest :app:testReleaseUnitTest -PaudiobookChannel=main
gradle :app:testReleaseUnitTest :app:assembleRelease -PaudiobookChannel=test
```

Для сборки: JDK 17, Gradle 8.11.1 и Android SDK 36. Нельзя ставить неподписанную или случайно переподписанную сборку поверх рабочего приложения.
