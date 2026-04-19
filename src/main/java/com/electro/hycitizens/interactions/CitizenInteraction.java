package com.electro.hycitizens.interactions;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CitizenMessage;
import com.electro.hycitizens.models.CommandAction;
import com.electro.hycitizens.models.MessagesConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
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
import java.util.Locale;
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
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITIZEN_NAME_PATTERN = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_X_PATTERN = Pattern.compile("\\{NpcX}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_Y_PATTERN = Pattern.compile("\\{NpcY}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_Z_PATTERN = Pattern.compile("\\{NpcZ}", Pattern.CASE_INSENSITIVE);
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
                Message part = parseRichStyledText(textPart, currentColor);
                msg = appendMessage(msg, part);
            }
        }

        return msg;
    }

    @Nullable
    private static Message parseRichStyledText(@Nonnull String text, @Nullable Color color) {
        Message result = null;
        Matcher linkMatcher = LINK_PATTERN.matcher(text);
        int cursor = 0;

        while (linkMatcher.find()) {
            if (linkMatcher.start() > cursor) {
                Message plain = parseInlineStyles(text.substring(cursor, linkMatcher.start()), color, null);
                result = appendMessage(result, plain);
            }

            String label = linkMatcher.group(1);
            String url = linkMatcher.group(2);
            Message linked = parseInlineStyles(label, color, url);
            result = appendMessage(result, linked);
            cursor = linkMatcher.end();
        }

        if (cursor < text.length()) {
            Message plain = parseInlineStyles(text.substring(cursor), color, null);
            result = appendMessage(result, plain);
        }

        return result;
    }

    @Nullable
    private static Message parseInlineStyles(@Nonnull String text, @Nullable Color color, @Nullable String linkUrl) {
        Message result = null;
        int i = 0;

        while (i < text.length()) {
            boolean bold = false;
            boolean italic = false;
            String token;

            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end != -1) {
                    token = text.substring(i + 2, end);
                    bold = true;
                    i = end + 2;
                } else {
                    token = text.substring(i);
                    i = text.length();
                }
            } else if (text.startsWith("*", i)) {
                int end = text.indexOf("*", i + 1);
                if (end != -1) {
                    token = text.substring(i + 1, end);
                    italic = true;
                    i = end + 1;
                } else {
                    token = text.substring(i);
                    i = text.length();
                }
            } else {
                int nextBold = text.indexOf("**", i);
                int nextItalic = text.indexOf("*", i);
                int next = -1;
                if (nextBold != -1 && nextItalic != -1) {
                    next = Math.min(nextBold, nextItalic);
                } else if (nextBold != -1) {
                    next = nextBold;
                } else if (nextItalic != -1) {
                    next = nextItalic;
                }

                if (next == -1) {
                    token = text.substring(i);
                    i = text.length();
                } else {
                    token = text.substring(i, next);
                    i = next;
                }
            }

            if (token.isEmpty()) {
                continue;
            }

            Message part = Message.raw(token);
            if (color != null) {
                part = part.color(color);
            }
            if (bold) {
                part = part.bold(true);
            }
            if (italic) {
                part = part.italic(true);
            }
            if (linkUrl != null && !linkUrl.isEmpty()) {
                part = part.link(linkUrl);
            }

            result = appendMessage(result, part);
        }

        return result;
    }

    @Nullable
    private static Message appendMessage(@Nullable Message base, @Nullable Message part) {
        if (part == null) {
            return base;
        }
        if (base == null) {
            return part;
        }
        return base.insert(part);
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
        UUID playerUUID = playerRef.getUuid();
        boolean runNormalBehavior = true;

        if (citizen.isFirstInteractionEnabled()) {
            boolean isFirstInteraction = citizen.getPlayersWhoCompletedFirstInteraction().add(playerUUID);
            if (isFirstInteraction) {
                runNormalBehavior = citizen.isRunNormalOnFirstInteraction();
                HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                runMessageFlow(
                        playerRef,
                        citizen,
                        interactionSource,
                        citizen.getFirstInteractionMessagesConfig(),
                        citizen.getSequentialFirstInteractionMessageIndex()
                );
                runCommandFlow(
                        playerRef,
                        citizen,
                        player,
                        interactionSource,
                        citizen.getFirstInteractionCommandActions(),
                        citizen.getFirstInteractionCommandSelectionMode(),
                        citizen.getSequentialFirstInteractionCommandIndex()
                );
            }
        }

        if (runNormalBehavior) {
            runMessageFlow(
                    playerRef,
                    citizen,
                    interactionSource,
                    citizen.getMessagesConfig(),
                    citizen.getSequentialMessageIndex()
            );
            runCommandFlow(
                    playerRef,
                    citizen,
                    player,
                    interactionSource,
                    citizen.getCommandActions(),
                    citizen.getCommandSelectionMode(),
                    citizen.getSequentialCommandIndex()
            );
        }
    }

    private static void runMessageFlow(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen,
                                       @Nonnull String interactionSource, @Nonnull MessagesConfig msgConfig,
                                       @Nonnull Map<UUID, Integer> sequentialIndexMap) {
        if (!msgConfig.isEnabled()) {
            return;
        }

        List<CitizenMessage> matchingMessages = msgConfig.getMessages().stream()
                .filter(m -> m.isTriggeredBy(interactionSource))
                .filter(CitizenInteraction::passesChance)
                .collect(Collectors.toList());

        if (matchingMessages.isEmpty()) {
            return;
        }

        List<CitizenMessage> selected = selectMessagesByMode(
                matchingMessages, msgConfig.getSelectionMode(), sequentialIndexMap, playerRef.getUuid());
        sendMessages(playerRef, citizen, selected);
    }

    private static void runCommandFlow(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen,
                                       @Nonnull Player player, @Nonnull String interactionSource,
                                       @Nonnull List<CommandAction> commands, @Nonnull String commandSelectionMode,
                                       @Nonnull Map<UUID, Integer> sequentialIndexMap) {
        List<CommandAction> matchingCommands = commands.stream()
                .filter(cmd -> cmd.isTriggeredBy(interactionSource))
                .filter(CitizenInteraction::passesChance)
                .collect(Collectors.toList());

        if (matchingCommands.isEmpty()) {
            return;
        }

        List<CommandAction> selected = selectCommandsByMode(
                matchingCommands, commandSelectionMode, sequentialIndexMap, playerRef.getUuid());
        runCommands(playerRef, citizen, player, selected);
    }

    @Nonnull
    private static List<CitizenMessage> selectMessagesByMode(@Nonnull List<CitizenMessage> messages,
                                                             @Nonnull String selectionMode,
                                                             @Nonnull Map<UUID, Integer> sequentialIndexMap,
                                                             @Nonnull UUID playerUuid) {
        if (messages.isEmpty()) {
            return List.of();
        }
        return switch (selectionMode.toUpperCase(Locale.ROOT)) {
            case "ALL" -> new ArrayList<>(messages);
            case "SEQUENTIAL" -> {
                int startIndex = sequentialIndexMap.getOrDefault(playerUuid, 0);
                int subsetIndex = startIndex % messages.size();
                sequentialIndexMap.put(playerUuid, subsetIndex + 1);
                yield List.of(messages.get(subsetIndex));
            }
            default -> List.of(messages.get(RANDOM.nextInt(messages.size())));
        };
    }

    @Nonnull
    private static List<CommandAction> selectCommandsByMode(@Nonnull List<CommandAction> commands,
                                                            @Nonnull String selectionMode,
                                                            @Nonnull Map<UUID, Integer> sequentialIndexMap,
                                                            @Nonnull UUID playerUuid) {
        if (commands.isEmpty()) {
            return List.of();
        }
        return switch (selectionMode.toUpperCase(Locale.ROOT)) {
            case "ALL" -> new ArrayList<>(commands);
            case "SEQUENTIAL" -> {
                int startIndex = sequentialIndexMap.getOrDefault(playerUuid, 0);
                int subsetIndex = startIndex % commands.size();
                sequentialIndexMap.put(playerUuid, subsetIndex + 1);
                yield List.of(commands.get(subsetIndex));
            }
            default -> List.of(commands.get(RANDOM.nextInt(commands.size())));
        };
    }

    private static boolean passesChance(@Nonnull CommandAction action) {
        return RANDOM.nextFloat() * 100.0f <= action.getChancePercent();
    }

    private static boolean passesChance(@Nonnull CitizenMessage message) {
        return RANDOM.nextFloat() * 100.0f <= message.getChancePercent();
    }

    private static void sendMessages(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen,
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
                if (parsed != null) {
                    playerRef.sendMessage(parsed);
                }
                return CompletableFuture.completedFuture(null);
            });
        }
    }

    private static void runCommands(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen,
                                    @Nonnull Player player, @Nonnull List<CommandAction> commands) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (CommandAction commandAction : commands) {
            chain = chain.thenCompose(v -> {
                if (commandAction.getDelaySeconds() > 0) {
                    CompletableFuture<Void> delayedFuture = new CompletableFuture<>();
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(
                            () -> delayedFuture.complete(null),
                            (long) (commandAction.getDelaySeconds() * 1000),
                            TimeUnit.MILLISECONDS);
                    return delayedFuture;
                }
                return CompletableFuture.completedFuture(null);
            }).thenCompose(v -> {
                String command = replacePlaceholders(commandAction.getCommand(), playerRef, citizen);

                if (command.startsWith("{SendMessage}")) {
                    String messageContent = command.substring("{SendMessage}".length()).trim();
                    Message msg = parseColoredMessage(messageContent);
                    if (msg != null) {
                        playerRef.sendMessage(msg);
                    }
                    return CompletableFuture.completedFuture(null);
                }

                if (commandAction.isRunAsServer()) {
                    return CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
                }
                return CommandManager.get().handleCommand(player, command);
            });
        }
    }

    private static String replacePlaceholders(@Nonnull String text, @Nonnull PlayerRef playerRef,
                                              @Nonnull CitizenData citizen) {
        Vector3d npcPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        String npcX = String.format(Locale.ROOT, "%.2f", npcPos.x);
        String npcY = String.format(Locale.ROOT, "%.2f", npcPos.y);
        String npcZ = String.format(Locale.ROOT, "%.2f", npcPos.z);

        text = PLAYER_NAME_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(playerRef.getUsername()));
        text = CITIZEN_NAME_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(formatCitizenNamePlaceholder(citizen)));
        text = NPC_X_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcX));
        text = NPC_Y_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcY));
        text = NPC_Z_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcZ));
        return text;
    }

    @Nonnull
    private static String formatCitizenNamePlaceholder(@Nonnull CitizenData citizen) {
        return citizen.getName()
                .replace("\\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ');
    }
}
