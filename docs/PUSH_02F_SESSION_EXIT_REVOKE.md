# PUSH-02F — отзыв регистрации до очистки пассажирской сессии

Дата: 04.11.2026. Статус: зависимый Draft от Android PUSH-02E (PR №31).

## Проверенный контекст

- Пассажирская `PassengerOrderSessionStore` хранит read-token в Android Keystore/AES-GCM и умеет атомарно `clearIfMatches(expected)`.
- `PassengerPushRegistrationClient` готовит только Retrofit вызовы, но **никогда не запускает их самостоятельно**. Compile-time `PASSENGER_PUSH_NETWORK_ENABLED=false` в Debug/Release; Firebase SDK не подключен.
- Отдельного полнофункционального logout UI в утверждённом экране профиля пока нет. `ActiveOrderViewModel.clearActiveOrder()` и автоматический сброс некорректной сессии выполняют **локальную** очистку без серверного push DELETE. Их нельзя считать серверным отзывом и **нельзя включать реальную FCM-доставку до интеграции lifecycle**.
- Backend PUSH-01I/01J поддерживает scoped `operation=revoke` для `/passenger/push-grant`; DELETE требует одноразовый grant, привязанный к заказу/устройству. PUSH-01N ограничивает серверную регистрацию оставшимся read-JWT TTL. Это пока Draft в отдельных репозиториях.

## Что подготовлено

В `notifications/PassengerPushSessionExitCoordinator.java` добавлен **неподключённый к UI** тип для будущего явного выхода из активной пассажирской сессии. Вход: snapshot защищённой `Session` и `installationId` зарегистрированного устройства, который пока не хранится/не создаётся координатором. Вызов помечен `@WorkerThread`.

Протокол:
1. Проверить срок и подлинность *текущего локального snapshot* через `sessions.isCurrent(expected)`. Проверка на клиенте не заменяет серверную авторизацию.
2. `prepareRevocationGrant` — только `operation="revoke"`; без доступа/истёкший read-token/выключенный feature gate → **никаких запросов и удаления локального токена**.
3. Отправить POST в PHP; принимать только HTTP 2xx, `ok=true`, непустой `grantToken` и `expiresAt`. До DELETE повторно проверить, что сохранена та же сессия и она не истекла.
4. `prepareRevoke` → DELETE в PHP. Лишь при HTTP 2xx и `ok=true` вызвать атомарный `clearIfMatches(expected)`. При смене сессии вернуть отдельный статус, **не удаляя новую сессию**.
5. Любая ошибка сети, 403, 429, 503, пустой/неполный JSON или истечение срока → `REMOTE_NOT_CONFIRMED`, **без ложного успеха и без автоматического локального стирания**. Без фоновых повторов, сохранения access/grant в новом файле или журналирования credentials.

Результаты разделены на `NO_ACTIVE_SESSION`, `SESSION_CHANGED`, `REMOTE_NOT_CONFIRMED`, `REMOTE_REVOKED_AND_LOCAL_CLEARED`, `REMOTE_REVOKED_BUT_SESSION_CHANGED`. Конечный UI обязан показывать реальные статусы, а не сообщение «серверное отключение выполнено» при одном лишь локальном opt-out.

## Проверка

`PassengerPushSessionExitCoordinatorTest` использует только MockWebServer, фальшивые `readToken` и `installationId`, проверяет последовательность двух HTTP-запросов и строгие поля без `orderId/fcmToken` в grant, отказ по умолчанию `BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED=false`, отсутствие DELETE при неверных ответах или истёкшей сессии, сохранение новой сессии при гонке после grant/DELETE, отсутствие локального сброса при 503.

## Обязательные этапы до реального FCM

1. Согласовать UX явного выхода и действия «очистить только на устройстве»: пользователь должен видеть предупреждение, что серверный отзыв **не подтверждён**. Пока такой кнопки и wiring нет.
2. Реализовать независимый надёжный `installationId` store и политику смены устройства/токена; защищённое хранение и запрет backup/restore. Не выдумывать ID на каждый revoke.
3. Пересмотреть **все** существующие локальные пути очистки: `clearActiveOrder`, удаление испорченной/просроченной сессии, изменение заказа, отозванное согласие или ОС permission. До согласованного lifecycle не включать network gate и Firebase.
4. Истёкший read-token запрещает получение revoke-only grant. Нельзя обходить авторизацию «по UUID», FCM-токену или старому grant. Серверные `expires_at` + PUSH-01M bounded cleanup ограничивают длительность, но ещё не развернуты и не запланированы.
5. Утвердить ограничения на 429 / offline retry, retention, race перед внешней отправкой, Firebase payload, серверный ответ logout и E2E. Не заявлять полной реализации logout после одного подготовительного класса.

Production — основная среда продукта. STAGING исключительно для необходимых изолированных тестов. Никаких реальных FCM/PHP/Worker/D1 запросов, новых заказов, включения флага, миграций, подписания APK, merge или deploy.
