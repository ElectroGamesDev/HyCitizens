package com.electro.hycitizens.interactions;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CitizenMessage;
import com.electro.hycitizens.models.CommandAction;
import com.electro.hycitizens.models.MessagesConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CitizenInteraction {

    public static final String SOURCE_LEFT_CLICK = "LEFT_CLICK";
    public static final String SOURCE_F_KEY = "F_KEY";

    private static final Map<String, Color> NAMED_COLORS = Map.ofEntries(
            Map.entry("BLACK", Color.decode("#000000")),
            Map.entry("WHITE", Color.decode("#FFFFFF")),
            Map.entry("RED", Color.decode("#FF0000")),
            Map.entry("GREEN", Color.decode("#00FF00")),
            Map.entry("BLUE", Color.decode("#0000FF")),
            Map.entry("YELLOW", Color.decode("#FFFF00")),
            Map.entry("ORANGE", Color.decode("#FFA500")),
            Map.entry("PINK", Color.decode("#FFC0CB")),
            Map.entry("PURPLE", Color.decode("#800080")),
            Map.entry("CYAN", Color.decode("#00FFFF")),
            Map.entry("MAGENTA", Color.decode("#FF00FF")),
            Map.entry("LIME", Color.decode("#00FF00")),
            Map.entry("MAROON", Color.decode("#800000")),
            Map.entry("NAVY", Color.decode("#000080")),
            Map.entry("TEAL", Color.decode("#008080")),
            Map.entry("OLIVE", Color.decode("#808000")),
            Map.entry("SILVER", Color.decode("#C0C0C0")),
            Map.entry("GRAY", Color.decode("#808080")),
            Map.entry("GREY", Color.decode("#808080")),
            Map.entry("BROWN", Color.decode("#A52A2A")),
            Map.entry("GOLD", Color.decode("#FFD700")),
            Map.entry("ORCHID", Color.decode("#DA70D6")),
            Map.entry("SALMON", Color.decode("#FA8072")),
            Map.entry("TURQUOISE", Color.decode("#40E0D0")),
            Map.entry("VIOLET", Color.decode("#EE82EE")),
            Map.entry("INDIGO", Color.decode("#4B0082")),
            Map.entry("CORAL", Color.decode("#FF7F50")),
            Map.entry("CRIMSON", Color.decode("#DC143C")),
            Map.entry("KHAKI", Color.decode("#F0E68C")),
            Map.entry("PLUM", Color.decode("#DDA0DD")),
            Map.entry("CHOCOLATE", Color.decode("#D2691E")),
            Map.entry("TAN", Color.decode("#D2B48C")),
            Map.entry("LIGHTBLUE", Color.decode("#ADD8E6")),
            Map.entry("LIGHTGREEN", Color.decode("#90EE90")),
            Map.entry("LIGHTGRAY", Color.decode("#D3D3D3")),
            Map.entry("LIGHTGREY", Color.decode("#D3D3D3")),
            Map.entry("DARKRED", Color.decode("#8B0000")),
            Map.entry("DARKGREEN", Color.decode("#006400")),
            Map.entry("DARKBLUE", Color.decode("#00008B")),
            Map.entry("DARKGRAY", Color.decode("#A9A9A9")),
            Map.entry("DARKGREY", Color.decode("#A9A9A9")),
            Map.entry("LIGHTPINK", Color.decode("#FFB6C1")),
            Map.entry("LIGHTYELLOW", Color.decode("#FFFFE0")),
            Map.entry("LIGHTCYAN", Color.decode("#E0FFFF")),
            Map.entry("LIGHTMAGENTA", Color.decode("#FF77FF")),
            Map.entry("ORANGERED", Color.decode("#FF4500")),
            Map.entry("DEEPSKYBLUE", Color.decode("#00BFFF"))
    );

    private static final Pattern COLOR_PATTERN = Pattern.compile("(\\{[A-Za-z]+})|(\\{#[0-9A-Fa-f]{6}})|([^\\{]+)");
    private static final Random RANDOM = new Random();

    @Nullable
    public static Message parseColoredMessage(@Nonnull String messageContent) {
        Matcher matcher = COLOR_PATTERN.matcher(messageContent);

        Message msg = null;
        Color currentColor = null;

        while (matcher.find()) {
            String namedColorToken = matcher.group(1);
            String hexColorToken = matcher.group(2);
            String textPart = matcher.group(3);

            // {RED}, {GREEN}, etc
            if (namedColorToken != null) {
                String colorKey = namedColorToken.substring(1, namedColorToken.length() - 1).toUpperCase();
                currentColor = NAMED_COLORS.getOrDefault(colorKey, null);
                continue;
            }

            // {#7CFC00}, etc
            if (hexColorToken != null) {
                String hex = hexColorToken.substring(1, hexColorToken.length() - 1); // remove { }
                try {
                    currentColor = Color.decode(hex);
                } catch (Exception ignored) {
                    currentColor = null;
                }
                continue;
            }

            // Text chunk
            if (textPart != null && !textPart.isEmpty()) {
                Message part = Message.raw(textPart);

                if (currentColor != null) {
                    part = part.color(currentColor);
                }

                if (msg == null) {
                    msg = part;
                } else {
                    msg = msg.insert(part);
                }
            }
        }

        return msg;
    }

    static public void handleInteraction(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef) {
        handleInteraction(citizen, playerRef, SOURCE_F_KEY);
    }

    static public void handleInteraction(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef,
                                         @Nonnull String interactionSource) {
        CitizenInteractEvent interactEvent = new CitizenInteractEvent(citizen, playerRef);
        HyCitizensPlugin.get().getCitizensManager().fireCitizenInteractEvent(interactEvent);

        if (interactEvent.isCancelled())
            return;

        MessagesConfig msgConfig = citizen.getMessagesConfig();
        List<CitizenMessage> allMessages = msgConfig.isEnabled() ? msgConfig.getMessages() : List.of();
        List<CitizenMessage> matchingMessages = allMessages.stream()
                .filter(m -> m.isTriggeredBy(interactionSource))
                .collect(Collectors.toList());

        List<CommandAction> matchingCommands = citizen.getCommandActions().stream()
                .filter(cmd -> cmd.isTriggeredBy(interactionSource))
                .collect(Collectors.toList());

        // Nothing to do for this interaction type, exit silently.
        if (matchingMessages.isEmpty() && matchingCommands.isEmpty()) {
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();

        Player player = ref.getStore().getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("An error occurred").color(Color.RED));
            return;
        }

        // Permission check
        if (!citizen.getRequiredPermission().isEmpty()) {
            if (!player.hasPermission(citizen.getRequiredPermission())) {
                String permissionMessage = citizen.getNoPermissionMessage();

                if (permissionMessage.isEmpty()) {
                    permissionMessage = "You do not have permissions";
                }

                player.sendMessage(Message.raw(permissionMessage).color(Color.RED));
                return;
            }
        }

        // Trigger ON_INTERACT animations
        HyCitizensPlugin.get().getCitizensManager().triggerAnimations(citizen, "ON_INTERACT");

        // Handle messages
        if (!matchingMessages.isEmpty()) {
            String mode = msgConfig.getSelectionMode();
            switch (mode) {
                case "SEQUENTIAL" -> sendSequentialMessage(citizen, playerRef, matchingMessages);
                case "ALL" -> sendAllMessages(playerRef, citizen, matchingMessages);
                default -> sendRandomMessage(playerRef, citizen, matchingMessages); // RANDOM
            }
        }

        // Run matching commands (with per-command delays chained sequentially)
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (CommandAction commandAction : matchingCommands) {
            chain = chain.thenCompose(v -> {
                if (commandAction.getDelaySeconds() > 0) {
                    CompletableFuture<Void> delayedFuture = new CompletableFuture<>();
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        delayedFuture.complete(null);
                    }, (long) (commandAction.getDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
                    return delayedFuture;
                }
                return CompletableFuture.completedFuture(null);
            }).thenCompose(v -> {
                String command = commandAction.getCommand();

                command = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE)
                        .matcher(command)
                        .replaceAll(playerRef.getUsername());

                command = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE)
                        .matcher(command)
                        .replaceAll(citizen.getName());

                if (command.startsWith("{SendMessage}")) {
                    String messageContent = command.substring("{SendMessage}".length()).trim();
                    Message msg = parseColoredMessage(messageContent);
                    if (msg != null) {
                        playerRef.sendMessage(msg);
                    }
                    return CompletableFuture.completedFuture(null);
                } else {
                    if (commandAction.isRunAsServer()) {
                        return CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
                    } else {
                        return CommandManager.get().handleCommand(player, command);
                    }
                }
            });
        }
    }

    private static void sendRandomMessage(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen,
                                          @Nonnull List<CitizenMessage> messages) {
        CitizenMessage selected = messages.get(RANDOM.nextInt(messages.size()));
        dispatchMessage(playerRef, citizen, selected);
    }

    private static void sendSequentialMessage(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef,
                                              @Nonnull List<CitizenMessage> messages) {
        UUID playerUUID = playerRef.getUuid();
        int startIndex = citizen.getSequentialMessageIndex().getOrDefault(playerUUID, 0);

        int subsetIndex = startIndex % messages.size();
        CitizenMessage selected = messages.get(subsetIndex);
        citizen.getSequentialMessageIndex().put(playerUUID, subsetIndex + 1);

        dispatchMessage(playerRef, citizen, selected);
    }

    private static void sendAllMessages(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen,
                                        @Nonnull List<CitizenMessage> messages) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (CitizenMessage cm : messages) {
            final CitizenMessage msg = cm;
            if (cm.getDelaySeconds() > 0) {
                chain = chain.thenCompose(v -> {
                    CompletableFuture<Void> delayed = new CompletableFuture<>();
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(
                            () -> delayed.complete(null),
                            (long) (msg.getDelaySeconds() * 1000),
                            TimeUnit.MILLISECONDS);
                    return delayed;
                });
            }
            chain = chain.thenCompose(v -> {
                String text = replacePlaceholders(msg.getMessage(), playerRef, citizen);
                Message parsed = parseColoredMessage(text);
                if (parsed != null) playerRef.sendMessage(parsed);
                return CompletableFuture.completedFuture(null);
            });
        }
    }

    private static void dispatchMessage(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen,
                                        @Nonnull CitizenMessage cm) {
        String text = replacePlaceholders(cm.getMessage(), playerRef, citizen);
        Message parsed = parseColoredMessage(text);
        if (parsed == null) return;

        if (cm.getDelaySeconds() > 0) {
            final Message finalMsg = parsed;
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                    () -> playerRef.sendMessage(finalMsg),
                    (long) (cm.getDelaySeconds() * 1000),
                    TimeUnit.MILLISECONDS);
        } else {
            playerRef.sendMessage(parsed);
        }
    }

    private static String replacePlaceholders(@Nonnull String text, @Nonnull PlayerRef playerRef,
                                              @Nonnull CitizenData citizen) {
        text = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(playerRef.getUsername());
        text = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(citizen.getName());
        return text;
    }
}
