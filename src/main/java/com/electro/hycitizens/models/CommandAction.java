package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class CommandAction {
    private String command;
    private boolean runAsServer;

    public CommandAction(@Nonnull String command, boolean runAsServer) {
        this.command = command;
        this.runAsServer = runAsServer;
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

    @Nonnull
    public String getFormattedCommand() {
        return "/" + command;
    }
}