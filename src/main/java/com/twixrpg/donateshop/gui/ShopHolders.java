package com.twixrpg.donateshop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Холдеры GUI: по ним слушатель кликов понимает, какое меню открыто и что выбрано. */
public final class ShopHolders {

    private ShopHolders() {
    }

    abstract static class Base implements InventoryHolder {
        private Inventory inventory;

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static final class MainHolder extends Base {
    }

    public static final class RanksHolder extends Base {
    }

    public static final class ItemsHolder extends Base {
    }

    public static final class DurationHolder extends Base {
        public final String rankId;

        public DurationHolder(String rankId) {
            this.rankId = rankId;
        }
    }

    public static final class ConfirmHolder extends Base {
        public enum Kind {RANK, ITEM}

        public final Kind kind;
        public final String rankId;
        public final String durationId;
        public final String productId;

        private ConfirmHolder(Kind kind, String rankId, String durationId, String productId) {
            this.kind = kind;
            this.rankId = rankId;
            this.durationId = durationId;
            this.productId = productId;
        }

        public static ConfirmHolder rank(String rankId, String durationId) {
            return new ConfirmHolder(Kind.RANK, rankId, durationId, null);
        }

        public static ConfirmHolder item(String productId) {
            return new ConfirmHolder(Kind.ITEM, null, null, productId);
        }
    }
}
