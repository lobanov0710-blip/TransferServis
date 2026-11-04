package ru.transferservis.app.data.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.transferservis.app.constants.AppConstants;
import ru.transferservis.app.BuildConfig;

public final class ApiClient {

    private static final long CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long READ_TIMEOUT_SECONDS = 30L;
    private static final long WRITE_TIMEOUT_SECONDS = 15L;
    private static final long CALL_TIMEOUT_SECONDS = 45L;

    private static final Gson GSON =
            new GsonBuilder()
                    .create();

    // Single origin for the staging credential. Never infer trust from a
    // substring of an arbitrary request URL or a redirect's destination.
    private static final HttpUrl STAGING_BASE_URL =
            HttpUrl.get(BuildConfig.API_BASE_URL);

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
                        // Не повторяем POST автоматически: при потере ответа заказ
                        // мог уже сохраниться на сервере.
                        .retryOnConnectionFailure(false);

        // Только Debug получает временный токен тестового PHP-шлюза.
        // HMAC-секрет Worker никогда не хранится в Android-приложении.
        if (BuildConfig.DEBUG && stagingAccessToken != null &&
                !stagingAccessToken.isEmpty()) {
            // Do not forward a custom bearer header across HTTP redirects.
            // Unlike Authorization, a custom header is not necessarily stripped
            // by the HTTP client when the redirect changes origin.
            builder.followRedirects(false);
            builder.followSslRedirects(false);
            builder.addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                if (!isApprovedStagingOrigin(request.url())) {
                    // Never attach credentials to an HTTP, foreign-host,
                    // different-port or userinfo-bearing request.
                    return chain.proceed(request);
                }
                return chain.proceed(request.newBuilder()
                        .header("X-Staging-Access-Token", stagingAccessToken)
                        .build());
            });
        }

        return builder.build();
    }

    // Package-private for offline unit tests. Only one configured HTTPS
    // origin can receive the temporary debug credential.
    static boolean isApprovedStagingOrigin(HttpUrl requestUrl) {
        return BuildConfig.DEBUG
                && "https".equals(STAGING_BASE_URL.scheme())
                && "https".equals(requestUrl.scheme())
                && requestUrl.host().equals(STAGING_BASE_URL.host())
                && requestUrl.port() == STAGING_BASE_URL.port()
                && requestUrl.username().isEmpty()
                && requestUrl.password().isEmpty();
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

    private static final PassengerPushApiService PASSENGER_PUSH_API =
            RETROFIT.create(PassengerPushApiService.class);

    private ApiClient() {
        throw new IllegalStateException(
                "Utility class"
        );
    }

    public static ApiService getApiService() {
        return API_SERVICE;
    }

    // This getter only returns an interface; it never performs a request.
    public static PassengerPushApiService getPassengerPushApiService() {
        return PASSENGER_PUSH_API;
    }
}