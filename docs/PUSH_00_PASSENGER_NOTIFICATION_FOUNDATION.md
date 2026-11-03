# Transfer Servis — PUSH-00: Android-основа пассажирских уведомлений

**Дата:** 03.11.2026  
**Статус:** реализация ограниченной Android-инфраструктуры в отдельной ветке, без FCM-доставки и серверной регистрации.  
**Основание:** [утверждённое ТЗ v0.2](PASSENGER_APP_TECHNICAL_SPEC_V0_2.md), требования PUSH-01…06.  
**Репозиторий:** `TransferServis` (`master` — основная ветка; feature-ветка до ревью).

## 1. Проверенные факты по состоянию на 03.11.2026

- Контрольный Android CI `master` после PR №21–24: **success**, [run 41577140660](https://github.com/lobanov0710-blip/TransferServis/actions/runs/41577140660).
- В пассажирском `app/build.gradle.kts` на момент начала PUSH-00 **не было** Firebase Messaging SDK и Google Services plugin; в отслеживаемом Git-дереве **нет** `google-services.json`, `FirebaseMessagingService` или пассажирского FCM registrar.
- В `TransferDriver` FCM и staff push-device реализованы **для водителя**; права staff и staff-токены **не** являются пассажирским механизмом.
- В `uber-v3` есть staff `PUT/DELETE /staff/push-device`, D1 staff sessions и FCM отправка по активным staff-устройствам. **Нет** подтверждённого passenger `PUT/DELETE` API или пассажирской FCM-доставки в действующем FROZEN v1.
- В Android пассажира есть `PassengerOrderSessionStore` c AES/GCM capability `passenger_order:read`, `orderId`, expiry и защищённым восстановлением. Это не готовое разрешение на операцию регистрации push.
- Debug APK использует `applicationIdSuffix = ".staging"`, а release приложение — `ru.transferservis.app`. Настройки Firebase должны быть корректны для **обоих** applicationId, если FCM будет включён на обоих.
- Android `targetSdk=37`, `minSdk=24`; разрешение `POST_NOTIFICATIONS` требуется на Android API 33+ для большинства уведомлений.

## 2. Что реализовано именно на этапе PUSH-00

1. В `AndroidManifest.xml` добавлено `POST_NOTIFICATIONS`, **без автоматического запроса** при запуске.
2. `PassengerNotificationChannels` создаёт канал `passenger_order_status_v1` с русским названием, `IMPORTANCE_DEFAULT`, `VISIBILITY_PRIVATE`, только на Android API26+.
3. `PassengerNotificationPermission` даёт безопасный способ проверить возможность показа уведомления с учётом Android API33+ и пользовательской блокировки канала/приложения.
4. `MainActivity` создаёт/обновляет системный канал без публикации уведомления.
5. `PassengerPushEnvelopePolicy` — строгий `version=1`, `type=passenger_order_updated`, `eventId` и `orderId` (канонические UUID), **никаких лишних полей** (телефон, текст чата, токен, адрес или статус). Принимать только event для текущего неистёкшего защищённого `sessionOrderId`. Парсер не выполняет запросы и не утверждает серверный статус.
6. Добавлены JUnit-тесты валидации envelope и инструментальный тест канала/manifest на изолированном Android API35.

**Не реализовано:** Firebase SDK, реальный FCM callback, получение/хранение/отправка FCM token, серверная регистрация, доставка, показ notification, пользовательский запрос permission, deep link и какие-либо изменения в Worker/PHP/D1.

Это **сознательное разделение**: до готовности проверенного passenger backend нельзя регистрировать токен на staff endpoint или отправлять пользователю ложные «изменения заказа».

## 3. Предложенный минимальный passenger event contract (НЕ действующий API)

```json
{
  "v": "1",
  "type": "passenger_order_updated",
  "eventId": "eea07921-11d1-4204-b106-29d9e9521122",
  "orderId": "68516805-d870-4bc9-ac4a-43995df5bc05"
}
```

Event означает только «на сервере могло измениться состояние заказа». Не передавать `status`, `driverName`, `phone`, `address`, `route`, `accessToken` или содержимое чата. **Подтверждение состояния — только через действующий защищённый order-status**, с учётом срока действия capability. Если пользователь нажмёт на будущий push, открывать только свою сессию заказа и перезапрашивать статус с сервера, не доверять полю payload.

**Повторная доставка:** нужна серверная идемпотентность по `eventId` + `installationId`, а также клиентская дедупликация. В PUSH-00 **ещё нет** ни доставки, ни дедупликации, и выполненным PUSH-06 считать нельзя.

## 4. Следующие этапы реализации

### PUSH-01: закрытый backend contract и права

В отдельном PR `uber-v3` + отдельном PHP gateway PR проектировать:
- `passenger_push_devices` (или аналог) с полями order ownership, installationId, FCM token, время регистрации/обновления/отзыва, состояние и TTL; D1 миграция **новая, аддитивная**, не изменять staff push таблицы.
- регистрацию / отзыв **только своего** устройства с новым узким правом и проверкой текущего заказа/expiry/terminal state, не использовать staff JWT и не наделять read-only capability без разбора произвольными write полномочиями;
- отписку при терминальных статусах / замене сессии, защиту от replay, rate limiting, ограничение installId и токенов;
- проверку источника статуса D1 и строгую серверную выборку получателей **не по произвольному client orderId**;
- тесты на чужой `orderId`, истечение доступа, повтор регистрации, двойную/позднюю доставку, отмену, смену устройства и fail-closed.

**Важно:** нельзя утверждать, что реальное событие «водитель прибыл» существует: в frozen v1 отдельного события прибытия нет. Нельзя делать напоминание за N минут без подтверждённого `pickup_at_utc` и согласованного интервала.

### PUSH-02: Android FCM client и понятный opt-in

- Зарегистрировать отдельное пассажирское Firebase Android-приложение для `ru.transferservis.app` и (при необходимости тестов) `ru.transferservis.app.staging`. Выбрать одинаковый Firebase project только после проверки разрешённой конфигурации. Нельзя копировать staff Android config вслепую.
- Подключить `firebase-messaging` и Google Services plugin **только после подтверждения/наличия соответствующей конфигурации**. Не коммитить service-account private key, FCM registration tokens или server HMAC.
- Firebase messaging callback должен проверять актуальную `PassengerOrderSessionStore.Session` и строгий envelope. Затем notification может быть только общим «Обновление поездки» (lockscreen PRIVATE), с открытием собственного заказа и повторной проверкой API.
- Реализовать on-device installationId, безопасную токен-ротацию, по одному запросу регистрации за раз, retry с generation, revoke на потерю прав/смену заказа. Не использовать один FCM токен в разных активных заказах без explicit ownership policy.
- **Не запрашивать разрешение на первом запуске.** Просить только по осознанному действию при наличии реальных уведомлений, с объяснением и корректным сценарием отказа. Отказ не мешает брони/статусу.
- No notification body/PII/token в Logcat/Crashlytics/URL, никакого автоматического открытия чужого заказа.

### PUSH-03: доставка и подтверждённые события

- Реальный production/pilot push только после отдельного разрешения владельца и контрактного review, сначала безопасные события статуса, потом новые события общения и прибытия, если они существуют.
- FCM — транспорт, а не «доказательство» статуса: всегда проверять D1 по текущей capability.
- Для сценариев отказов: без FCM, нет сети, отказ в разрешении, истёкшая сессия, недоступный backend, дубли и устаревший event.

## 5. Quality gates и границы разрешения

| ID | Критерий | PUSH-00 |
|---|---|---|
| P00-T01 | JUnit filter: свой UUID, чужой order UUID, неверная версия/тип, секрет в payload | Unit-тесты добавлены, результаты CI проверять отдельно |
| P00-T02 | Channel API35: PRIVАTE + русские ресурсы; API24 no-op без сбоя | Instrumented тест добавлен для API35; API24 отдельно перед релизом |
| P00-T03 | POST_NOTIFICATIONS в AndroidManifest и отсутствие автоматического запроса | Code-review + instrumented |
| P00-T04 | На отказе permission старое бронирование работает | Регрессия Android CI и отдельный E2E после подключения FCM |
| P00-T05 | Ни один новый endpoint/FCM token/реальный push в этой ветке не появляется | Сравнение diff; no production tests |
| P00-T06 | `API_CONTRACT_V1.md` и `minSdk=24` остаются неизменными | Code-review |

Готовность PUSH-00 **не равна** готовности PUSH-01…06 из общего ТЗ. Ни пользователь, ни приложение пока не получают новые реальные уведомления; это инфраструктурный foundation PR.

**Ограничения:** только Java/XML Android, отдельная feature-ветка, GitHub Actions. Тестовые сообщения в production, merge, signed APK и deployment не выполняются без согласования владельца.
