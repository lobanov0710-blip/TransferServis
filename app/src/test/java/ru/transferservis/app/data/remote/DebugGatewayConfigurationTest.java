package ru.transferservis.app.data.remote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.constants.AppConstants;

public final class DebugGatewayConfigurationTest {

    @Test
    public void debugNeverDefaultsToProductionGateway() {
        assertTrue(BuildConfig.DEBUG);
        assertNotEquals(
                "https://transfer-servis52.ru/",
                AppConstants.API_BASE_URL
        );
    }

    @Test
    public void debugGatewayUsesHttpsAndTrailingSlash() {
        assertTrue(AppConstants.API_BASE_URL.startsWith("https://"));
        assertTrue(AppConstants.API_BASE_URL.endsWith("/"));
        assertFalse(AppConstants.API_BASE_URL.contains("http://"));
    }
}
