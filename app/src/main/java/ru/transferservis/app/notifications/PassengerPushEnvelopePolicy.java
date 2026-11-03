package ru.transferservis.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Недоверенный FCM data payload. Проверяется исключительно тип/структура
 * события и совпадение с локальной сессией заказа.
 *
 * ВАЖНО: успешный parse НЕ подтверждает состояние заказа; клиент обязан
 * перечитать его через существующий защищённый passenger API.
 */
public final class PassengerPushEnvelopePolicy {

    public static final String EVENT_TYPE_ORDER_UPDATED = "passenger_order_updated";
    public static final String ENVELOPE_VERSION = "1";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Set<String> ALLOWED_FIELDS =
            Set.of("v", "type", "eventId", "orderId");

    private PassengerPushEnvelopePolicy() {
    }

    /**
     * @param data неподтверждённая карта полей из push
     * @param sessionOrderId ID только своей неистёкшей защищённой сессии;
     *                       caller проверяет expiry до вызова parse.
     * @return минимальное уведомление об обновлении ТОЛЬКО своей поездки.
     */
    @Nullable
    public static OrderUpdatedEvent parse(@Nullable Map<String, String> data,
                                          @Nullable String sessionOrderId) {
        if (data == null || data.size() != ALLOWED_FIELDS.size()
                || !data.keySet().equals(ALLOWED_FIELDS)) {
            return null;
        }
        if (!ENVELOPE_VERSION.equals(data.get("v"))
                || !EVENT_TYPE_ORDER_UPDATED.equals(data.get("type"))) {
            return null;
        }

        String rawOrderId = data.get("orderId");
        String rawEventId = data.get("eventId");
        if (!isCanonicalUuid(rawOrderId) || !isCanonicalUuid(rawEventId)
                || !isCanonicalUuid(sessionOrderId)) {
            return null;
        }

        String allowedOrder = sessionOrderId.toLowerCase(Locale.ROOT);
        String eventOrder = rawOrderId.toLowerCase(Locale.ROOT);
        if (!allowedOrder.equals(eventOrder)) {
            return null;
        }

        return new OrderUpdatedEvent(
                rawEventId.toLowerCase(Locale.ROOT), eventOrder
        );
    }

    private static boolean isCanonicalUuid(@Nullable String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    public static final class OrderUpdatedEvent {

        private final String eventId;
        private final String orderId;

        private OrderUpdatedEvent(@NonNull String eventId,
                                  @NonNull String orderId) {
            this.eventId = eventId;
            this.orderId = orderId;
        }

        @NonNull
        public String getEventId() {
            return eventId;
        }

        @NonNull
        public String getOrderId() {
            return orderId;
        }
    }
}
