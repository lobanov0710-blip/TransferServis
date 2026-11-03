package ru.transferservis.app.data.remote;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import ru.transferservis.app.constants.AppConstants;

/**
 * Only PHP gateway URLs; no direct Worker URL or client-side HMAC key.
 * Declaring methods does not execute any requests.
 */
public interface PassengerPushApiService {

    @Headers({"Accept: application/json", "Cache-Control: no-store"})
    @POST(AppConstants.API_PASSENGER_PUSH_GRANT)
    Call<PassengerPushPayloads.GrantResponse> issueGrant(
            @Body PassengerPushPayloads.GrantRequest request
    );

    @Headers({"Accept: application/json", "Cache-Control: no-store"})
    @POST(AppConstants.API_PASSENGER_PUSH_DEVICE)
    Call<PassengerPushPayloads.OperationResponse> register(
            @Body PassengerPushPayloads.DeviceRequest request
    );

    @Headers({"Accept: application/json", "Cache-Control: no-store"})
    @HTTP(method = "DELETE", path = AppConstants.API_PASSENGER_PUSH_DEVICE,
            hasBody = true)
    Call<PassengerPushPayloads.OperationResponse> revoke(
            @Body PassengerPushPayloads.RevokeRequest request
    );
}
