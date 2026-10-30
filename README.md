# TransferServis — Android-приложение пассажира

Приложение на **Java + XML** с архитектурой MVVM: расчёт стоимости трансфера, отправка заявки, активный заказ, локальная история поездок и профиль.

**Состояние на 28.10.2026.** Функциональная Android-реализация перенесена из `fix/passenger-profile-instrumentation-v1` в `master` через [PR №9](https://github.com/lobanov0710-blip/TransferServis/pull/9), merge-коммит [`67b1e1b`](https://github.com/lobanov0710-blip/TransferServis/commit/67b1e1b95ff243b936cd96c6d5de4a1a7bf24dbc). Ранее в рабочую ветку вошли [PR №10](https://github.com/lobanov0710-blip/TransferServis/pull/10) (автоматизированные Debug/Release-проверки) и [PR №11](https://github.com/lobanov0710-blip/TransferServis/pull/11) (очистка локальных артефактов). [GitHub Actions №40765068343](https://github.com/lobanov0710-blip/TransferServis/actions/runs/40765068343) на merge-коммите `master` завершился успешно в обоих заданиях. Это **не означает выпуск или приёмку production Release**.

[План разработки](docs/DEVELOPMENT_PLAN.md) · [STAGING Android Debug](docs/STAGING_ANDROID_DEBUG.md) · [Результаты проверок](docs/VALIDATION.md) · [Подготовка подписанного Release APK](docs/RELEASE_SIGNING.md)

## Варианты сборки

- **Debug**: `applicationId=ru.transferservis.app.staging`; отдельный HTTPS staging API и временный токен из **локальных, некоммитируемых** Gradle-свойств. Без настройки адрес `https://staging-not-configured.invalid/`: такие APK не отправляют заявку в production. Токен может находиться внутри Debug APK — **не распространять** APK, собранный с рабочим токеном.
- **Release**: `applicationId=ru.transferservis.app`, `API_BASE_URL=https://transfer-servis52.ru/`, пустой staging-токен. Release-тесты проверяют отсутствие заголовка `X-Staging-Access-Token` и HMAC-заголовков. Production HTTP-запросы в CI не отправляются.

Используются Gradle wrapper, Android SDK Platform 37 и Java 11 для исходного кода. GitHub Actions запускает сборку с JDK 17 и установленным Android SDK. Для локальной сборки используйте совместимую версию JDK согласно Android Studio/Gradle и настройкам проекта; SDK и необходимые компоненты должны быть установлены.

## Автоматические проверки

GitHub Actions в `master` выполняет Debug unit-тесты, Lint, сборку Debug APK и **компиляцию** Android instrumentation (без запуска на устройстве). Отдельное задание Release Gate выполняет:

```bash
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

Проверяется наличие `app/build/outputs/apk/release/app-release-unsigned.apk` и отсутствие действительной подписи. Этот APK **не подписывается, не загружается в CI artifacts и не публикуется**. В artifacts хранятся только отчёты Release тестов и Lint. На merge-коммите [`67b1e1b`](https://github.com/lobanov0710-blip/TransferServis/commit/67b1e1b95ff243b936cd96c6d5de4a1a7bf24dbc) оба задания [GitHub Actions №40765068343](https://github.com/lobanov0710-blip/TransferServis/actions/runs/40765068343) завершились **success**; Debug APK и отчёты опубликованы в артефактах CI.

Debug unit/Lint и сборка локально:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Отчёты: `app/build/reports/tests/`, `app/build/reports/lint-results-debug.html`, `app/build/reports/lint-results-release.html`.

## Инструментальные тесты — только на изолированном AVD

**Не запускайте `./gradlew :app:connectedDebugAndroidTest` на общем списке подключённых устройств.** Некоторые тесты удаляют локальные настройки профиля, сессии и отложенной заявки. Это может стереть тестовые данные на используемом эмуляторе.

Для Windows используйте проверенный сценарий из [STAGING_ANDROID_DEBUG.md](docs/STAGING_ANDROID_DEBUG.md), который создаёт отдельный временный AVD, собирает Debug c неработающим endpoint и пустым staging-токеном, запускает тесты и формирует отчёт:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-staging-instrumentation.ps1
```

На временном `emulator-5556` с Android 36.1 ранее успешно пройдены **27/27** инструментальных тестов. Не повторяйте без причины прогон на основном `emulator-5554` с сохранённой тестовой заявкой.

## Структура

- `ui/`: Fragment, ViewModel, LiveData и состояния экранов; навигация в `res/navigation/nav_graph.xml`.
- `domain/`: модели и правила валидации.
- `data/repository/`: API и локальные репозитории.
- `data/remote/`: Retrofit/OkHttp, DTO, обработка ошибок.
- `data/local/`: Room, защищённое локальное хранение доступа к заказу.

Сейчас поддерживаются один локальный профиль и одна сохранённая сессия заказа. Новая заявка заменяет сохранённую сессию предыдущей; история хранится на устройстве. Регистрация аккаунтов и серверный личный кабинет не реализованы.

## Сервер и границы проверок

Debug через отдельный HTTPS PHP Gateway обращается только к `uber-v3-staging` и staging D1; ручной E2E на эмуляторе проверен в STAGING-06. Release указывает на рабочий сайт `https://transfer-servis52.ru/`, но **CI не создаёт production-заказы**. Автоматические HTTP-тесты используют только локальный `MockWebServer`.

Токен доступа к конкретному заказу хранится с использованием AndroidKeyStore/AES-GCM. Не добавляйте реальные токены, HMAC-секреты и приватные Gradle-свойства в Git, журналы, URL или резервные копии.

**До публикации Release:** согласовать ключ подписи владельца и параметры `versionCode`/`versionName` (сейчас `1`/`1.0`), проверить подписанную сборку, установку/обновление на физическом устройстве и пользовательские сценарии с production-сервером. Успешный CI не означает разрешение на публикацию. Слияние в `master` уже выполнено; выпуск не выполнялся.
