package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class CitizenMessage {
    private String message;

    public CitizenMessage(@Nonnull String message) {
        this.message = message;
    }

    public CitizenMessage() {
        this.message = "";
    }

    @Nonnull
    public String getMessage() {
        return message;
    }

    public void setMessage(@Nonnull String message) {
        this.message = message;
    }
}
