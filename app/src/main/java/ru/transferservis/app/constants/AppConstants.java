package ru.transferservis.app.constants;

import ru.transferservis.app.BuildConfig;

public final class AppConstants {

    private AppConstants() {
        throw new IllegalStateException("Utility class");
    }


    public static final String API_BASE_URL =
            BuildConfig.API_BASE_URL;


    public static final String API_CALCULATE =
            "api/calculate.php";


    public static final String API_ORDERS =
            "api/order.php";


    public static final String API_ORDER_STATUS =
            "api/order-status.php";
}