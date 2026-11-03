# Transfer Servis — PUSH-02: подготовка Firebase Android

**Дата:** 03.11.2026. **Статус:** документационная подготовка; FCM SDK, `google-services.json`, токены и получение push НЕ подключены.  
**База:** `feature/push-00-passenger-notification-foundation`, [PUSH-00 PR №25](https://github.com/lobanov0710-blip/TransferServis/pull/25).  
**Сервер:** `uber-v3` PUSH-01A…D (PR №13–16) и PHP `public_html` №6–7 ещё не объединены, не развёрнуты и работают fail-closed.

## 1. Проверенная Android-конфигурация

`app/build.gradle.kts`:
- `namespace = "ru.transferservis.app"`;
- Release `applicationId = "ru.transferservis.app"`;
- Debug `applicationIdSuffix = ".staging"` → **`ru.transferservis.app.staging`**;
- `minSdk=24`, `targetSdk=37`, Java 11, XML, Android Gradle Plugin 9.4.1;
- Firebase Messaging, Google Services Gradle plugin и `google-services.json` пока отсутствуют;
- Debug API расположен на отдельном staging hostname, без заданного URL debug-сборка не обращается к production.

## 2. Регистрация Firebase

Зарегистрировать в **согласованном владельцем Firebase project** два разных Android app ID:
1. `ru.transferservis.app` — release;
2. `ru.transferservis.app.staging` — debug/испытания, если FCM действительно нужен в debug.

Файлы Firebase:
- `app/src/release/google-services.json` — для release app ID;
- `app/src/debug/google-services.json` — для debug app ID.

Этот формат поддерживается Google Services Gradle plugin. Не копировать водительский `google-services.json` без проверки package ID и политики проекта. Не создавать фиктивный конфигурационный файл, особенно с выдуманным project ID. Firebase Android config содержит идентификаторы проекта, не серверный service-account private key, но его публикацию нужно согласовать с владельцем. Server service-account ключи, HMAC, FCM registration token, пассажирские bearer/grant токены **никогда** не коммитить и не добавлять в Logcat.

Официально: https://firebase.google.com/docs/android/setup и https://developers.google.com/android/guides/google-services-plugin

## 3. Обязательный opt-in и запрет автоматической регистрации

Firebase Messaging по умолчанию может генерировать/обновлять registration token при запуске. Чтобы до согласия пассажира не происходило автосоздание FCM-регистрации, на этапе интеграции SDK нужно добавить в `application` AndroidManifest:

```xml
<meta-data
    android:name="firebase_messaging_auto_init_enabled"
    android:value="false" />
<meta-data
    android:name="firebase_analytics_collection_enabled"
    android:value="false" />
```

Это **проект будущего изменения**, фрагмент пока не внесён в Manifest. Firebase указывает, что отключение автоматической регистрации требует обе мета-настройки.

Android API33+: `POST_NOTIFICATIONS` запрашивать только при осознанном включении уведомлений пассажиром, не при первом запуске приложения. Отказ не должен нарушать бронирование и чтение статуса. На API24–32 учитывать системное состояние уведомлений без ненужного runtime запроса.

Официально: https://firebase.google.com/docs/cloud-messaging/android/get-started и https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging

## 4. План Java-клиента

```text
DISABLED
  ├─ согласие, разрешение Android, собственная неистёкшая passenger session
  └─ ENROLLING
       ├─ новый on-device installationId (не staff installation ID)
       ├─ отдельный grant через PHP /api/passenger-push-grant.php
       ├─ регистрация FCM token через PHP /api/passenger-push-device.php
       └─ REGISTERED
            ├─ onNewToken → ротация только после нового grant
            ├─ смена/отмена/завершение заказа → revoke
            ├─ пользователь выключил → revoke + disable auto init
            └─ истечение/отказ/сеть → fail-closed / ограниченный retry
```

- Java `FirebaseMessagingService` в пассажирском package после реального Firebase setup; не использовать водительский сервис и его staff endpoints.
- Входящий data-only push: `{v,type,eventId,orderId}`, без персональных данных, статуса/цены/адресов или токенов; строгий `PassengerPushEnvelopePolicy` из PUSH-00 и проверка активной зашифрованной сессии.
- FCM — транспорт, а не источник статуса: после получения/открытия уведомления снова читать подтверждённый backend `/api/order-status.php`.
- Системный канал PUSH-00 создать только при реальном согласии/необходимости, `NotificationCompat.Builder` с `VISIBILITY_PRIVATE`. Не выводить чувствительный текст на экран блокировки.
- Дедупликация `eventId`, ограниченный повтор, долгоживущую запись FCM token в незашифрованном SharedPreferences не делать. Firebase token не отправлять автоматически до успешной аутентификации и включения backend.
- Регистрация должна учитывать переход заказа и expiry: никаких фоновых повторных регистраций после отказа пользователя или утраты passenger capability.

## 5. Quality gates (ещё НЕ пройдены)

1. Подтверждены Firebase app IDs и конфигурации обеих сборок. Build Debug не использует release-проект или production backend.
2. Сборки Debug/Release с Google Services plugin компилируются с Java/AGP 9.4.1 и реальными конфигами, включая instrumented tests без реального заказа.
3. Firebase auto-init disabled на старте; без opt-in нет FCM token/network registration, нет скрытого запроса разрешения.
4. Проверены API24/32/33/35/37, согласие, отказ и смена разрешения.
5. На чужой `orderId`, отсутствующей или истёкшей сессии, поддельном payload, дубликате и новом token — ни статус, ни заказ, ни чужие данные не раскрываются.
6. Проверена ротация FCM token + revoke и ошибки PHP/Worker/FCM на mock backend.
7. E2E с настоящей регистрацией/FCM после одобрения и полноценного backend rollout, без автоматических production уведомлений до отдельного разрешения.

**Не делать сейчас:** добавлять Firebase SDK к default variant без конфигурации, копировать водительские секреты, включать автосбор токенов, подписывать/распространять APK или запускать реальную отправку. Эта документация — спецификация следующего PR, а не готовая интеграция.
