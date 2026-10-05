package ru.transferservis.app.domain.model;

import org.junit.Test;

import ru.transferservis.app.data.model.TariffType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class QuoteTest {

    @Test
    public void isExpired_returnsFalseBeforeExpiration() {

        long expiration =
                2_000L;

        Quote quote =
                createQuote(
                        expiration
                );

        assertFalse(
                quote.isExpired(
                        1_999L
                )
        );
    }

    @Test
    public void isExpired_returnsTrueAtExpirationBoundary() {

        long expiration =
                2_000L;

        Quote quote =
                createQuote(
                        expiration
                );

        assertTrue(
                quote.isExpired(
                        2_000L
                )
        );
    }

    @Test
    public void isExpired_returnsTrueAfterExpiration() {

        Quote quote =
                createQuote(
                        2_000L
                );

        assertTrue(
                quote.isExpired(
                        2_001L
                )
        );
    }

    private Quote createQuote(
            long expiresAtMillis
    ) {

        return new Quote(
                "123e4567-e89b-12d3-a456-426614174000",
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
}