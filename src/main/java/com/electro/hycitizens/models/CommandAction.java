package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CommandAction {
    private String command;
    private boolean runAsServer;
    private float delaySeconds;
    @Nullable
    private String interactionTrigger;

    public CommandAction(@Nonnull String command, boolean runAsServer) {
        this(command, runAsServer, 0.0f, "BOTH");
    }

    public CommandAction(@Nonnull String command, boolean runAsServer, float delaySeconds) {
        this(command, runAsServer, delaySeconds, "BOTH");
    }

    public CommandAction(@Nonnull String command, boolean runAsServer, float delaySeconds, @Nullable String interactionTrigger) {
        this.command = command;
        this.runAsServer = runAsServer;
        this.delaySeconds = delaySeconds;
        this.interactionTrigger = interactionTrigger;
    }

    @Nonnull
    public String getCommand() {
        return command;
    }

    public void setCommand(@Nonnull String command) {
        this.command = command;
    }

    public boolean isRunAsServer() {
        return runAsServer;
    }

    public void setRunAsServer(boolean runAsServer) {
        this.runAsServer = runAsServer;
    }

    public float getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(float delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    @Nullable
    public String getInteractionTrigger() {
        return interactionTrigger;
    }

    public void setInteractionTrigger(@Nullable String interactionTrigger) {
        this.interactionTrigger = interactionTrigger;
    }

    public boolean isTriggeredBy(@Nonnull String interactionSource) {
        String trigger = interactionTrigger != null ? interactionTrigger : "BOTH";
        return "BOTH".equals(trigger) || trigger.equals(interactionSource);
    }

    @Nonnull
    public String getFormattedCommand() {
        return "/" + command;
    }
}
