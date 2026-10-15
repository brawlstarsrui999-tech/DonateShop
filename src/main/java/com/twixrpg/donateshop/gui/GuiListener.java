package com.twixrpg.donateshop.gui;

import com.twixrpg.donateshop.DonateShopPlugin;
import com.twixrpg.donateshop.Text;
import com.twixrpg.donateshop.model.DonateRank;
import com.twixrpg.donateshop.model.DurationOption;
import com.twixrpg.donateshop.model.Quote;
import com.twixrpg.donateshop.model.ShopProduct;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Клики по меню магазина: навигация, подтверждение покупки, защита от вытаскивания предметов. */
public final class GuiListener implements Listener {

    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] DURATION_SLOTS = {11, 13, 15, 20, 22, 24};
    private static final int BACK_SLOT = 18;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final DonateShopPlugin plugin;

    public GuiListener(DonateShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ShopHolders.Base)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return; // клики по своему инвентарю просто запрещены выше (cancel), сюда не ведём логику
        }
        int slot = event.getSlot();

        if (holder instanceof ShopHolders.MainHolder) {
            if (slot == 11) plugin.menus().openRanks(player);
            else if (slot == 15) plugin.menus().openItems(player);
        } else if (holder instanceof ShopHolders.RanksHolder) {
            if (slot == BACK_SLOT) {
                plugin.menus().openMain(player);
                return;
            }
            int index = indexOf(CONTENT_SLOTS, slot);
            if (index >= 0 && index < plugin.config().ranks().size()) {
                plugin.menus().openDurations(player, plugin.config().ranks().get(index));
            }
        } else if (holder instanceof ShopHolders.DurationHolder durationHolder) {
            if (slot == BACK_SLOT) {
                plugin.menus().openRanks(player);
                return;
            }
            DonateRank rank = plugin.config().rank(durationHolder.rankId);
            if (rank == null) {
                player.closeInventory();
                return;
            }
            int index = indexOf(DURATION_SLOTS, slot);
            if (index < 0 || index >= plugin.config().durations().size()) return;
            DurationOption duration = plugin.config().durations().get(index);
            Quote quote = plugin.ranks().quote(player, rank, duration);
            if (!quote.allowed()) {
                player.sendMessage(Text.mm(plugin.config().msg("purchase-denied",
                        "reason", String.valueOf(quote.denyReason()))));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            plugin.menus().openConfirmRank(player, rank, duration);
        } else if (holder instanceof ShopHolders.ItemsHolder) {
            if (slot == BACK_SLOT) {
                plugin.menus().openMain(player);
                return;
            }
            int index = indexOf(CONTENT_SLOTS, slot);
            if (index >= 0 && index < plugin.config().products().size()) {
                plugin.menus().openConfirmItem(player, plugin.config().products().get(index));
            }
        } else if (holder instanceof ShopHolders.ConfirmHolder confirm) {
            handleConfirm(player, confirm, slot);
        }
    }

    private void handleConfirm(Player player, ShopHolders.ConfirmHolder confirm, int slot) {
        if (slot == CANCEL_SLOT) {
            // назад — туда, откуда пришли
            if (confirm.kind == ShopHolders.ConfirmHolder.Kind.RANK) {
                DonateRank rank = plugin.config().rank(confirm.rankId);
                if (rank != null) {
                    plugin.menus().openDurations(player, rank);
                    return;
                }
            }
            plugin.menus().openItems(player);
            return;
        }
        if (slot != CONFIRM_SLOT) return;

        boolean success;
        if (confirm.kind == ShopHolders.ConfirmHolder.Kind.RANK) {
            DonateRank rank = plugin.config().rank(confirm.rankId);
            DurationOption duration = plugin.config().duration(confirm.durationId);
            if (rank == null || duration == null) {
                player.closeInventory();
                return;
            }
            success = plugin.purchases().buyRank(player, rank, duration);
            if (success) {
                player.closeInventory();
            } else {
                // обновить меню подтверждения (баланс/доступность могли измениться)
                plugin.menus().openConfirmRank(player, rank, duration);
            }
        } else {
            ShopProduct product = plugin.config().product(confirm.productId);
            if (product == null) {
                player.closeInventory();
                return;
            }
            success = plugin.purchases().buyItem(player, product);
            if (success) {
                player.closeInventory();
            } else {
                plugin.menus().openConfirmItem(player, product);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolders.Base) {
            event.setCancelled(true);
        }
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) return i;
        }
        return -1;
    }
}
