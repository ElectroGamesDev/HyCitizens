package com.electro.hycitizens.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public final class CommandExecutionUtil {
    private CommandExecutionUtil() {
    }

    @Nonnull
    public static CompletableFuture<Void> execute(@Nullable PlayerRef player, @Nonnull String command, boolean runAsServer) {
        String processedCommand = normalizeCommand(command);
        if (processedCommand.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CommandManager commandManager = CommandManager.get();
        if (commandManager == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (runAsServer) {
            return commandManager.handleCommand(ConsoleSender.INSTANCE, processedCommand);
        }

        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        Set<String> permissions = getPermissionsForCommand(processedCommand);
        if (permissions.isEmpty()) {
            return commandManager.handleCommand(player, processedCommand);
        }

        return grantPermissionTemporarily(
                player,
                permissions,
                () -> commandManager.handleCommand(player, processedCommand)
        );
    }

    @Nonnull
    public static CompletableFuture<String> executeWithCapture(@Nonnull String command) {
        String processedCommand = normalizeCommand(command);
        if (processedCommand.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }

        CommandManager commandManager = CommandManager.get();
        if (commandManager == null) {
            return CompletableFuture.completedFuture("");
        }

        CapturingConsoleSender capturingConsole = new CapturingConsoleSender();

        // Execute as console. Many Hytale commands (especially those extending CommandBase)
        // schedule their work on the world thread via world.execute(), meaning sendMessage()
        // is called AFTER the command future completes. We add a 200ms delay to let the
        // world-threaded lambda finish before reading the captured output.
        return commandManager.handleCommand(capturingConsole, processedCommand)
                .thenCompose(v -> delayedFuture(200))
                .thenApply(v -> capturingConsole.getCapturedOutput())
                .exceptionally(ex -> {
                    getLogger().atWarning().log("[HyCitizens] Command capture failed: " + ex.getMessage());
                    return capturingConsole.getCapturedOutput();
                });
    }

    private static CompletableFuture<Void> delayedFuture(long millis) {
        CompletableFuture<Void> delayed = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            delayed.complete(null);
        });
        return delayed;
    }
    
    private static class CapturingConsoleSender implements CommandSender {
        private final StringBuilder output = new StringBuilder();
        private final UUID uuid = new UUID(0L, 0L);

        @Override
        public void sendMessage(@Nonnull Message message) {
            if (message != null && message.getRawText() != null) {
                if (output.length() > 0) {
                    output.append("\n");
                }
                output.append(message.getRawText());
            }
        }

        public String getCapturedOutput() {
            return output.toString();
        }

        @Nonnull
        public String getDisplayName() {
            return "Console";
        }

        @Nonnull
        public String getUsername() {
            return "Console";
        }

        @Nonnull
        public UUID getUuid() {
            return uuid;
        }

        public boolean hasPermission(@Nonnull String id) {
            return true;
        }

        public boolean hasPermission(@Nonnull String id, boolean def) {
            return true;
        }
    }

    @Nonnull
    private static String normalizeCommand(@Nullable String command) {
        if (command == null) {
            return "";
        }

        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    @Nonnull
    private static Set<String> getPermissionsForCommand(@Nonnull String command) {
        Set<String> permissions = new LinkedHashSet<>();
        String[] commandParts = command.trim().split("\\s+");
        if (commandParts.length == 0 || commandParts[0].isBlank()) {
            return permissions;
        }

        AbstractCommand resolvedCommand = findRootCommand(commandParts[0]);
        if (resolvedCommand == null) {
            return permissions;
        }

        addPermission(permissions, resolvedCommand);
        for (int i = 1; i < commandParts.length; i++) {
            AbstractCommand subCommand = findSubCommand(resolvedCommand, commandParts[i]);
            if (subCommand == null) {
                break;
            }

            resolvedCommand = subCommand;
            addPermission(permissions, resolvedCommand);
        }

        return permissions;
    }

    @Nonnull
    private static CompletableFuture<Void> grantPermissionTemporarily(@Nonnull PlayerRef player, @Nonnull Set<String> permissions, @Nonnull Supplier<CompletableFuture<Void>> callback) {
        PermissionsModule permissionsModule = PermissionsModule.get();
        if (permissionsModule == null) {
            return callback.get();
        }

        Set<String> missingPermissions = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (!player.hasPermission(permission)) {
                missingPermissions.add(permission);
            }
        }

        if (missingPermissions.isEmpty()) {
            return callback.get();
        }

        UUID playerUuid = player.getUuid();
        permissionsModule.addUserPermission(playerUuid, missingPermissions);

        CompletableFuture<Void> execution;
        try {
            execution = callback.get();
        } catch (Throwable throwable) {
            revokeTemporaryPermissions(permissionsModule, playerUuid, missingPermissions);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            return failed;
        }

        if (execution == null) {
            revokeTemporaryPermissions(permissionsModule, playerUuid, missingPermissions);
            return CompletableFuture.completedFuture(null);
        }

        execution.whenComplete((ignored, throwable) ->
                revokeTemporaryPermissions(permissionsModule, playerUuid, missingPermissions));

        return execution;
    }

    @Nullable
    private static AbstractCommand findRootCommand(@Nonnull String token) {
        CommandManager commandManager = CommandManager.get();
        if (commandManager == null) {
            return null;
        }

        return findCommand(commandManager.getCommandRegistration(), token);
    }

    @Nullable
    private static AbstractCommand findSubCommand(@Nonnull AbstractCommand parentCommand, @Nonnull String token) {
        return findCommand(parentCommand.getSubCommands(), token);
    }

    @Nullable
    private static AbstractCommand findCommand(@Nullable Map<String, AbstractCommand> commands, @Nonnull String token) {
        if (commands == null || commands.isEmpty()) {
            return null;
        }

        String normalizedToken = token.toLowerCase(Locale.ROOT);
        AbstractCommand command = commands.get(token);
        if (command == null) {
            command = commands.get(normalizedToken);
        }
        if (command != null) {
            return command;
        }

        for (AbstractCommand candidate : commands.values()) {
            if (matchesCommandToken(candidate, normalizedToken)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean matchesCommandToken(@Nonnull AbstractCommand command, @Nonnull String normalizedToken) {
        if (command.getName() != null && command.getName().equalsIgnoreCase(normalizedToken)) {
            return true;
        }

        for (String alias : command.getAliases()) {
            if (alias != null && alias.equalsIgnoreCase(normalizedToken)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static String cleanPermission(@Nullable String permission) {
        if (permission == null || permission.isBlank()) {
            return null;
        }
        return permission.trim();
    }

    private static void addPermission(@Nonnull Set<String> permissions, @Nonnull AbstractCommand command) {
        String permission = cleanPermission(command.getPermission());
        if (permission != null) {
            permissions.add(permission);
        }
    }

    private static void revokeTemporaryPermissions(@Nonnull PermissionsModule permissionsModule, @Nonnull UUID playerUuid, @Nonnull Set<String> permissions) {
        try {
            permissionsModule.removeUserPermission(playerUuid, permissions);
        } catch (Throwable throwable) {
            getLogger().atWarning().log("Failed to revoke temporary command permissions " + permissions + ": " + throwable.getMessage());
        }
    }
}
