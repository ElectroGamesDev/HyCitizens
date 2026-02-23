package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class DeathDropItem {
    private String itemId;
    private int quantity;

    public DeathDropItem() {
        this.itemId = "";
        this.quantity = 1;
    }

    public DeathDropItem(@Nonnull String itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }

    @Nonnull
    public String getItemId() { return itemId; }
    public void setItemId(@Nonnull String itemId) { this.itemId = itemId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
