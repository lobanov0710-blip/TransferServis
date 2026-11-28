package ru.transferservis.app.data.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.transferservis.app.constants.AppConstants;
import ru.transferservis.app.BuildConfig;

public final class ApiClient {

    private static final long CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long READ_TIMEOUT_SECONDS = 30L;
    private static final long WRITE_TIMEOUT_SECONDS = 15L;
    private static final long CALL_TIMEOUT_SECONDS = 45L;
    private static final String TESTER_HEADER = "X-Staging-Access-Token";
    private static volatile String tz02QaTesterToken = "";

    private static final Gson GSON =
            new GsonBuilder()
                    .create();

    private static final OkHttpClient HTTP_CLIENT =
            createHttpClient(BuildConfig.STAGING_ACCESS_TOKEN);

    // Открыто в пределах package для локальных HTTP-тестов без настоящего staging.
    static OkHttpClient createHttpClient(String stagingAccessToken) {
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        // Status v2 must stay on the approved staging origin, including redirects.
                        .followRedirects(!BuildConfig.PASSENGER_STATUS_V2)
                        // Не повторяем POST автоматически: при потере ответа заказ
                        // мог уже сохраниться на сервере.
                        .retryOnConnectionFailure(false);

        // Только Debug получает временный токен тестового PHP-шлюза.
        // HMAC-секрет Worker никогда не хранится в Android-приложении.
        if (BuildConfig.PASSENGER_TZ02_QA) {
            HttpUrl qaStatusUrl = HttpUrl.get(BuildConfig.API_BASE_URL + BuildConfig.PASSENGER_STATUS_V2_PATH);
            builder.addInterceptor(chain -> {
                Request request = chain.request();
                String token = tz02QaTesterToken;
                if (!request.method().equals("POST") || !request.url().equals(qaStatusUrl) || token.isEmpty()) {
                    throw new IOException("TZ-02 QA permits only authenticated status reads");
                }
                return chain.proceed(request.newBuilder().header(TESTER_HEADER, token).build());
            });
        } else if (BuildConfig.DEBUG && stagingAccessToken != null &&
                !stagingAccessToken.isEmpty()) {
            builder.addInterceptor(chain ->
                    chain.proceed(
                            chain.request().newBuilder()
                                    .header(
                                            TESTER_HEADER,
                                            stagingAccessToken
                                    )
                                    .build()
                    )
            );
        }

        return builder.build();
    }

    private static final Retrofit RETROFIT =
            new Retrofit.Builder()

                    .baseUrl(
                            AppConstants.API_BASE_URL
                    )

                    .client(
                            HTTP_CLIENT
                    )

                    .addConverterFactory(
                            GsonConverterFactory.create(
                                    GSON
                            )
                    )

                    .build();

    private static final ApiService API_SERVICE =
            RETROFIT.create(
                    ApiService.class
            );

    private ApiClient() {
        throw new IllegalStateException(
                "Utility class"
        );
    }

    public static ApiService getApiService() {
        return API_SERVICE;
    }

    public static void configureTz02QaTesterToken(String token) {
        if (BuildConfig.PASSENGER_TZ02_QA && token.matches("[a-f0-9]{64}")) {
            tz02QaTesterToken = token;
        }
    }
}
