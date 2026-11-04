# PUSH-02E — подготовка отдельного revoke-only grant в Android

Дата 04.11.2026. Статус: Java/MockWebServer подготовка в зависимой ветке. Base: PUSH-02D PR №30.
Зависимости: uber-v3 PUSH-01I PR №21, public_html PUSH-01I PR №9, обе ветки Draft.

## Контракт клиента

- Обычный prepareGrant формирует ровно {accessToken, installationId} и относится к будущему opt-in/управлению.
- prepareRevocationGrant формирует ровно {accessToken, installationId, operation:"revoke"}. Операция добавляется через статическую фабрику GrantRequest.forRevocation, поэтому сторонняя строка operation не принимается.
- DTO не содержит orderId, FCM token, PHP/Worker HMAC или других приватных полей. Retrofit направлен только в /api/passenger-push-grant.php.
- prepareRevoke продолжает использовать DELETE /api/passenger-push-device.php с телом только grantToken. Grant scope в самом Android не верифицируется: авторитетная проверка только на сервере.
- Подготовительные методы не отправляют запрос самостоятельно и во всех обычных Android Debug/Release сборках заблокированы compile-time PASSENGER_PUSH_NETWORK_ENABLED=false.
- Отзыв предусмотрен без повторного opt-in/OS notification permission, но только при действующей защищённой passenger order session и валидном installationId. Зависимость от срока жизни capability остаётся блокером.

## Тестирование

- MockWebServer проверяет фактический POST к PHP с точным JSON и operation="revoke", без client orderId и FCM token.
- Обычный manage request по-прежнему имеет два поля; feature-disabled client не готовит никаких Calls.
- Запуск unit/Lint/Debug/unsigned Release и isolated Android tests только CI. Реального HTTP к production, FCM и создания заказов нет.

## До включения уведомлений

- Завершить review зависимых PHP/backend/Android PR и локальный интеграционный тест на обновлённой цепочке.
- Утвердить revoke при истёкшем read-token, приоритетную rate policy, серверную очистку токенов, смену installationId после opt-out.
- Подготовить Firebase release/debug apps, реальные конфигурации, token lifecycle/onNewToken, отложенный/надёжный revoke, подтверждённый backend статус заказа и E2E.
- Отдельное согласие владельца требуется на любые merge, deployment и запуск push. Этот PR не меняет Android master и production.
