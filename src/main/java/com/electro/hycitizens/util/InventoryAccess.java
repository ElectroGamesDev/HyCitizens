package com.electro.hycitizens.util;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class InventoryAccess {
    private InventoryAccess() {
    }

    @Nullable
    public static ItemContainer hotbar(@Nullable Ref<EntityStore> ref) {
        InventoryComponent.Hotbar hotbar = component(ref, InventoryComponent.Hotbar.getComponentType());
        return hotbar != null ? hotbar.getInventory() : null;
    }

    @Nullable
    public static ItemContainer storage(@Nullable Ref<EntityStore> ref) {
        InventoryComponent.Storage storage = component(ref, InventoryComponent.Storage.getComponentType());
        return storage != null ? storage.getInventory() : null;
    }

    @Nullable
    public static ItemContainer armor(@Nullable Ref<EntityStore> ref) {
        InventoryComponent.Armor armor = component(ref, InventoryComponent.Armor.getComponentType());
        return armor != null ? armor.getInventory() : null;
    }

    @Nullable
    public static ItemStack itemInHand(@Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return InventoryComponent.getItemInHand(ref.getStore(), ref);
    }

    @Nullable
    public static ItemStack utilityItem(@Nullable Ref<EntityStore> ref) {
        InventoryComponent.Utility utility = component(ref, InventoryComponent.Utility.getComponentType());
        return utility != null ? utility.getActiveItem() : null;
    }

    public static boolean addToHotbarOrStorage(@Nullable Ref<EntityStore> ref, @Nonnull ItemStack stack) {
        ItemContainer hotbar = hotbar(ref);
        if (hotbar != null && hotbar.canAddItemStack(stack)) {
            hotbar.addItemStack(stack);
            return true;
        }

        ItemContainer storage = storage(ref);
        if (storage != null && storage.canAddItemStack(stack)) {
            storage.addItemStack(stack);
            return true;
        }

        return false;
    }

    @Nullable
    private static <T extends InventoryComponent> T component(@Nullable Ref<EntityStore> ref,
                                                             @Nonnull ComponentType<EntityStore, T> componentType) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref.getStore().getComponent(ref, componentType);
    }
}
