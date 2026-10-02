package ru.transferservis.app.data.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.transferservis.app.constants.AppConstants;

public final class ApiClient {

    private static final long CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long READ_TIMEOUT_SECONDS = 30L;
    private static final long WRITE_TIMEOUT_SECONDS = 15L;
    private static final long CALL_TIMEOUT_SECONDS = 45L;

    private static final Gson GSON =
            new GsonBuilder()
                    .create();

    private static final OkHttpClient HTTP_CLIENT =
            new OkHttpClient.Builder()

                    .connectTimeout(
                            CONNECT_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )

                    .readTimeout(
                            READ_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )

                    .writeTimeout(
                            WRITE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )

                    .callTimeout(
                            CALL_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )

                    .retryOnConnectionFailure(true)

                    .build();

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