package ru.transferservis.app.constants;

public final class AppConstants {

    private AppConstants() {
        throw new IllegalStateException("Utility class");
    }


    public static final String API_BASE_URL =
            "https://transfer-servis52.ru/";


    public static final String API_CALCULATE =
            "api/calculate.php";


    public static final String API_ORDERS =
            "api/order.php";


    public static final String API_ORDER_STATUS =
            "api/order-status.php";
}