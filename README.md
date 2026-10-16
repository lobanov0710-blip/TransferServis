# TransferServis

Android-приложение пассажира: расчёт стоимости трансфера, оформление заявки,
просмотр активного заказа, локальная история поездок и профиль.

Разработка ведётся в `feature/passenger-completion`, созданной от
`feature/foundation-v1` (`3fa4900`). Перенос изменений в основную ветку выполняется
после проверки владельцем. План и критерии готовности: [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md).

## Сборка и проверки

Нужны JDK 25, Android SDK Platform 37.0, SDK Build Tools 36.0.0 и platform-tools.
Gradle 9.6.0 запускается через wrapper; Java-исходники используют уровень 11.

```bash
bash ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Отчёты: `app/build/reports/tests/testDebugUnitTest/` и
`app/build/reports/lint-results-debug.html`.

Для Android-тестов нужен подключённый эмулятор или устройство с API 24+:

```bash
bash ./gradlew :app:connectedDebugAndroidTest
```

В подготовленной облачной среде перед командами активируйте инструменты:

```bash
cd /workspace/TransferServis
source /workspace/.setup/activate.sh
```

Облачная задача уже изолирована: используйте текущую рабочую копию, дополнительный
Git worktree без отдельного запроса не нужен.

## Структура

- `ui/`: Fragment, ViewModel и состояния экранов; навигация в `res/navigation/nav_graph.xml`.
- `domain/model/`: модели расчёта, заказа, профиля и истории.
- `domain/validation/`: общие правила ввода данных пассажира.
- `data/repository/`: запросы API и работа с локальными данными.
- `data/remote/`: Retrofit/OkHttp, DTO и ошибки API.
- `data/local/`: Room и защищённая сессия доступа к заказу.

Сейчас поддерживаются один локальный профиль и одна сохранённая сессия заказа.
Новая заявка заменяет сохранённую сессию предыдущей. История остаётся на устройстве.
Это не система регистрации пассажиров и не серверный личный кабинет.

## Сервер

Адрес и маршруты заданы в `constants/AppConstants.java`:
`https://transfer-servis52.ru/`, `api/calculate.php`, `api/order.php`,
`api/order-status.php`. Исходный код сервера в репозитории отсутствует.
Проверка реальных заказов требует согласованного тестового сервера/сценария;
автоматические HTTP-тесты используют MockWebServer и не создают реальные заявки.

Токен доступа к заказу хранится через AndroidKeyStore/AES-GCM. Не добавляйте его
в логи, навигационные аргументы, URL, документацию или резервные копии.
