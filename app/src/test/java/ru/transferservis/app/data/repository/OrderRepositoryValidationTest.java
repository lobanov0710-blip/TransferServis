package ru.transferservis.app.data.repository;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.ApiService;
import ru.transferservis.app.data.remote.dto.CalculateRequest;
import ru.transferservis.app.data.remote.dto.CalculateResponse;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;
import ru.transferservis.app.data.remote.dto.CreateOrderResponse;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class OrderRepositoryValidationTest {

    private OrderRepository repository;

    @Before
    public void setUp() {

        repository =
                new OrderRepository(
                        new NoNetworkApiService(),
                        new Gson()
                );
    }

    @Test
    public void createOrder_expiredQuote_returnsQuoteExpired() {

        Quote expiredQuote =
                createQuote(
                        "123e4567-e89b-12d3-a456-426614174000",
                        System.currentTimeMillis()
                                - 60_000L
                );

        ApiResult<OrderReceipt> result =
                createOrder(
                        expiredQuote,
                        "Николай",
                        "+7 999 123-45-67",
                        "2026-10-10",
                        null
                );

        assertValidationError(
                result,
                ApiErrorCode.QUOTE_EXPIRED
        );
    }

    @Test
    public void createOrder_invalidQuoteId_returnsInvalidQuoteId() {

        ApiResult<OrderReceipt> result =
                createOrder(
                        createValidQuote(
                                "invalid-id"
                        ),
                        "Николай",
                        "+7 999 123-45-67",
                        "2026-10-10",
                        null
                );

        assertValidationError(
                result,
                ApiErrorCode.INVALID_QUOTE_ID
        );
    }

    @Test
    public void createOrder_emptyName_returnsNameRequired() {

        ApiResult<OrderReceipt> result =
                createOrder(
                        createValidQuote(),
                        "   ",
                        "+7 999 123-45-67",
                        "2026-10-10",
                        null
                );

        assertValidationError(
                result,
                ApiErrorCode.NAME_REQUIRED
        );
    }

    @Test
    public void createOrder_nameOver100Characters_returnsNameTooLong() {

        String name =
                repeat(
                        "А",
                        101
                );

        ApiResult<OrderReceipt> result =
                createOrder(
                        createValidQuote(),
                        name,
                        "+7 999 123-45-67",
                        "2026-10-10",
                        null
                );

        assertValidationError(
                result,
                ApiErrorCode.NAME_TOO_LONG
        );
    }

    @Test
    public void createOrder_shortPhone_returnsPhoneInvalid() {

        ApiResult<OrderReceipt> result =
                createOrder(
                        createValidQuote(),
                        "Николай",
                        "12345",
                        "2026-10-10",
                        null
                );

        assertValidationError(
                result,
                ApiErrorCode.PHONE_INVALID
        );
    }

    @Test
    public void createOrder_phoneWithFormatting_isAcceptedByLocalValidation() {

        AtomicReference<ApiResult<OrderReceipt>> reference =
                new AtomicReference<>();

        try {

            repository.createOrder(
                    createValidQuote(),
                    "Николай",
                    "+7 (999) 123-45-67",
                    "2026-10-10",
                    null,
                    reference::set
            );

        } catch (AssertionError expected) {

            /*
             * Validation passed and repository
             * reached the fake network layer.
             */
            return;
        }

        throw new AssertionError(
                "Valid formatted phone must pass local validation"
        );
    }

    @Test
    public void createOrder_invalidDateFormat_returnsDateInvalid() {

        ApiResult<OrderReceipt> result =
                createOrder(
                        createValidQuote(),
                        "Николай",
                        "+7 999 123-45-67",
                        "10.10.2026",
                        null
                );

        assertValidationError(
                result,
                ApiErrorCode.DATE_INVALID
        );
    }

    @Test
    public void createOrder_commentOver2000Characters_returnsCommentTooLong() {

        String comment =
                repeat(
                        "A",
                        2001
                );

        ApiResult<OrderReceipt> result =
                createOrder(
                        createValidQuote(),
                        "Николай",
                        "+7 999 123-45-67",
                        "2026-10-10",
                        comment
                );

        assertValidationError(
                result,
                ApiErrorCode.COMMENT_TOO_LONG
        );
    }

    private ApiResult<OrderReceipt> createOrder(
            Quote quote,
            String name,
            String phone,
            String date,
            String comment
    ) {

        AtomicReference<ApiResult<OrderReceipt>> resultReference =
                new AtomicReference<>();

        repository.createOrder(
                quote,
                name,
                phone,
                date,
                comment,
                resultReference::set
        );

        ApiResult<OrderReceipt> result =
                resultReference.get();

        assertNotNull(
                result
        );

        return result;
    }

    private Quote createValidQuote() {

        return createValidQuote(
                "123e4567-e89b-12d3-a456-426614174000"
        );
    }

    private Quote createValidQuote(
            String quoteId
    ) {

        return createQuote(
                quoteId,
                System.currentTimeMillis()
                        + 30L * 60L * 1000L
        );
    }

    private Quote createQuote(
            String quoteId,
            long expiresAtMillis
    ) {

        return new Quote(
                quoteId,
                expiresAtMillis,
                "Нижний Новгород",
                "Москва",
                TariffType.COMFORT,
                "Комфорт",
                420.5,
                375,
                23128
        );
    }

    private void assertValidationError(
            ApiResult<OrderReceipt> result,
            ApiErrorCode expectedCode
    ) {

        assertFalse(
                result.isSuccess()
        );

        assertEquals(
                ApiResult.Status.VALIDATION_ERROR,
                result.getStatus()
        );

        assertEquals(
                expectedCode,
                result.getErrorCode()
        );
    }

    private String repeat(
            String value,
            int count
    ) {

        StringBuilder builder =
                new StringBuilder(
                        value.length()
                                * count
                );

        for (int index = 0;
             index < count;
             index++) {

            builder.append(
                    value
            );
        }

        return builder.toString();
    }

    private static final class NoNetworkApiService
            implements ApiService {

        @Override
        public Call<CalculateResponse> calculate(
                CalculateRequest request
        ) {

            throw new AssertionError(
                    "Network must not be called during validation"
            );
        }

        @Override
        public Call<CreateOrderResponse> createOrder(
                CreateOrderRequest request
        ) {

            throw new AssertionError(
                    "Network must not be called during validation"
            );
        }
    }
}