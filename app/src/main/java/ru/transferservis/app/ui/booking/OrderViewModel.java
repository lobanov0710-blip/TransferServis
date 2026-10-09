package ru.transferservis.app.ui.booking;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.data.repository.TripHistoryRepository;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;

public final class OrderViewModel
        extends AndroidViewModel {

    private static final String TAG =
            "OrderViewModel";


    private final OrderRepository repository;

    private final TripHistoryRepository historyRepository;

    private final PassengerOrderSessionStore sessionStore;


    private final MutableLiveData<OrderUiState> uiState =
            new MutableLiveData<>(
                    OrderUiState.idle()
            );


    private long requestSequence =
            0L;


    public OrderViewModel(
            @NonNull Application application
    ) {

        super(
                application
        );


        repository =
                new OrderRepository();


        historyRepository =
                new TripHistoryRepository(
                        application
                );


        sessionStore =
                new PassengerOrderSessionStore(
                        application
                );
    }


    @NonNull
    public LiveData<OrderUiState> getUiState() {

        return uiState;
    }


    public void createOrder(
            @NonNull Quote quote,
            @NonNull String name,
            @NonNull String phone,
            @NonNull String date,
            @Nullable String comment
    ) {

        final long requestId =
                ++requestSequence;


        uiState.setValue(
                OrderUiState.loading()
        );


        repository.createOrder(
                quote,
                name,
                phone,
                date,
                comment,
                result -> {

                    if (
                            requestId !=
                                    requestSequence
                    ) {

                        return;
                    }


                    handleResult(
                            result,
                            quote,
                            date
                    );
                }
        );
    }


    private void handleResult(
            @NonNull ApiResult<OrderReceipt> result,
            @NonNull Quote quote,
            @NonNull String tripDate
    ) {

        if (result.isSuccess()) {

            OrderReceipt receipt =
                    result.getData();


            if (receipt == null) {

                uiState.postValue(
                        OrderUiState.error(
                                ApiErrorCode.UNKNOWN
                        )
                );

                return;
            }


            // ========================================
            // SECURE PASSENGER SESSION
            // ========================================
            //
            // Order already exists on the server.
            //
            // A local secure-storage failure must NOT
            // turn successful server-side creation
            // into an order-creation error, because
            // that could encourage duplicate retries.
            //
            // We never fall back to plaintext token
            // storage.
            // ========================================

            try {

                sessionStore.save(
                        receipt
                );

            } catch (
                    RuntimeException error
            ) {

                Log.e(
                        TAG,
                        "Unable to persist passenger order session",
                        error
                );
            }


            // ========================================
            // LOCAL HISTORY
            // ========================================

            historyRepository.save(
                    quote,
                    receipt,
                    tripDate
            );


            // ========================================
            // UI SUCCESS
            // ========================================

            uiState.postValue(
                    OrderUiState.success(
                            receipt
                    )
            );

            return;
        }


        ApiErrorCode errorCode =
                result.getErrorCode();


        if (errorCode == null) {

            errorCode =
                    ApiErrorCode.UNKNOWN;
        }


        uiState.postValue(
                OrderUiState.error(
                        errorCode
                )
        );
    }


    public void reset() {

        requestSequence++;


        uiState.setValue(
                OrderUiState.idle()
        );
    }
}