# PUSH-02A — безопасный Java-контракт пассажирской FCM-регистрации

**Дата:** 03.11.2026. **Статус:** подготовка клиентского кода; никакого Firebase SDK и реального FCM.  
**База:** [PUSH-02 документ PR №26](https://github.com/lobanov0710-blip/TransferServis/pull/26) → [PUSH-00 PR №25](https://github.com/lobanov0710-blip/TransferServis/pull/25).  
**Сервер:** `uber-v3` PR №13–20, PHP `public_html` PR №6–8 находятся в отдельных Draft-ветках. **PRODUCTION — основной продукт; STAGING только для необходимых тестов.**

## Добавлено

1. `PassengerPushRegistrationPolicy` проверяет явное согласие пассажира, системное разрешение уведомлений, срок активной сессии, корректные UUID заказа и установки, непустые корректные bearer/FCM токены, срок grant и дополнительный compile-time флаг. Возвращает только bool; не обращается в сеть и не читает FCM.
2. `PassengerPushConsentStore` по умолчанию `false`, хранит **только** выбор пользователя, без FCM token, персональных данных, grant и accessToken. Ошибочная/повреждённая настройка приводит к запрету. Для этой настройки добавлены исключения из облачного backup и межустройственного переноса Android.
3. `PassengerPushApiService` + `PassengerPushPayloads` описывают **только** PHP endpoints:
   - POST `api/passenger-push-grant.php`: `{accessToken,installationId}` → `{ok,grantToken,expiresAt}`.
   - POST `api/passenger-push-device.php`: `{grantToken,platform:"android",token}` → `{ok}`.
   - DELETE `api/passenger-push-device.php`: `{grantToken}` → `{ok}`. Retrofit использует `@HTTP(method="DELETE",hasBody=true)`.
   - В body **нет client orderId**, статуса, цены, имени или телефона. PHP, а не Android, рассчитывает HMAC по оригинальному raw body. На Android нет HMAC секрета и прямого Worker URL.
4. `PassengerPushRegistrationClient`: `prepareGrant`/`prepareRegister`/`prepareRevoke` возвращают только `retrofit2.Call` или `null` при отсутствии условий; **не запускают** запрос самостоятельно. Сетевые действия будущий orchestration-слой должен запускать вне UI потока, после повторной проверки consent и session. Для отзыва после отказа предусмотрен отдельный клиентский путь \`prepareRevocationGrant\`, который не требует повторного согласия/разрешения, но требует действующей пассажирской сессии; после этого \`prepareRevoke\` принимает краткосрочный grant. **На сервере grant всё ещё имеет scope \`passenger_push:manage\`, а не revoke-only**. До production нужно утвердить безопасное ограничение возможностей такого grant и реализацию удаления регистрации при просроченной пассажирской сессии.
5. `BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED=false` в **каждой** сборке по умолчанию. Никакой пользовательский API не вызывает методы клиента; включать этот флаг в production до готовности backend и тестов запрещено.
6. В `AndroidManifest.xml` добавлены `firebase_messaging_auto_init_enabled=false` и `firebase_analytics_collection_enabled=false`. Это будущая безопасная настройка; сам Firebase SDK и `google-services.json` **не добавлены**.
7. Unit-тесты `PassengerPushRegistrationPolicyTest` и `PassengerPushRegistrationClientTest` на JVM/MockWebServer; Android instrumentation `PassengerPushConsentStoreTest` проверяет default-disabled, opt-out и повреждённые prefs. CI собирает Debug и проверяет отсутствие Firebase конфигурации.

## Границы

- **Нет** регистрации настоящего FCM token, Firebase Messaging listener/service, отправки уведомлений или пользовательской кнопки. Данный PR не даёт пользователю возможности подписаться на push, он готовит отдельный безопасный код.
- `PASSENGER_PUSH_NETWORK_ENABLED=false` запрещает создание запросов через стандартный конструктор клиента, в том числе на Debug. Package-private test constructor разрешает включение **только** для локального MockWebServer.
- Для настоящего включения нужны отдельные валидные `google-services.json` для `ru.transferservis.app` и (при необходимости) `ru.transferservis.app.staging`, зависимости SDK, явный пользовательский opt-in UX, runtime permission API33+, обработка onNewToken, корректный retry/revoke после обновления grant.
- Самостоятельная клиентская проверка UUID/сроков — **дополнительный guard**, не доверенная авторизация. Источник прав — подпись JWT + актуальный заказ на backend и отдельные механизмы replay/owner authorization.
- На backend выдаётся short-lived `passenger_push:manage` grant, read capability сама не позволяет регистрацию. После grant expiry для отзыва требуется **новый** grant; подготавливать его нужно даже при отозванном согласии, без повторного включения FCM.
- Пока backend PR не смёрджены, серверные endpoints недоступны в production. Нет тестов реальной сети или E2E с Firebase.
- **Никаких** изменений `TransferDriver`, `public_html`, `uber-v3`, production D1/Worker, secret, релизных APK или уже работающих уведомлений водителей.

## Правило разработки и проверка

Основной контур проекта — **PRODUCTION**, STAGING используется **только при необходимости проверки**. Все новые изменения проходят локальный unit/CI, отдельный PR и явное одобрение перед merge/deploy. 

Приёмка этого PR: `./gradlew :app:testDebugUnitTest :app:assembleDebug` + изолированный API35 `PassengerPushConsentStoreTest`, подтверждение `PASSENGER_PUSH_NETWORK_ENABLED=false`, отсутствия Firebase config и запрета backup выбора пользователя. Только успешный актуальный CI считать доказательством прохождения.

**Следующий отдельный этап:** согласование Firebase Android app и UI opt-in; затем реализация FCM service, token rotation, dedupe, status refresh с `PassengerPushEnvelopePolicy` и mock E2E. Без реального Firebase проекта не добавлять выдуманный config и не утверждать, что уведомления доставляются.
