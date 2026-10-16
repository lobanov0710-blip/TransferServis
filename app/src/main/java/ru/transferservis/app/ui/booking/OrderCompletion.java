package ru.transferservis.app.ui.booking;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.domain.model.OrderReceipt;

/** Completes local work after the server has already accepted a booking. */
public final class OrderCompletion {

    private OrderCompletion() {
    }

    public interface SessionWriter {
        boolean save(@NonNull OrderReceipt receipt);
    }

    /** UI request freshness belongs to the publisher, never to confirmed local work. */
    public static void handleResult(
            @NonNull ApiResult<OrderReceipt> result,
            @NonNull Executor completionExecutor,
            @NonNull SessionWriter saveSession,
            @NonNull Consumer<OrderReceipt> saveHistory,
            @NonNull Consumer<OrderUiState> publishState
    ) {
        OrderReceipt receipt = result.getData();
        if (result.isSuccess() && receipt != null) {
            completionExecutor.execute(() -> publishState.accept(complete(receipt,
                    () -> saveSession.save(receipt), () -> saveHistory.accept(receipt))));
            return;
        }
        ApiErrorCode error = result.getErrorCode();
        publishState.accept(OrderUiState.error(error == null ? ApiErrorCode.UNKNOWN : error));
    }

    @NonNull
    public static OrderUiState complete(
            @NonNull OrderReceipt receipt,
            @NonNull BooleanSupplier saveSession,
            @NonNull Runnable saveHistory
    ) {
        try {
            // Queue the original row before publishing a discoverable session. A fast status
            // refresh must enqueue its UPDATE after this INSERT on the history executor.
            saveHistory.run();
        } catch (RuntimeException ignored) {
            // A local history failure cannot undo a confirmed server booking.
        }

        boolean accessSaved = false;
        try {
            accessSaved = saveSession.getAsBoolean();
        } catch (RuntimeException ignored) {
            // The success screen explains that access could not be saved.
            // Retrying order creation would risk creating a duplicate booking.
        }

        return OrderUiState.success(receipt, accessSaved);
    }
}
