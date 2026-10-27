# Android Transfer Servis — тестовое окружение (Debug)

**Статус (27.10.2026):** PHP Gateway на `https://staging.transfer-servis52.ru/` развёрнут и проверен через PowerShell. Подтверждены авторизация, HMAC, расчёт, создание и повторная отправка заказа, чтение статуса и запрет доступа без корректного пассажирского токена. Проверка Android-устройства ещё не проводилась. Изменения Android остаются в черновике PR.

## Архитектура

Пассажирское Android-приложение по-прежнему написано на **Java + XML** с архитектурой MVVM. Для запросов сохраняются Retrofit и OkHttp.

```text
Android Debug
  → отдельный HTTPS PHP-шлюз staging
  → uber-v3-staging (подпись HMAC проверяется здесь)
  → transfer-servis52-staging (D1)
```

PHP-шлюз подписывает запросы к Worker. HMAC-секрет Worker хранится **только на PHP-сервере**, не в APK. API-маршруты не меняются: `api/calculate.php`, `api/order.php`, `api/order-status.php`.

Android Release, как и раньше, обращается к `https://transfer-servis52.ru/`. Только Debug может использовать тестовый адрес.

## Локальная конфигурация разработчика

**Только после** отдельного согласования и публикации защищённого staging PHP-хоста поместить в непубликуемый файл `%USERPROFILE%\.gradle\gradle.properties` на Windows:

```properties
transferStagingBaseUrl=https://staging.transfer-servis52.ru/
transferStagingAccessToken=TEMPORARY_STAGING_ONLY_TOKEN
```

Адрес staging уже подтверждён. `TEMPORARY_STAGING_ONLY_TOKEN` — только заполнитель: действительный токен берут из локального файла `ts52-staging-secrets/staging-access.key`, не публикуют и не отправляют в чат. Путь URL должен оканчиваться `/`, а имя хоста должно содержать `staging`.

Тестовый токен является временным: он попадёт в собранный Debug APK. Debug APK **нельзя публиковать или передавать посторонним**. После E2E-испытаний тестовый токен перевыпускаем. Никогда не помещаем секрет Worker или персональные данные клиентов в `gradle.properties`.

Если `transferStagingBaseUrl` не задан, Debug использует заведомо неработающий `https://staging-not-configured.invalid/`. Это исключает случайную отправку тестового заказа в production при запуске Debug без настроек. Release не содержит тестового токена.

## Что проверяется без реального сервера

- Код Android не переводился с Java на Kotlin.
- Retrofit-контракт, модель заказа, защищённое хранилище Keystore/AES-GCM и механизм восстановления заявки не переписаны.
- HTTP-тест с MockWebServer проверяет заголовок `X-Staging-Access-Token` и отсутствие HMAC-заголовков Worker.
- CI выполняет unit-тесты, Lint, сборку debug APK и компиляцию Android instrumentation APK. Сам эмулятор CI не запускает.

## После отдельного разрешения на развёртывание

1. Администратор развёртывает отдельный PHP staging-хост согласно PR №5 репозитория `public_html`; production-шлюз не меняется.
2. Проверить HTTPS, ACL, лимиты запросов, отдельные серверные HMAC-файлы и временный токен.
3. Выполнить на Android Debug расчёт → создание заказа → локальное сохранение сессии → принудительный перезапуск → `/order-status`.
4. Проверить восстановление при потере сетевого ответа, отсутствие дубликатов в staging D1 и отсутствие Telegram/FCM уведомлений.
5. После тестов отозвать тестовый токен. Слияния в основные ветки — только после приёмки.

**Работающий production-сайт, сервер, БД, заявки клиентов и Telegram/FCM в этом PR не изменяются.**
