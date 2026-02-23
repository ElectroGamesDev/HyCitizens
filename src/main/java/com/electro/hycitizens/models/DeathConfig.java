package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class DeathConfig {
    private List<DeathDropItem> dropItems;
    private List<CommandAction> deathCommands;
    private List<CitizenMessage> deathMessages;
    private String commandSelectionMode;
    private String messageSelectionMode;

    public DeathConfig() {
        this.dropItems = new ArrayList<>();
        this.deathCommands = new ArrayList<>();
        this.deathMessages = new ArrayList<>();
        this.commandSelectionMode = "ALL";
        this.messageSelectionMode = "ALL";
    }

    @Nonnull
    public List<DeathDropItem> getDropItems() { return new ArrayList<>(dropItems); }
    public void setDropItems(@Nonnull List<DeathDropItem> dropItems) { this.dropItems = new ArrayList<>(dropItems); }

    @Nonnull
    public List<CommandAction> getDeathCommands() { return new ArrayList<>(deathCommands); }
    public void setDeathCommands(@Nonnull List<CommandAction> deathCommands) { this.deathCommands = new ArrayList<>(deathCommands); }

    @Nonnull
    public List<CitizenMessage> getDeathMessages() { return new ArrayList<>(deathMessages); }
    public void setDeathMessages(@Nonnull List<CitizenMessage> deathMessages) { this.deathMessages = new ArrayList<>(deathMessages); }

    @Nonnull
    public String getCommandSelectionMode() { return commandSelectionMode; }
    public void setCommandSelectionMode(@Nonnull String commandSelectionMode) { this.commandSelectionMode = commandSelectionMode; }

    @Nonnull
    public String getMessageSelectionMode() { return messageSelectionMode; }
    public void setMessageSelectionMode(@Nonnull String messageSelectionMode) { this.messageSelectionMode = messageSelectionMode; }
}
