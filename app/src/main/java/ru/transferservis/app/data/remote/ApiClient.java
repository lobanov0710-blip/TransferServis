package ru.transferservis.app.data.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

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

    private static final OkHttpClient HTTP_CLIENT =
            createHttpClient();

    private static OkHttpClient createHttpClient() {
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        // Unknown booking outcome must not trigger automatic POST retry.
                        .retryOnConnectionFailure(false);

        // Only the temporary, revocable staging tester token is included in Debug.
        // The Worker HMAC secret must remain server-side in the PHP staging gateway.
        if (BuildConfig.DEBUG && !BuildConfig.STAGING_ACCESS_TOKEN.isEmpty()) {
            builder.addInterceptor(chain ->
                    chain.proceed(
                            chain.request().newBuilder()
                                    .header(
                                            "X-Staging-Access-Token",
                                            BuildConfig.STAGING_ACCESS_TOKEN
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
}