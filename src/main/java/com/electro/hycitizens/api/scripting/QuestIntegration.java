package com.electro.hycitizens.api.scripting;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public final class QuestIntegration {
    private static final String BRIDGE_CLASS = "com.electro.hyquests.api.HyQuestsScriptingBridge";
    private static final int SUPPORTED_API_VERSION = 1;

    private QuestIntegration() {
    }

    public static boolean startQuest(@Nonnull PlayerRef player, @Nonnull String questId) {
        return (boolean) invoke("startQuest", new Class<?>[]{PlayerRef.class, String.class}, player, questId);
    }

    public static void progressObjective(
            @Nonnull PlayerRef player,
            @Nonnull String type,
            @Nonnull String target,
            int amount
    ) {
        invoke(
                "progressObjective",
                new Class<?>[]{PlayerRef.class, String.class, String.class, int.class},
                player,
                type,
                target,
                amount
        );
    }

    public static boolean hasActiveQuest(@Nonnull UUID playerId, @Nonnull String questId) {
        return (boolean) invoke("hasActiveQuest", new Class<?>[]{UUID.class, String.class}, playerId, questId);
    }

    public static boolean hasCompletedQuest(@Nonnull UUID playerId, @Nonnull String questId) {
        return (boolean) invoke("hasCompletedQuest", new Class<?>[]{UUID.class, String.class}, playerId, questId);
    }

    public static boolean isRewardClaimed(@Nonnull UUID playerId, @Nonnull String questId) {
        return (boolean) invoke("isRewardClaimed", new Class<?>[]{UUID.class, String.class}, playerId, questId);
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Class<?> bridge = loadBridge();
            Method method = bridge.getMethod(methodName, parameterTypes);
            return method.invoke(null, arguments);
        } catch (ClassNotFoundException e) {
            throw new QuestIntegrationException("HyQuests is not installed; quest scripting action cannot run", e);
        } catch (NoSuchMethodException e) {
            throw new QuestIntegrationException("Installed HyQuests does not support scripting bridge API v1", e);
        } catch (IllegalAccessException e) {
            throw new QuestIntegrationException("HyQuests scripting bridge is not accessible", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new QuestIntegrationException("HyQuests rejected the scripting operation: " + cause.getMessage(), cause);
        } catch (LinkageError e) {
            throw new QuestIntegrationException("HyQuests scripting bridge could not be linked", e);
        }
    }

    private static Class<?> loadBridge() throws ClassNotFoundException {
        ClassLoader bridgeLoader = PluginManager.get().getBridgeClassLoader();
        Class<?> bridge = Class.forName(BRIDGE_CLASS, true, bridgeLoader);
        try {
            int apiVersion = bridge.getField("API_VERSION").getInt(null);
            if (apiVersion != SUPPORTED_API_VERSION) {
                throw new QuestIntegrationException(
                        "Unsupported HyQuests scripting bridge API version " + apiVersion
                                + " (expected " + SUPPORTED_API_VERSION + ")"
                );
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new QuestIntegrationException("Installed HyQuests does not declare scripting bridge API v1", e);
        }
        return bridge;
    }

    public static final class QuestIntegrationException extends RuntimeException {
        public QuestIntegrationException(String message) {
            super(message);
        }

        public QuestIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
