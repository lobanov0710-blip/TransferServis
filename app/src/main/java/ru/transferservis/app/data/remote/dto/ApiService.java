package ru.transferservis.app.data.remote;

import ru.transferservis.app.constants.AppConstants;
import ru.transferservis.app.data.remote.dto.CalculateRequest;
import ru.transferservis.app.data.remote.dto.CalculateResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ApiService {

    @Headers({
            "Accept: application/json"
    })
    @POST(AppConstants.API_CALCULATE)
    Call<CalculateResponse> calculate(
            @Body CalculateRequest request
    );
}