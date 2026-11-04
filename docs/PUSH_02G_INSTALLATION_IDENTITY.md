# PUSH-02G — постоянный локальный идентификатор установки и безопасный отзыв

Дата: 04.11.2026. Статус: Draft поверх Android PUSH-02F PR №36.

## Проблема
PUSH-02F подготовил последовательность `revoke-only grant → DELETE → clearIfMatches(session)`, но требовал получить `installationId` от будущего вызывающего кода. Если бы при отзыве генерировался новый UUID, серверный DELETE затронул бы **не ту** регистрацию; старая могла остаться действующей до истечения срока. При смене сессии нельзя удалять идентификатор новой регистрации.

## Решение
- Новый `PassengerPushInstallationStore` в Java. Отдельный `SharedPreferences`-файл `passenger_push_installation_v1.xml`, одно поле `installation_id`, только случайный UUID v4 от `UUID.randomUUID()`. **Не является секретом или заменой bearer read JWT**. В нём не хранятся FCM-токены, grant, phone, order ID или read-token.
- `getExistingForRevocation()` строго **read-only**: отсутствие/повреждение UUID → `null`, никакой генерации и смены идентификатора.
- `getOrCreateForExplicitRegistration(userConsented, notificationsAllowed)` — единственный путь генерации. Требуются одновременно явное согласие, системное разрешение и `BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED=true`; фактически во всех текущих APK флаг `false`. `commit()` проверяется, при испорченном сохранённом значении регистрация отклоняется, чтобы не потерять адрес старой регистрации.
- Стабильный ID используется между запусками, при параллельном создании действует общий процессный lock. После **подтверждённого** сервером DELETE ID удаляется только через `clearAfterConfirmedRevocation(expectedId)`, с атомарной проверкой сохранённой версии. Старый UUID нельзя повторно использовать: backend оставляет `installation_id` tombstone, новая регистрация после подтверждённого отзыва получит новый UUID.
- В `backup_rules.xml` и **обоих** разделах `data_extraction_rules.xml` файл исключён из облачного backup и device transfer. Восстановление на другом устройстве не переносит ID и согласие.
- `PassengerPushSessionExitCoordinator.revokeBeforeLocalExit(expectedSession)` теперь сам берёт **только имеющийся** ID из данного хранилища. Проверяет актуальность ID и Session до grant и перед DELETE. Когда удаление на сервере подтверждено, очищает только совпадающую Session и ID. Новую Session/ID во время in-flight запросов не удаляет.
- Ошибки после успешного ответа DELETE **не маскируются** как серверная неопределённость: `REMOTE_REVOKED_LOCAL_CLEAR_FAILED` при сбое очистки session, `REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED` при сбое очистки ID; UI должен различать эти исходы.
- Сеть, Firebase SDK, background job, отправка FCM, реальная кнопка выхода, активация feature flag **НЕ добавлены**.

## Проверки
- JVM `PassengerPushSessionExitCoordinatorTest` через MockWebServer: отсутствие существующего ID, 503 при DELETE, точный UUID в revoke grant, сохранение новой Session/ID при гонке, очистка только после двух 2xx+`ok=true`, раздельные исходы ошибок **после** подтверждения.
- API35 instrumentation `PassengerPushInstallationStoreTest` с реальными SharedPreferences: выключенный network flag не создаёт ID; два отдельных экземпляра и конкурентные потоки читают один UUID; отсутствие согласия или OS permission не создаёт ID; ID на revoke читается без генерации; испорченный файл отказывает без замены; удаление чужого ID запрещено; после подтверждения корректный ID удаляется и новый opt-in создаёт другой. Запускать внутри изолированного emulator GitHub Actions вместе с consent UI tests.
- Существующие Android Debug/unsigned Release, Lint, JVM, MockWebServer и другие инструментальные тесты должны остаться успешными. До результата финального SHA не утверждать, что CI PASS.

## Ограничения до реального push
1. Серверные `PUSH-01I`…`PUSH-01N` и PHP gateway находятся в Draft. Не включать `PASSENGER_PUSH_NETWORK_ENABLED` без всех базовых PR/миграций и отдельного разрешения.
2. `ProfileFragment` ещё не запускает сетевой revoke; `ActiveOrderViewModel.clearActiveOrder()` и автоматическая очистка истёкшей/испорченной Session остаются **локальными**, без серверного подтверждения. UI выхода, очередь на сетевую ошибку, политика отмены согласия и UX локального выхода ещё требуют реализации.
3. `clearAfterConfirmedRevocation` должен вызываться **только** координатором после backend DELETE. Сам `installationId` — не доказательство владения заказом. Подлинность и scope по-прежнему проверяет server JWT/HMAC.
4. При истёкшем read JWT выдавать новый revoke grant нельзя. Не добавлять обход; серверное истечение регистрации + ограниченная очистка — отдельная политика (backend PUSH-01N/01M).
5. Нет end-to-end доставки пассажирского FCM. Нельзя утверждать, что вся цепочка вышла в production.

Production — основная среда эксплуатации и разработки продукта; STAGING используется **только при необходимости тестирования**. Никаких реальных PHP/Worker/D1/FCM вызовов, миграций, merge, deployment, новых заказов и подписания APK.
