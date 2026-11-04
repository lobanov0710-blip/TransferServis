# PUSH-02D — UI согласия на пассажирские уведомления

Дата: 04.11.2026. Ветка: feature/push-02d-consent-ui, основание PUSH-02C / PR №29.
Статус: подготовительный Android Java/XML код, без включения доставки Firebase.

## Что меняется

- В профиле появляется раздел «Уведомления о поездке» с точным сообщением об отсутствии реальной доставки. Пока build gate PASSENGER_PUSH_NETWORK_ENABLED=false, кнопка неактивна.
- Выключенная функция не сохраняет согласие, не вызывает системный POST_NOTIFICATIONS prompt, не создаёт канал, не вызывает API и не получает FCM token.
- Будущий явный сценарий opt-in предусматривает ActivityResultContracts.RequestPermission на API33+, отказ и проверку OS permission при onResume. API24–32 не требуют runtime prompt. Отключение сети не отменяет возможность обычного бронирования.
- Используется существующий PassengerPushConsentStore (default false) с запретом cloud backup/device transfer.
- Тесты: PassengerPushConsentUiPolicyTest (unit), PassengerPushConsentUiTest (Android instrumentation, запускать только на отдельном AVD). Тесты не создают реальных заказов.

## Что ещё необходимо до включения

1. Review и объединение зависимой цепочки Android PR №25–29 в утверждённой последовательности.
2. В PHP и Worker внедрить проверенный revoke-only grant, безопасное удаление регистрации при opt-out, проверку scoped passenger access. Только локального сохранения false недостаточно для серверного отзыва.
3. Зарегистрировать раздельные Firebase Android apps для ru.transferservis.app и ru.transferservis.app.staging; проверить реальные конфигурации. Фиктивные JSON и водительские токены не использовать.
4. В отдельном PR подключить FirebaseMessagingService, явную token lifecycle, onNewToken, server grant/registration/revoke и E2E. Не включать Firebase auto-init без согласия.
5. Получить отдельное разрешение владельца на merge, server rollout и production delivery; до этого флаг должен оставаться false.

Приёмка PUSH-02D: GitHub Actions на текущем HEAD должны подтвердить unit-тесты, Lint, Debug и unsigned Release сборки, компиляцию androidTest. Успех CI не подтверждает реальное FCM.

Запрещено менять master, production Worker/PHP/D1, TransferDriver, оформлять реальные заказы, подписывать и публиковать APK.
