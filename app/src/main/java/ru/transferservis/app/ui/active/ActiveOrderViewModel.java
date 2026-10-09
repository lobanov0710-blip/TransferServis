package ru.transferservis.app.ui.active;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.ActiveOrder;

public final class ActiveOrderViewModel
        extends AndroidViewModel {

    private final OrderRepository repository;

    private final PassengerOrderSessionStore sessionStore;

    private final ExecutorService storageExecutor;


    private final MutableLiveData<ActiveOrderUiState> uiState =
            new MutableLiveData<>(
                    ActiveOrderUiState.empty()
            );


    private final AtomicLong requestSequence =
            new AtomicLong(
                    0L
            );


    public ActiveOrderViewModel(
            @NonNull Application application
    ) {

        super(
                application
        );


        repository =
                new OrderRepository();


        sessionStore =
                new PassengerOrderSessionStore(
                        application
                );


        storageExecutor =
                Executors.newSingleThreadExecutor();


        restoreActiveOrder();
    }


    @NonNull
    public LiveData<ActiveOrderUiState> getUiState() {

        return uiState;
    }


    // ========================================
    // RESTORE
    // ========================================

    public void restoreActiveOrder() {

        final long requestId =
                requestSequence
                        .incrementAndGet();


        uiState.setValue(
                ActiveOrderUiState.loading()
        );


        storageExecutor.execute(
                () -> {

                    PassengerOrderSessionStore.Session session =
                            sessionStore.load();


                    if (
                            requestId !=
                                    requestSequence.get()
                    ) {

                        return;
                    }


                    if (session == null) {

                        uiState.postValue(
                                ActiveOrderUiState.empty()
                        );

                        return;
                    }


                    loadRemoteOrder(
                            requestId,
                            session
                    );
                }
        );
    }


    // ========================================
    // REMOTE ORDER
    // ========================================

    private void loadRemoteOrder(
            long requestId,
            @NonNull PassengerOrderSessionStore.Session session
    ) {

        repository.getOrderStatus(
                session.getAccessToken(),
                result -> {

                    if (
                            requestId !=
                                    requestSequence.get()
                    ) {

                        return;
                    }


                    handleRemoteResult(
                            result,
                            session
                    );
                }
        );
    }


    // ========================================
    // RESULT
    // ========================================

    private void handleRemoteResult(
            @NonNull ApiResult<ActiveOrder> result,
            @NonNull PassengerOrderSessionStore.Session session
    ) {

        if (result.isSuccess()) {

            ActiveOrder order =
                    result.getData();


            if (order == null) {

                uiState.postValue(
                        ActiveOrderUiState.error(
                                ApiErrorCode.INVALID_RESPONSE
                        )
                );

                return;
            }


            // ========================================
            // LOCAL / SERVER CONSISTENCY
            // ========================================
            //
            // The server capability token already
            // binds access to one order.
            //
            // We additionally verify that the
            // returned order id matches the local
            // session id.
            // ========================================

            if (
                    !session
                            .getOrderId()
                            .equals(
                                    order.getOrderId()
                            )
            ) {

                storageExecutor.execute(
                        sessionStore::clear
                );


                uiState.postValue(
                        ActiveOrderUiState.error(
                                ApiErrorCode.INVALID_RESPONSE
                        )
                );

                return;
            }


            uiState.postValue(
                    ActiveOrderUiState.success(
                            order
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


        // ========================================
        // IMPORTANT
        // ========================================
        //
        // Network / HTTP failure does not destroy
        // the local capability token.
        //
        // This allows another refresh after a
        // temporary network/server failure.
        // ========================================

        uiState.postValue(
                ActiveOrderUiState.error(
                        errorCode
                )
        );
    }


    // ========================================
    // REFRESH
    // ========================================

    public void refresh() {

        restoreActiveOrder();
    }


    // ========================================
    // CLEAR
    // ========================================

    public void clearActiveOrder() {

        requestSequence
                .incrementAndGet();


        uiState.setValue(
                ActiveOrderUiState.empty()
        );


        storageExecutor.execute(
                sessionStore::clear
        );
    }


    // ========================================
    // CLEANUP
    // ========================================

    @Override
    protected void onCleared() {

        requestSequence
                .incrementAndGet();


        storageExecutor.shutdownNow();


        super.onCleared();
    }
}