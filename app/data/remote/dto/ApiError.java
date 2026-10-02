package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class ApiError {

    private Boolean ok;
    private String error;

    @Nullable
    public Boolean getOk() {
        return ok;
    }

    @Nullable
    public String getError() {
        return error;
    }
}