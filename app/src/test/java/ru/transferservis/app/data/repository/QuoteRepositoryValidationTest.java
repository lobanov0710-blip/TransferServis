package ru.transferservis.app.data.repository;

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
import ru.transferservis.app.domain.model.Quote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class QuoteRepositoryValidationTest {

    private QuoteRepository repository;

    @Before
    public void setUp() {

        repository =
                new QuoteRepository(
                        new NoNetworkApiService()
                );
    }

    @Test
    public void calculateQuote_shortFrom_returnsFromRequired() {

        ApiResult<Quote> result =
                calculate(
                        "",
                        "Москва"
                );

        assertValidationError(
                result,
                ApiErrorCode.FROM_REQUIRED
        );
    }

    @Test
    public void calculateQuote_whitespaceFrom_returnsFromRequired() {

        ApiResult<Quote> result =
                calculate(
                        "   ",
                        "Москва"
                );

        assertValidationError(
                result,
                ApiErrorCode.FROM_REQUIRED
        );
    }

    @Test
    public void calculateQuote_shortTo_returnsToRequired() {

        ApiResult<Quote> result =
                calculate(
                        "Нижний Новгород",
                        ""
                );

        assertValidationError(
                result,
                ApiErrorCode.TO_REQUIRED
        );
    }

    private ApiResult<Quote> calculate(
            String from,
            String to
    ) {

        AtomicReference<ApiResult<Quote>> resultReference =
                new AtomicReference<>();

        repository.calculateQuote(
                from,
                to,
                TariffType.COMFORT,
                resultReference::set
        );

        ApiResult<Quote> result =
                resultReference.get();

        assertNotNull(
                result
        );

        return result;
    }

    private void assertValidationError(
            ApiResult<Quote> result,
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