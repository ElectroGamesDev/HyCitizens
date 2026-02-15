package com.electro.hycitizens.interactions;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CitizenMessage;
import com.electro.hycitizens.models.CommandAction;
import com.electro.hycitizens.models.MessagesConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CitizenInteraction {

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

    static public void handleInteraction(CitizenData citizen, PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();

        Player player = ref.getStore().getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("An error occurred").color(Color.RED));
            return;
        }

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

        CitizenInteractEvent interactEvent = new CitizenInteractEvent(citizen, playerRef);
        HyCitizensPlugin.get().getCitizensManager().fireCitizenInteractEvent(interactEvent);

        if (interactEvent.isCancelled())
            return;

        // Trigger ON_INTERACT animations
        HyCitizensPlugin.get().getCitizensManager().triggerAnimations(citizen, "ON_INTERACT");

        // Handle messages system
        MessagesConfig msgConfig = citizen.getMessagesConfig();
        if (msgConfig.isEnabled() && !msgConfig.getMessages().isEmpty()) {
            List<CitizenMessage> messages = msgConfig.getMessages();
            String mode = msgConfig.getSelectionMode();

            switch (mode) {
                case "SEQUENTIAL" -> {
                    UUID playerUUID = playerRef.getUuid();
                    int index = citizen.getSequentialMessageIndex().getOrDefault(playerUUID, 0);
                    if (index >= messages.size()) index = 0;
                    String msgText = replacePlaceholders(messages.get(index).getMessage(), playerRef, citizen);
                    Message parsed = parseColoredMessage(msgText);
                    if (parsed != null) playerRef.sendMessage(parsed);
                    citizen.getSequentialMessageIndex().put(playerUUID, index + 1);
                }
                case "ALL" -> {
                    for (CitizenMessage cm : messages) {
                        String msgText = replacePlaceholders(cm.getMessage(), playerRef, citizen);
                        Message parsed = parseColoredMessage(msgText);
                        if (parsed != null) playerRef.sendMessage(parsed);
                    }
                }
                default -> { // RANDOM
                    int index = RANDOM.nextInt(messages.size());
                    String msgText = replacePlaceholders(messages.get(index).getMessage(), playerRef, citizen);
                    Message parsed = parseColoredMessage(msgText);
                    if (parsed != null) playerRef.sendMessage(parsed);
                }
            }
        }

        // Run commands
        // Using CompletableFuture to ensure the commands run in the correct order
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (CommandAction commandAction : citizen.getCommandActions()) {
            chain = chain.thenCompose(v -> {
                String command = commandAction.getCommand();

                // Replace {PlayerName} placeholders
                command = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE)
                        .matcher(command)
                        .replaceAll(playerRef.getUsername());

                // Replace {CitizenName} placeholders
                command = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE)
                        .matcher(command)
                        .replaceAll(citizen.getName());

                // Check if this is a "send message" command
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

    private static String replacePlaceholders(@Nonnull String text, @Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen) {
        text = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(playerRef.getUsername());
        text = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(citizen.getName());
        return text;
    }
}
