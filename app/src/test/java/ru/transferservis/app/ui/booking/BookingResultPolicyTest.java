package ru.transferservis.app.ui.booking;

import org.junit.Test;

import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;

import static org.junit.Assert.*;

public final class BookingResultPolicyTest {
    @Test
    public void lostNetworkReplyCouldFollowACommittedOrder() {
        assertTrue(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.networkError(ApiErrorCode.NETWORK, new java.io.IOException())));
    }

    @Test
    public void malformedSuccessCouldHideAConfirmedBooking() {
        assertTrue(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.invalidResponse(201, ApiErrorCode.INVALID_RESPONSE)));
    }

    @Test
    public void gateway502AndServer500AreIndeterminate() {
        assertTrue(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.httpError(502, ApiErrorCode.SERVICE_UNAVAILABLE)));
        assertTrue(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.httpError(500, ApiErrorCode.ORDER_CREATE_FAILED)));
    }

    @Test
    public void rateLimitAndQuoteConflictRequireReconciliation() {
        assertTrue(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.httpError(409, ApiErrorCode.QUOTE_ALREADY_USED)));
        assertTrue(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.httpError(429, ApiErrorCode.RATE_LIMITED)));
    }

    @Test
    public void validationAndConfirmedSuccessAreNotIndeterminate() {
        assertFalse(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.validationError(ApiErrorCode.PHONE_INVALID)));
        assertFalse(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.httpError(400, ApiErrorCode.PHONE_INVALID)));
        assertFalse(BookingResultPolicy.isOutcomeUnknown(
                ApiResult.success("confirmed", 201)));
    }

    @Test
    public void uiStateRetainsDistinctResultUnknownStatus() {
        OrderUiState state = OrderUiState.resultUnknown();
        assertEquals(OrderUiState.Status.RESULT_UNKNOWN, state.getStatus());
        assertFalse(state.isSuccess());
        assertFalse(state.isLoading());
        assertNull(state.getReceipt());
    }
}
