package ru.transferservis.app.data.remote;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ApiResultTest {

    @Test
    public void success_preservesDataAndHttpCode() {

        String data =
                "result";

        ApiResult<String> result =
                ApiResult.success(
                        data,
                        201
                );

        assertTrue(
                result.isSuccess()
        );

        assertEquals(
                ApiResult.Status.SUCCESS,
                result.getStatus()
        );

        assertEquals(
                data,
                result.getData()
        );

        assertEquals(
                201,
                result.getHttpCode()
        );

        assertNull(
                result.getErrorCode()
        );

        assertNull(
                result.getCause()
        );
    }

    @Test
    public void validationError_containsErrorCode() {

        ApiResult<String> result =
                ApiResult.validationError(
                        ApiErrorCode.PHONE_INVALID
                );

        assertFalse(
                result.isSuccess()
        );

        assertEquals(
                ApiResult.Status.VALIDATION_ERROR,
                result.getStatus()
        );

        assertEquals(
                ApiErrorCode.PHONE_INVALID,
                result.getErrorCode()
        );

        assertEquals(
                0,
                result.getHttpCode()
        );

        assertNull(
                result.getData()
        );
    }

    @Test
    public void httpError_preservesHttpCode() {

        ApiResult<String> result =
                ApiResult.httpError(
                        429,
                        ApiErrorCode.RATE_LIMITED
                );

        assertFalse(
                result.isSuccess()
        );

        assertEquals(
                ApiResult.Status.HTTP_ERROR,
                result.getStatus()
        );

        assertEquals(
                ApiErrorCode.RATE_LIMITED,
                result.getErrorCode()
        );

        assertEquals(
                429,
                result.getHttpCode()
        );
    }

    @Test
    public void networkError_preservesCause() {

        IOException exception =
                new IOException(
                        "network"
                );

        ApiResult<String> result =
                ApiResult.networkError(
                        ApiErrorCode.NETWORK,
                        exception
                );

        assertFalse(
                result.isSuccess()
        );

        assertEquals(
                ApiResult.Status.NETWORK_ERROR,
                result.getStatus()
        );

        assertEquals(
                ApiErrorCode.NETWORK,
                result.getErrorCode()
        );

        assertSame(
                exception,
                result.getCause()
        );
    }
}