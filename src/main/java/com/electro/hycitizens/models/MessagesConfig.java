package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class MessagesConfig {
    private List<CitizenMessage> messages;
    private String selectionMode;
    private boolean enabled;

    public MessagesConfig(@Nonnull List<CitizenMessage> messages, @Nonnull String selectionMode, boolean enabled) {
        this.messages = new ArrayList<>(messages);
        this.selectionMode = selectionMode;
        this.enabled = enabled;
    }

    public MessagesConfig() {
        this.messages = new ArrayList<>();
        this.selectionMode = "RANDOM";
        this.enabled = true;
    }

    @Nonnull
    public List<CitizenMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void setMessages(@Nonnull List<CitizenMessage> messages) {
        this.messages = new ArrayList<>(messages);
    }

    @Nonnull
    public String getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(@Nonnull String selectionMode) {
        this.selectionMode = selectionMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
