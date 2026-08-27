package com.electro.hycitizens.api.scripting;

import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.managers.DialogueManager;
import com.electro.hycitizens.api.dialogue.DialogueSession;
import com.electro.hycitizens.api.dialogue.PlayerDialogState;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.electro.hycitizens.HyCitizensPlugin;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import java.util.concurrent.ThreadLocalRandom;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ScriptExpressionEvaluator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(?:%|\\{\\{|\\$\\{)\\s*([a-zA-Z0-9_]+)[\\.:]([a-zA-Z0-9_\\.]+)(?:[\\.:]([a-zA-Z0-9_\\.]+))?\\s*(?:%|\\}\\}|\\})");
    private static final Pattern EVAL_PATTERN = Pattern.compile("\\{EVAL:\\s*([^\\}]+)\\}");
    private static final Pattern IF_PATTERN = Pattern.compile("\\{IF:\\s*([^\\s]+)\\s*([^\\s]+)\\s*([^\\s]+)\\s*THEN:\\s*(.*?)\\s*ELSE:\\s*(.*?)\\}");

    public static Object evaluateParameter(Object param, ScriptContext context) {
        if (param instanceof String) {
            return resolve((String) param, context);
        } else if (param instanceof List) {
            List<Object> resolvedList = new ArrayList<>();
            for (Object obj : (List<?>) param) {
                resolvedList.add(evaluateParameter(obj, context));
            }
            return resolvedList;
        } else if (param instanceof Map) {
            Map<Object, Object> resolvedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) param).entrySet()) {
                resolvedMap.put(entry.getKey(), evaluateParameter(entry.getValue(), context));
            }
            return resolvedMap;
        }
        return param;
    }

    public static String resolve(String text, ScriptContext context) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Replace Scope Variables & Built-in Placeholders
        String result = replacePlaceholders(text, context);

        // Process Ternary IF blocks
        result = processTernaries(result);

        // Process Arithmetic EVAL blocks
        result = processEvals(result, context);

        return result;
    }

    private static String replacePlaceholders(String text, ScriptContext context) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String scope = matcher.group(1).toLowerCase();
            String varName = matcher.group(2);
            String extraArg = matcher.group(3);

            String resolvedValue = "";

            if ("player".equals(scope)) {
                if ("has_quest".equalsIgnoreCase(varName) && extraArg != null) {
                    resolvedValue = String.valueOf(evaluateHasQuestPlaceholder(extraArg, context));
                } else if ("completed_quest".equalsIgnoreCase(varName) && extraArg != null) {
                    resolvedValue = String.valueOf(evaluateCompletedQuestPlaceholder(extraArg, context));
                } else {
                    resolvedValue = resolvePlayerField(varName, context);
                }
            } else if ("citizen".equals(scope)) {
                resolvedValue = resolveCitizenField(varName, context);
            } else if ("global".equals(scope)) {
                Object val = VariableManager.get().getGlobalVar(varName);
                resolvedValue = val != null ? val.toString() : "0";
            } else if ("session".equals(scope)) {
                Object val = context.getSessionVar(varName);
                if (val == null) {
                    val = evaluateConditionAsVariable(varName, context);
                }
                resolvedValue = val != null ? val.toString() : "0";
            } else if ("loop".equals(scope)) {
                // Inside loops, variables like loop:item, loop:index are session variables
                Object val = context.getSessionVar("loop:" + varName);
                resolvedValue = val != null ? val.toString() : "0";
            } else if ("signal".equals(scope)) {
                if ("name".equalsIgnoreCase(varName)) {
                    Object val = context.getTriggerArg("signal_name");
                    resolvedValue = val != null ? val.toString() : "";
                } else if ("arg".equalsIgnoreCase(varName) && extraArg != null) {
                    Map<?, ?> args = (Map<?, ?>) context.getTriggerArg("args");
                    Object val = args != null ? args.get(extraArg) : null;
                    resolvedValue = val != null ? val.toString() : "";
                }
            } else if ("damage".equals(scope)) {
                if ("amount".equalsIgnoreCase(varName)) {
                    Object val = context.getTriggerArg("damage_amount");
                    resolvedValue = val != null ? val.toString() : "0";
                }
            } else if ("attacker".equals(scope)) {
                PlayerRef attacker = (PlayerRef) context.getTriggerArg("attacker_player");
                if (attacker != null) {
                    if ("name".equalsIgnoreCase(varName)) {
                        resolvedValue = attacker.getUsername();
                    } else if ("uuid".equalsIgnoreCase(varName)) {
                        resolvedValue = attacker.getUuid().toString();
                    }
                }
            } else if ("distance".equals(scope)) {
                resolvedValue = String.valueOf(calculateDistance(context));
            } else if ("dialog".equals(scope) || "dialogue".equals(scope)) {
                resolvedValue = resolveDialogueField(varName, extraArg, context);
            } else {
                resolvedValue = matcher.group(0); // keep original
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolvedValue));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveDialogueField(String field, String extraArg, ScriptContext context) {
        PlayerRef player = context.getPlayer();
        DialogueSession session = (player != null) ? DialogueManager.get().getDialogueSession(player) : null;
        String dialogId = (extraArg != null && !extraArg.isEmpty()) ? extraArg : (session != null ? session.getDialogue().getId() : "");

        if ("id".equalsIgnoreCase(field)) {
            return session != null ? session.getDialogue().getId() : (extraArg != null ? extraArg : "");
        } else if ("title".equalsIgnoreCase(field)) {
            if (session != null && (extraArg == null || extraArg.isEmpty())) {
                return session.getDialogue().getTitle();
            }
            if (dialogId != null && !dialogId.isEmpty()) {
                var d = DialogueManager.get().getDialogue(dialogId);
                return d != null ? d.getTitle() : "";
            }
            return "";
        } else if ("node_id".equalsIgnoreCase(field) || "node".equalsIgnoreCase(field)) {
            return session != null ? session.getCurrentNodeId() : "";
        } else if ("speaker".equalsIgnoreCase(field)) {
            if (session != null && session.getCurrentNode() != null) {
                return session.getCurrentNode().getSpeaker();
            }
            return context.getCitizen() != null ? context.getCitizen().getName() : "NPC";
        } else if ("visits".equalsIgnoreCase(field) || "visit_count".equalsIgnoreCase(field)) {
            if (player == null || dialogId == null || dialogId.isEmpty()) return "0";
            PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(player.getUuid());
            return String.valueOf(state != null ? state.getDialogVisits().getOrDefault(dialogId, 0) : 0);
        } else if ("seen".equalsIgnoreCase(field)) {
            if (player == null || dialogId == null || dialogId.isEmpty()) return "false";
            PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(player.getUuid());
            return String.valueOf(state != null && state.getSeenDialogs().contains(dialogId));
        } else if ("completed".equalsIgnoreCase(field)) {
            if (player == null || dialogId == null || dialogId.isEmpty()) return "false";
            PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(player.getUuid());
            return String.valueOf(state != null && state.getCompletedDialogs().contains(dialogId));
        } else if ("state".equalsIgnoreCase(field) || "custom".equalsIgnoreCase(field)) {
            if (player == null || extraArg == null || extraArg.isEmpty()) return "0";
            PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(player.getUuid());
            if (state == null) return "0";
            Object val = state.getCustomState().get(extraArg);
            return val != null ? val.toString() : "0";
        }
        return "0";
    }

    private static String resolvePlayerField(String field, ScriptContext context) {
        PlayerRef player = context.getPlayer();
        if (player == null) {
            return "0";
        }

        if ("name".equalsIgnoreCase(field)) {
            return player.getUsername();
        } else if ("uuid".equalsIgnoreCase(field)) {
            return player.getUuid().toString();
        } else if ("x".equalsIgnoreCase(field)) {
            return String.format(Locale.ROOT, "%.2f", player.getTransform().getPosition().x);
        } else if ("y".equalsIgnoreCase(field)) {
            return String.format(Locale.ROOT, "%.2f", player.getTransform().getPosition().y);
        } else if ("z".equalsIgnoreCase(field)) {
            return String.format(Locale.ROOT, "%.2f", player.getTransform().getPosition().z);
        } else if ("health".equalsIgnoreCase(field)) {
            return String.valueOf(getEntityHealth(player.getReference()));
        } else if ("max_health".equalsIgnoreCase(field)) {
            return String.valueOf(getEntityMaxHealth(player.getReference()));
        }

        // Custom player variable
        Object val = VariableManager.get().getPlayerVar(player.getUuid(), field);
        if (val == null && field.contains(".")) {
            // Dot notation for JSON maps
            val = resolveDotNotation(VariableManager.get().getPlayerVariables(player.getUuid()), field);
        }
        return val != null ? val.toString() : "0";
    }

    private static String resolveCitizenField(String field, ScriptContext context) {
        CitizenData citizen = context.getCitizen();
        if (citizen == null) {
            return "0";
        }

        Vector3d pos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();

        if ("name".equalsIgnoreCase(field)) {
            return citizen.getName();
        } else if ("id".equalsIgnoreCase(field)) {
            return citizen.getId();
        } else if ("x".equalsIgnoreCase(field)) {
            return String.format(Locale.ROOT, "%.2f", pos.x);
        } else if ("y".equalsIgnoreCase(field)) {
            return String.format(Locale.ROOT, "%.2f", pos.y);
        } else if ("z".equalsIgnoreCase(field)) {
            return String.format(Locale.ROOT, "%.2f", pos.z);
        } else if ("health".equalsIgnoreCase(field)) {
            return String.valueOf(getEntityHealth(citizen.getNpcRef()));
        } else if ("max_health".equalsIgnoreCase(field)) {
            return String.valueOf(getEntityMaxHealth(citizen.getNpcRef()));
        } else if ("model_id".equalsIgnoreCase(field)) {
            return citizen.getModelId();
        }

        // Custom citizen variable
        Object val = VariableManager.get().getCitizenVar(citizen, field);
        if (val == null && field.contains(".")) {
            val = resolveDotNotation(VariableManager.get().getCitizenVariables(citizen), field);
        }
        return val != null ? val.toString() : "0";
    }

    private static Object resolveDotNotation(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private static double calculateDistance(ScriptContext context) {
        PlayerRef player = context.getPlayer();
        CitizenData citizen = context.getCitizen();
        if (player == null || citizen == null) {
            return 0.0;
        }
        Vector3d cPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        Vector3d pPos = player.getTransform().getPosition();
        return cPos.distance(pPos);
    }

    public static float getEntityHealth(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return 0f;
        try {
            EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
            if (statMap != null) {
                EntityStatValue val = statMap.get(DefaultEntityStatTypes.getHealth());
                if (val != null) return val.get();
            }
        } catch (Exception ignored) {}
        return 0f;
    }

    public static float getEntityMaxHealth(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return 100f;
        try {
            EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
            if (statMap != null) {
                EntityStatValue val = statMap.get(DefaultEntityStatTypes.getHealth());
                if (val != null) return val.getMax();
            }
        } catch (Exception ignored) {}
        return 100f;
    }

    private static String processTernaries(String text) {
        Matcher matcher = IF_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String leftVal = matcher.group(1);
            String operator = matcher.group(2);
            String rightVal = matcher.group(3);
            String thenBranch = matcher.group(4);
            String elseBranch = matcher.group(5);

            boolean evaluation = evaluateComparison(leftVal, operator, rightVal);
            String replacement = evaluation ? thenBranch : elseBranch;

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean evaluateComparison(String left, String op, String right) {
        // Try parsing as double
        Double dLeft = null;
        Double dRight = null;
        try {
            dLeft = Double.parseDouble(left);
            dRight = Double.parseDouble(right);
        } catch (NumberFormatException ignored) {}

        if (dLeft != null && dRight != null) {
            return switch (op) {
                case "==", "EQUALS" -> dLeft.equals(dRight);
                case "!=", "NOT_EQUALS" -> !dLeft.equals(dRight);
                case ">", "GREATER_THAN" -> dLeft > dRight;
                case ">=", "GREATER_THAN_OR_EQUAL" -> dLeft >= dRight;
                case "<", "LESS_THAN" -> dLeft < dRight;
                case "<=", "LESS_THAN_OR_EQUAL" -> dLeft <= dRight;
                default -> false;
            };
        }

        // Fallback to string comparison
        return switch (op) {
            case "==", "EQUALS" -> left.equalsIgnoreCase(right);
            case "!=", "NOT_EQUALS" -> !left.equalsIgnoreCase(right);
            case "CONTAINS" -> left.toLowerCase().contains(right.toLowerCase());
            case "STARTS_WITH" -> left.toLowerCase().startsWith(right.toLowerCase());
            case "ENDS_WITH" -> left.toLowerCase().endsWith(right.toLowerCase());
            default -> false;
        };
    }

    private static String processEvals(String text, ScriptContext context) {
        Matcher matcher = EVAL_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1);
            double result = evaluateMathExpression(expression);
            if (result == 0.0 && !expression.trim().matches("[\\d\\s+\\-*/%().]+")) {
                String citizenId = (context != null && context.getCitizen() != null) ? context.getCitizen().getId() : "unknown";
                getLogger().atWarning().log("[HyCitizens] Script EVAL expression returned 0 (possible bad variable or syntax) - citizen: " + citizenId + ", expression: {EVAL: " + expression + "}");
            }
            String formatted = String.format(Locale.ROOT, "%.2f", result);
            if (formatted.endsWith(".00")) {
                formatted = formatted.substring(0, formatted.length() - 3);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(formatted));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static double evaluateMathExpression(String expr) {
        try {
            return new Object() {
                int pos = -1, ch;

                void nextChar() {
                    ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
                }

                boolean eat(int charToEat) {
                    while (ch == ' ') nextChar();
                    if (ch == charToEat) {
                        nextChar();
                        return true;
                    }
                    return false;
                }

                double parse() {
                    nextChar();
                    double x = parseExpression();
                    if (pos < expr.length()) throw new RuntimeException("Unexpected character: " + (char)ch);
                    return x;
                }

                double parseExpression() {
                    double x = parseTerm();
                    for (;;) {
                        if      (eat('+')) x += parseTerm(); // addition
                        else if (eat('-')) x -= parseTerm(); // subtraction
                        else return x;
                    }
                }

                double parseTerm() {
                    double x = parseFactor();
                    for (;;) {
                        if      (eat('*')) x *= parseFactor(); // multiplication
                        else if (eat('/')) x /= parseFactor(); // division
                        else if (eat('%')) x %= parseFactor(); // modulo
                        else return x;
                    }
                }

                double parseFactor() {
                    if (eat('+')) return parseFactor(); // unary plus
                    if (eat('-')) return -parseFactor(); // unary minus

                    double x;
                    int startPos = this.pos;
                    if (eat('(')) { // parentheses
                        x = parseExpression();
                        eat(')');
                    } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(expr.substring(startPos, this.pos));
                    } else {
                        // Unparseable token - skip it and return 0 (caller logs the full expression)
                        while (ch != -1 && ch != ' ' && ch != '+' && ch != '-' && ch != '*' && ch != '/' && ch != '%' && ch != ')') {
                            nextChar();
                        }
                        x = 0.0;
                    }

                    return x;
                }
            }.parse();
        } catch (Exception e) {
            getLogger().atWarning().log("Failed to evaluate math expression: " + expr + ". Error: " + e.getMessage());
            return 0.0;
        }
    }

    private static Object evaluateConditionAsVariable(String name, ScriptContext context) {
        String varNameUpper = name.toUpperCase();
        
        switch (varNameUpper) {
            case "TIME_OF_DAY": {
                WorldTimeResource timeResource = context.getStore().getResource(WorldTimeResource.getResourceType());
                if (timeResource != null) {
                    return timeResource.getDayProgress() * 24.0;
                }
                return 0.0;
            }
            case "WEATHER": {
                return getCurrentWeather(context);
            }
            case "INVENTORY_SPACE_FREE": {
                return getFreeInventorySlots(context);
            }
            case "CHANCE": {
                return ThreadLocalRandom.current().nextDouble();
            }
            case "IS_IN_COMBAT": {
                if (context.getCitizen() != null) {
                    return HyCitizensPlugin.get().getCitizensManager().isCitizenInCombat(context.getCitizen());
                }
                return false;
            }
            default: {
                if (ScriptManager.get().hasCondition(varNameUpper)) {
                    return ScriptManager.get().evaluateConditionDirect(varNameUpper, context, Collections.emptyMap());
                }
                return null;
            }
        }
    }

    private static String getCurrentWeather(ScriptContext context) {
        try {
            WeatherResource weatherResource = context.getStore().getResource(WeatherResource.getResourceType());
            if (weatherResource != null) {
                int forcedIndex = weatherResource.getForcedWeatherIndex();
                if (forcedIndex != Weather.UNKNOWN_ID) {
                    Weather weather = Weather.getAssetMap().getAsset(forcedIndex);
                    if (weather != null && weather.getId() != null) return weather.getId();
                }
            }
        } catch (Exception ignored) {}
        return "CLEAR";
    }

    private static int getFreeInventorySlots(ScriptContext context) {
        PlayerRef playerRef = context.getPlayer();
        if (playerRef == null) return 0;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return 0;
        CombinedItemContainer container = InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.HOTBAR_FIRST);
        if (container == null) return 0;
        int free = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack item = container.getItemStack(i);
            if (item == null || item.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private static boolean evaluateHasQuestPlaceholder(String questId, ScriptContext context) {
        if (context.getPlayer() == null) return false;
        try {
            return QuestIntegration.hasActiveQuest(context.getPlayer().getUuid(), questId);
        } catch (QuestIntegration.QuestIntegrationException e) {
            getLogger().atWarning().log("[HyCitizens] ${player:has_quest:" + questId + "} failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean evaluateCompletedQuestPlaceholder(String questId, ScriptContext context) {
        if (context.getPlayer() == null) return false;
        try {
            return QuestIntegration.hasCompletedQuest(context.getPlayer().getUuid(), questId);
        } catch (QuestIntegration.QuestIntegrationException e) {
            getLogger().atWarning().log("[HyCitizens] ${player:completed_quest:" + questId + "} failed: " + e.getMessage());
            return false;
        }
    }
}
